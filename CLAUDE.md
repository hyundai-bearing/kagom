## 커밋 규칙

- 커밋에 "Co-Authored-By" 줄을 넣지 않는다.
- 커밋 메시지는 영어 명령형 한 줄 요약, 필요하면 빈 줄 뒤에 이유를 적는다.

## 프로젝트 구조

카곰(Kagom): Pleos Connect 인포테인먼트용 반려 캐릭터 앱. Kotlin + Jetpack Compose, 모듈 하나(`app`), 패키지 `com.example.roro`.

- `core/`: 게임 규칙과 마스터 데이터. 순수 Kotlin이다(`android.*`, Compose, Pleos import 금지). 그래야 `app/src/test/.../core`에서 단위 테스트할 수 있다.
- `data/`: Pleos 차량 신호, 위치, 저장소(`KagomStore`), 지도 에셋. SDK 호출은 모두 여기에만 둔다.
- `ui/`: `theme/`(토큰, 글자, Full/Split 스케일), `components/`(`PleosKit` = Pleos ivi_* 위젯, `KagomComponents` = Figma 부록 C, `Glyphs`, `KagomCharacter`, `KagomTurntable`), `screens/`(SCR-00~06, POP-01~05), `KagomController`(앱 상태와 동작), `KagomApp`(화면 이동, 팝업 큐, 시연 패널).
- 디자인 자료: Figma "Kagom App"을 브리지로 직접 읽는다(`docs/FIGMA_BRIDGE.md`, 읽기 도구만 쓰고 Figma 파일은 고치지 않는다). 작업 가이드 PDF와 버전별 차이는 `docs/design/`(가장 최근 날짜 폴더, `README.md`). 코드 주석의 Figma 노드 번호는 가이드 부록 A·C 기준. 그 밖에 `docs/spec.pdf`, `docs/requirements.md`. 현재 구현 설명은 `docs/IMPLEMENTATION.md`이고, 동작이나 구조가 바뀌면 같이 고친다.

## 코드 규칙

여러 사람과 AI가 써도 코드가 한결같도록:

- 먼저 주변 코드를 읽고 있는 것을 쓴다(`PleosKit`, `KagomComponents`, `Glyphs`, 조사는 `ui/Korean.kt`의 `josa()`). 새 컴포넌트·헬퍼는 그다음이다. 작업에 필요한 만큼만 고치고, 건드리지 않은 코드는 포맷이나 이름을 바꾸지 않는다.
- 주변 스타일을 따른다: 들여쓰기 4칸, 여러 줄 인자 목록은 끝에 쉼표, 식별자는 영어, KDoc·주석은 한국어로 "왜"를 적는다(디자인 출처 "Figma 149:4593", "가이드 v1.2 SCR-05" 또는 세운 가정). "무엇을"은 적지 않는다.
- Gradle 의존성은 사용자 확인 없이 추가하지 않는다. Material3는 `Text`만 쓴다. UI는 Material `Button`, `Card`, `Scaffold` 등이 아니라 `PleosKit`/`KagomComponents`로 만든다.
- 색·모서리·간격·글자는 `ui/theme`의 `KagomColor`, `KagomRadius`, `KagomSpace`, `KagomType`만 쓴다. `Color(0x…)`는 `Tokens.kt`, 숫자 `sp`는 `Type.kt` 밖에서 쓰지 않는다. 토큰이 없으면 Figma 변수 경로 이름으로 `Tokens.kt`에 추가한다.
- 글자 크기는 Pleos Connect 글자 단계다(최소 20px, `Type.kt`). `KagomType` 이름은 Figma 스타일 이름이고 값은 그보다 크다. Figma에서 본 스타일 이름을 그대로 고르고, 글자가 안 들어가면 글자를 줄이지 말고 칸을 키운 뒤 `docs/IMPLEMENTATION.md`에 적는다.
- `1.dp` = Figma 1px이다(`KagomTheme`이 밀도를 맞춘다). Figma 숫자를 그대로 옮긴다.
- 모든 화면은 레이아웃이 두 벌이다: `LocalLayoutMode.current`가 FULL(가로 1920 기준)과 SPLIT(세로, 640 기준). 둘 다 고치고 둘 다 확인한다.
- 주행 중 제한: 화면은 `gearP`를 받는다. 데이터를 바꾸거나 운전자의 주의를 끄는 것(저장, 장착, 돌려보기, 캐릭터 터치 반응, 팝업, 상세 카드)은 기어가 P가 아니면 막거나 숨긴다. 팝업은 P가 될 때까지 큐에서 기다린다(`KagomController.activePopup`).
- UI 글자는 한국어로 코드 안에 바로 쓴다(`strings.xml` 없음). 지금 문구처럼 짧은 해요체로 쓴다.
- 상태: 앱 상태는 `KagomController`의 `StateFlow`에 둔다. 화면은 받은 값과 콜백으로만 그리고, 화면 안에서만 쓰는 상태만 `remember`/`rememberSaveable`에 둔다.
- 규칙(EXP, 티어, 도전과제, 랜드마크, 심각도, 지도 해금 집계)은 `core/`에 단위 테스트와 함께 둔다. ID 형식은 `ITM-H-01`, `QST-01`, `LM-GN-01`. 시연용 예시 데이터는 `EX-` 접두어를 붙여 `core/Examples.kt`에 두고 저장하지 않는다.
- 저장소(`data/KagomStore`, SharedPreferences에 JSON 하나): 새 필드는 `opt*`와 기본값으로 읽어 기존 설치에서도 열리게 한다. 기존 키 이름은 바꾸거나 다른 뜻으로 다시 쓰지 않는다.
- 차량·위치 신호는 `data/`의 소스로만 읽는다. 신호가 없으면 앱이 죽지 않고 "확인 불가"로 보여야 하고, Pleos `PRIVATE_*` API 없이도 앱이 돌아가야 한다.
- 디버그 전용: 시연 패널(`DebugPanel.kt`)과 `demo_*` 인텐트 옵션(`MainActivity`, debuggable 빌드만). 에뮬레이터에서 만들기 어려운 상태를 추가하면 시연 훅도 만들고 `MainActivity` 머리 주석에 적는다.
- 로그: `Kagom…` 태그로 `Log.w`/`Log.i`. 위치나 개인정보는 로그에 남기지 않는다.
- 디자인에 없는 값: 정해서 쓰고 `docs/IMPLEMENTATION.md`의 "디자인에 없던 값 (가정)"에 적는다.
- 생성된 파일은 손으로 고치지 않는다: `app/src/main/assets/kagom/**`는 `tools/closet3d/build_closet.py`, `app/src/main/assets/regions/**`는 `tools/regions/*.py`에서 나온다. 스크립트를 고치고 다시 만들어 둘 다 커밋한다. Python 도구는 `python -I -X utf8`로 돌리고 다운로드·작업 폴더는 레포 밖에 둔다.
- 확인은 Pleos Connect 에뮬레이터(Full)와 세로 폰 에뮬레이터(Split)에서 Figma 화면(브리지 `get_screenshot`)과 비교한다.

## PR 규칙

팀 전체 절차는 `docs/PR_AND_DEPLOY.md`. 에이전트가 지킬 것:

- `main`에 직접 push하지 않는다. 최신 `main`에서 `feature/<주제>`, `fix/<주제>`, `docs/<주제>`(영문 kebab-case)로 브랜치를 딴다. 브랜치·PR 하나에 목적 하나.
- PR 제목과 본문은 한국어. 본문은 `.github/pull_request_template.md` 형식을 따른다(모든 절을 채우고, 해당이 없으면 절을 지우지 말고 "해당 없음"이라고 쓴다).
- PR을 올리기 전에 `:app:compileDebugKotlin`, `:app:testDebugUnitTest`가 통과해야 한다(`JAVA_HOME`은 Android Studio의 `jbr`). 에뮬레이터에서 무엇을 확인했고 무엇을 못 했는지 본문에 적는다.
- UI 변경: Full(가로, Pleos Connect)과 Split(세로) 스크린샷을 둘 다 붙인다. 주행 중 제한(기어 P 아님)도 확인한다.
- `app/src/main/assets/kagom` 에셋 변경: `tools/closet3d/build_closet.py`로 다시 만들고, 스크립트 변경을 같은 커밋에 넣고, 정확한 명령과 에셋 크기 변화를 본문에 적는다.
- 아직 머지 안 된 PR 위에 쌓을 때: 새 PR의 base를 그 브랜치로 하고(`main` 아님), 본문 첫 줄에 `#<번호> 머지 후 리뷰`라고 쓴다.
- 사용자가 요청하지 않으면 PR을 머지·닫기·승인·force-push하지 않는다. 위에 다른 PR이 쌓여 있는 PR은 squash 머지하지 않는다.
- `local.properties`, 키스토어, 서명 비밀번호, `build/` 산출물은 커밋하지 않는다.
