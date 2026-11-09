# 카곰 (bearing-test)

Pleos Connect 차량용 Android 앱. 차량 상태에 따라 표정이 바뀌는 곰 캐릭터 "카곰"을 키우고, 주행·랜드마크 방문·정비로 치장 아이템을 모은다.

## 빠르게 시작

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"   # java가 PATH에 없을 때
.\gradlew.bat :app:assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Pleos Connect 에뮬레이터(가로, Full 화면)와 폰 에뮬레이터(세로, Split 화면)에서 확인한다.

## 문서

| 문서 | 내용 |
|---|---|
| [docs/PR_AND_DEPLOY.md](docs/PR_AND_DEPLOY.md) | 브랜치·커밋·PR·리뷰·머지·APK 배포 순서와 유의사항 |
| [docs/requirements.md](docs/requirements.md) | 요구사항 (FR/NFR) |
| `docs/IMPLEMENTATION.md` | 구현 노트: 구조, 렌더 파이프라인, 시연 방법 (Figma 리빌드 브랜치에 있음) |
| [.github/pull_request_template.md](.github/pull_request_template.md) | PR 본문 양식 |
