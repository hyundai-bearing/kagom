# 로로 앱 성능 측정: 콜드 스타트 N회 → 로딩 단계별 시간, 실행 중 fps·CPU, 메모리, 스크린샷
# 사용법: powershell -File tools/perf/measure.ps1 -Apk <apk> -Out <dir> [-Runs 3]
param(
    [Parameter(Mandatory = $true)][string]$Apk,
    [Parameter(Mandatory = $true)][string]$Out,
    [int]$Runs = 3,
    [string]$Pkg = "com.example.roro"
)
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
New-Item -ItemType Directory -Force $Out | Out-Null
& $adb install -r $Apk | Out-Null

$results = @()
for ($r = 1; $r -le $Runs; $r++) {
    & $adb shell am force-stop $Pkg
    & $adb logcat -c
    & $adb shell am start -n "$Pkg/.MainActivity" | Out-Null
    # 곰이 처음 그려질 때까지 최대 40초 대기
    $deadline = (Get-Date).AddSeconds(40)
    do { Start-Sleep -Milliseconds 500; $log = & $adb logcat -d -s RoroPerf } until (($log -match 'first frame with bear') -or (Get-Date) -gt $deadline)
    Start-Sleep 30   # 안정화 후 fps 측정 (마지막 2구간 = 10초)
    $log = & $adb logcat -d -s RoroPerf
    $procId = (& $adb shell pidof $Pkg).Trim()
    $cpu = (& $adb shell top -b -n 3 -d 2 -p $procId) | Select-String "^\s*$procId\s" | ForEach-Object { [double](($_ -split '\s+' | Where-Object { $_ })[8]) }
    $mem = (& $adb shell dumpsys meminfo $Pkg) | Select-String 'TOTAL PSS:\s+(\d+)' | ForEach-Object { [int]$_.Matches[0].Groups[1].Value }
    $row = [ordered]@{ run = $r }
    foreach ($k in 'compose start', 'still visible', 'engine created', 'first frame', 'model attached', 'first frame with bear') {
        $m = $log | Select-String "(\d+)ms $k$"
        $row[$k] = if ($m) { [int]$m[0].Matches[0].Groups[1].Value } else { $null }
    }
    $fps = $log | Select-String 'fps=([\d.]+)' | ForEach-Object { [double]$_.Matches[0].Groups[1].Value }
    $row['fps'] = if ($fps) { [math]::Round(($fps | Select-Object -Last 2 | Measure-Object -Average).Average, 1) } else { $null }
    $row['cpu%'] = if ($cpu) { [math]::Round(($cpu | Select-Object -Skip 1 | Measure-Object -Average).Average, 1) } else { $null }
    $row['pssMB'] = if ($mem) { [math]::Round($mem[0] / 1024, 1) } else { $null }
    $results += [pscustomobject]$row
    if ($r -eq $Runs) { & $adb shell screencap -p /sdcard/perf.png; & $adb pull /sdcard/perf.png "$Out\screen.png" | Out-Null }
}
$results | Format-Table -AutoSize | Out-String -Width 200
$results | ConvertTo-Json | Set-Content -Encoding utf8 "$Out\results.json"
"apkMB={0:N1}" -f ((Get-Item $Apk).Length / 1MB)
