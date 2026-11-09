# 카곰 (Kagom)

Pleos Connect 차량용 Android 앱. 차량 상태에 따라 표정이 바뀌는 곰 캐릭터 "카곰"을 키우고, 주행·랜드마크 방문·정비로 치장 아이템을 모은다.

Kotlin + Jetpack Compose, 모듈 하나(`app`). 차량 신호와 위치는 Pleos Playground SDK(Vehicle 2.3.2, FusedLocation 1.1.3)로 받는다.

## 화면

디자인 기준은 Figma "Kagom App"과 작업 가이드 v1.2 (`docs/design/2026-11-09/`). 모든 화면은 Full(가로, 1920 기준)과 Split(세로, 640 기준) 두 벌이다.

| 화면 | 내용 |
|---|---|
| SCR-00 | 주행 중에는 첫 설정을 막는 안내 |
| SCR-01·02 | 온보딩: 성향 테스트 3문항 → 스킨(8종) 결과와 이름 짓기 |
| SCR-03 홈 | 연료·배터리, 엔진오일·브레이크액·미션오일·워셔액, 엔진 경고, 정비 주기(엔진오일 교체·1만 km 점검)에 따라 카곰 표정과 상태 행이 바뀐다. 탭 반응, 돌려보기 |
| SCR-04 도전과제 | 주행 거리·랜드마크 방문·정기 점검 도전과제 5개, EXP와 티어 |
| SCR-05 랜드마크 지도 | 전국 → 시·도 → 시·군·구 3단계. 랜드마크 근처에 주차(기어 P)하면 방문으로 인정하고 구획을 해금한다 |
| SCR-06 치장 | 아이템 6종·칭호 장착, 위아래·좌우 돌려보기, 페이지 넘김 |
| POP-01~05 | 주행 중 토스트, 보상 언박싱, 랜드마크 해금, 티어 승급, 정비 시트 |

**주행 중(기어 P 아님)** 에는 저장·장착·돌려보기·터치 반응·팝업·상세 카드를 막거나 숨기고, 팝업은 P가 될 때까지 기다린다.

## 빠르게 시작

```powershell
git clone https://github.com/hyundai-bearing/kagom.git
cd kagom
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"   # java가 PATH에 없을 때
.\gradlew.bat :app:assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

- Android SDK 경로는 Android Studio로 한 번 열면 `local.properties`에 생긴다 (커밋하지 않는다).
- Pleos SDK는 `nexus-playground.pleos.ai`에서 받는다. 사내망·VPN에서 막히면 첫 빌드가 실패한다.
- Pleos Connect 에뮬레이터(가로, Full)와 폰 에뮬레이터(세로, Split)에서 확인한다.
- 차 없이 상태를 바꾸려면 디버그 빌드에서 레일(Full)·탭바(Split) 빈 곳을 길게 눌러 시연 패널을 연다. adb 명령은 `MainActivity.kt` 머리 주석에 있다.

## 폴더

```
app/src/main/java/com/example/roro/
  core/     게임 규칙·마스터 데이터 (순수 Kotlin, 단위 테스트는 app/src/test)
  data/     Pleos 차량·위치 신호, 저장소(KagomStore)
  ui/       theme(토큰) · components · screens · KagomController · KagomApp
app/src/main/assets/
  kagom/    캐릭터·치장 렌더 (tools/closet3d/build_closet.py가 만든다)
  regions/  지도 데이터 (tools/regions/*.py가 OpenStreetMap에서 만든다)
tools/      렌더(Blender)·지도(Python)·측정 도구. 곰 모델 원본은 tools/closet3d/source/
docs/       기획(spec.pdf)·요구사항·구현 노트·PR/배포 가이드·디자인 자료
```

## 알아 둘 것

자세한 내용은 [docs/PR_AND_DEPLOY.md](docs/PR_AND_DEPLOY.md) 6·7장.

- **`main`에 직접 push하지 않는다.** 모든 변경은 PR로, 승인 1명 뒤 merge commit으로 합친다.
- **release 빌드는 R8로 줄인다.** Pleos SDK의 keep 규칙이 빠지면 모든 신호가 "확인 불가"가 되니, 배포 전에 release APK를 설치해서 확인한다.
- **서명**: 정식 키 정보(`kagom.signing.*`)가 없으면 release도 디버그 키로 서명된다. 그 APK는 시연용이다.
- **패키지명은 아직 `com.example.roro`** 다. 앱마켓에 올리기 전에 바꾼다 (요구사항 D11).
- **생성 파일은 손으로 고치지 않는다.** `assets/kagom`, `assets/regions`는 스크립트를 고쳐 다시 만든다.
- **지도 화면의 "© OpenStreetMap contributors" 표시는 지우지 않는다.** ([THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md))
- **`docs/requirements.md`의 "현재 데모" 열은 11/5 기준**이다. 11/6 리빌드 때 빠진 기능(음성, 질문 메뉴 등)은 문서 맨 위에 있다.
- **AI로 코드를 쓸 때** 규칙은 [CLAUDE.md](CLAUDE.md)에 있다 (Codex·Cursor 등은 [AGENTS.md](AGENTS.md)를 거쳐 같은 파일).

## 문서

| 문서 | 내용 |
|---|---|
| [docs/PR_AND_DEPLOY.md](docs/PR_AND_DEPLOY.md) | 브랜치·커밋·PR·리뷰·머지·APK 배포 순서와 유의사항 |
| [docs/IMPLEMENTATION.md](docs/IMPLEMENTATION.md) | 구현 노트: 구조, 디자인에 없던 값(가정), 지도·렌더 파이프라인, 시연, 검증 |
| [docs/requirements.md](docs/requirements.md) | 요구사항 (FR/NFR), 결정 사항 |
| [docs/design/README.md](docs/design/README.md) | 디자인 자료 버전별 차이, 새 버전 받는 법 |
| [CHANGELOG.md](CHANGELOG.md) | 버전별 변경 내역 |
| [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) | 외부 라이브러리·폰트·지도 데이터의 라이선스 |
| [.github/pull_request_template.md](.github/pull_request_template.md) | PR 본문 양식 |

## 이력

이 레포는 시험 레포 `hyundai-bearing/bearing-test`의 `main`(df0333c, PR #1~#11)을 커밋 기록 없이 옮겨 시작했다. 그 전의 커밋·PR·리뷰 기록은 bearing-test에서 본다. bearing-test의 PR 번호는 이 레포의 PR 번호와 다르다.
