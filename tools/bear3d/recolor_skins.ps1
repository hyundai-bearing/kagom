# pebble_bear.glb 의 민트 텍스처를 8종 스킨 색으로 바꿔 assets/skins/<key>.png 로 저장한다.
# 사용법: powershell -File tools/bear3d/recolor_skins.ps1 -Source <민트 base color PNG>
#   (원본 텍스처는 glb 안의 Image_0. Blender로 glb를 열어 저장하거나, 이 스크립트의 -Glb 옵션으로 직접 추출)
param(
    [string]$Glb = "app/src/main/assets/models/pebble_bear.glb",
    [string]$Out = "app/src/main/assets/skins"
)
$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing
Add-Type -ReferencedAssemblies System.Drawing -TypeDefinition @"
using System;
using System.Drawing;
using System.Drawing.Imaging;
using System.Runtime.InteropServices;
public static class BearRecolor {
    static float Smooth(float e0, float e1, float x) {
        float t = Math.Max(0f, Math.Min(1f, (x - e0) / (e1 - e0)));
        return t * t * (3 - 2 * t);
    }
    // body/belly: 목표 sRGB 색 (0~255). 원본 픽셀의 채도가 낮고 밝으면 배·주둥이(흰 부분)로 본다.
    public static Bitmap Recolor(Bitmap src, int[] body, int[] belly) {
        var dst = new Bitmap(src.Width, src.Height, PixelFormat.Format32bppArgb);
        var r = new Rectangle(0, 0, src.Width, src.Height);
        var sd = src.LockBits(r, ImageLockMode.ReadOnly, PixelFormat.Format32bppArgb);
        var dd = dst.LockBits(r, ImageLockMode.WriteOnly, PixelFormat.Format32bppArgb);
        int n = sd.Stride * src.Height;
        var px = new byte[n];
        Marshal.Copy(sd.Scan0, px, 0, n);
        for (int i = 0; i < n; i += 4) {
            float b = px[i] / 255f, g = px[i + 1] / 255f, rr = px[i + 2] / 255f;
            float max = Math.Max(rr, Math.Max(g, b)), min = Math.Min(rr, Math.Min(g, b));
            float v = max, s = max > 0 ? (max - min) / max : 0;
            // 갈색(눈·코 잔여) 픽셀은 그대로 둔다: 빨강이 가장 크고 채도가 높음
            if (rr >= g && rr >= b && s > 0.25f) continue;
            float w = Smooth(0.13f, 0.05f, s) * Smooth(0.55f, 0.75f, v); // 흰 부분 정도
            float refV = 0.74f + (0.90f - 0.74f) * w;
            float shade = v / refV;
            for (int c = 0; c < 3; c++) {
                float t = (body[2 - c] + (belly[2 - c] - body[2 - c]) * w) / 255f;
                px[i + c] = (byte)Math.Max(0, Math.Min(255, (int)(t * shade * 255f + 0.5f)));
            }
        }
        Marshal.Copy(px, 0, dd.Scan0, n);
        src.UnlockBits(sd); dst.UnlockBits(dd);
        return dst;
    }
}
"@

function Hex([string]$h) { @([Convert]::ToInt32($h.Substring(0,2),16), [Convert]::ToInt32($h.Substring(2,2),16), [Convert]::ToInt32($h.Substring(4,2),16)) }

# glb에서 base color 텍스처(material_0 → texture 1 → image) 추출
$bytes = [IO.File]::ReadAllBytes((Resolve-Path $Glb))
$jsonLen = [BitConverter]::ToUInt32($bytes, 12)
$json = [Text.Encoding]::UTF8.GetString($bytes, 20, $jsonLen) | ConvertFrom-Json
$binStart = 20 + $jsonLen + 8
$texIndex = $json.materials[0].pbrMetallicRoughness.baseColorTexture.index
$img = $json.images[$json.textures[$texIndex].source]
$bv = $json.bufferViews[$img.bufferView]
$off = if ($bv.byteOffset) { $bv.byteOffset } else { 0 }
$ms = New-Object IO.MemoryStream(, $bytes[($binStart + $off)..($binStart + $off + $bv.byteLength - 1)])
$src = New-Object Drawing.Bitmap $ms

# 스킨 색 (BearSkin.kt 와 같은 키)
$skins = [ordered]@{
    classic_beige      = @("D3BC9C", "F1E6D0")
    matte_mint         = $null              # 원본 그대로
    pastel_lavender    = @("B9A1E0", "EEDDBF")
    obsidian_black     = @("34363C", "4A4D55")
    polar_white        = @("E9EBEF", "D6DAE0")
    chocolate_brown    = @("7B4A2D", "CFA67C")
    frosty_jelly       = @("72DCC4", "B4F2E4")
    metallic_rose_gold = @("D49C8A", "EFCDBF")
}
New-Item -ItemType Directory -Force $Out | Out-Null
foreach ($k in $skins.Keys) {
    $path = Join-Path (Resolve-Path $Out) "$k.png"
    if ($null -eq $skins[$k]) { $src.Save($path, [Drawing.Imaging.ImageFormat]::Png) }
    else {
        $res = [BearRecolor]::Recolor($src, (Hex $skins[$k][0]), (Hex $skins[$k][1]))
        $res.Save($path, [Drawing.Imaging.ImageFormat]::Png); $res.Dispose()
    }
    "{0} {1:N0}KB" -f $k, ((Get-Item $path).Length / 1KB)
}
$src.Dispose()
