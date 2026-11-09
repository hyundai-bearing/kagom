# PR·배포 가이드

카곰(bearing-test) 코드를 고쳐서 PR로 올리고, `main`에 합치고, APK로 배포하기까지의 순서와 유의사항.
처음 하는 사람은 1장부터, 익숙하면 [한눈에 보는 체크리스트](#한눈에-보는-체크리스트)만 봐도 된다.

---

## 1. 준비

| 필요한 것 | 메모 |
|---|---|
| Android Studio | Gradle은 Android Studio에 들어 있는 JDK(`jbr`)로 돌린다 |
| Android SDK | 경로는 각자 `local.properties`의 `sdk.dir` (커밋하지 않는다) |
| 에뮬레이터 | **Pleos Connect** (가로 = Full 화면)와 **폰 세로** (= Split 화면) 둘 다 |
| GitHub CLI `gh` | PR을 터미널에서 올릴 때 (`gh auth login`) |
| Blender 5.x | 캐릭터·옷 렌더를 다시 만들 때만 (`tools/closet3d/build_closet.py`) |

**JAVA_HOME이 없다고 나올 때** (`java`가 PATH에 없는 PC가 많다):

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:compileDebugKotlin
```

Pleos SDK는 `settings.gradle.kts`의 Pleos 저장소(`nexus-playground.pleos.ai`)에서 받는다. 사내망·VPN에서 막히면 첫 빌드가 실패하니 저장소 접속부터 확인한다.

---

## 2. 브랜치

- 항상 최신 `main`에서 딴다: `git switch main && git pull && git switch -c feature/<요약>`
- 이름: `feature/<요약>` (기능), `fix/<요약>` (버그), `docs/<요약>` (문서). 요약은 영문 kebab-case.
- **`main`에 직접 push하지 않는다.** 모든 변경은 PR로.
- 브랜치 하나 = 목적 하나. "버그 고치다가 겸사겸사 리팩터링"은 PR을 나눈다.

---

## 3. 커밋

- 메시지는 영어 명령형 한 줄 요약 (`Fix stale item thumbnails ...`), 필요하면 빈 줄 뒤에 이유를 적는다.
- `Co-Authored-By` 줄은 넣지 않는다 (팀 규칙, `CLAUDE.md`).

**커밋하면 안 되는 것** (`.gitignore`에 있지만 `git add -A`·`-f` 때 주의):

- `local.properties` (개인 SDK 경로)
- 키스토어 `*.jks`, `*.keystore`, 서명 비밀번호, API 키
- `build/`, `.gradle/`, `.idea/`, `.kotlin/`
- 렌더 작업용 임시 파일 (`bear.glb`, `skins/`는 git 기록에서 꺼내 쓰는 것이라 다시 넣지 않는다)

**에셋(`app/src/main/assets/kagom`)을 바꿀 때:**

- 손으로 고친 이미지를 넣지 말고 `tools/closet3d/build_closet.py`로 다시 만든다. 스크립트 변경과 산출물을 **같은 커밋**에 넣는다.
- PR 본문에 다시 만드는 명령과 에셋 크기 변화를 적는다. APK 크기와 첫 실행 로딩에 바로 영향이 간다.

> Windows에서 `LF will be replaced by CRLF` 경고는 무시해도 된다 (`core.autocrlf=true`). 다만 diff에 파일 전체가 바뀐 것처럼 보이면 편집기가 줄바꿈을 바꾼 것이니 되돌린다.

---

## 4. PR 올리기

### 올리기 전 확인

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug      # 설치해서 직접 볼 때
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

- **Full과 Split 둘 다** 본다. 같은 화면이 레이아웃 두 벌이라 한쪽만 고치고 다른 쪽이 깨지는 일이 많다.
- **주행 중 제한**을 본다. 기어 P가 아닐 때는 치장 저장, 돌려보기, 캐릭터 터치 반응 등이 막혀야 한다.
  디버그 빌드에서는 레일(Full)·탭바(Split) 빈 곳을 길게 누르면 시연 패널이 열리고, adb로도 바꿀 수 있다:

  ```
  adb shell am start --activity-single-top -n com.example.roro/.MainActivity --es demo_gear D   # 주행
  adb shell am start --activity-single-top -n com.example.roro/.MainActivity --es demo_gear P   # 주차
  adb shell am start --activity-single-top -n com.example.roro/.MainActivity --es demo_visit LM-GN-01
  adb shell am start --activity-single-top -n com.example.roro/.MainActivity --ez demo_reset true
  ```
  (전체 목록은 `MainActivity.kt` 머리 주석)

### 올리기

```bash
git push -u origin feature/<요약>
gh pr create --base main --title "<한국어 제목>"    # 본문은 템플릿이 채워진다
```

- 제목·본문은 한국어. 본문은 `.github/pull_request_template.md` 형식을 지킨다.
- UI가 바뀌면 **Full·Split 스크린샷**을, 애니메이션이면 짧은 GIF/MP4를 붙인다.
- 아직 작업 중이면 Draft로 올린다 (`gh pr create --draft`).

### 다른 PR 위에 쌓을 때 (stacked PR)

아직 머지되지 않은 PR B 위에 이어서 작업해야 할 때:

1. B 브랜치에서 새 브랜치를 딴다.
2. PR을 만들 때 **base를 B 브랜치로** 둔다 (`gh pr create --base <B 브랜치>`). 그래야 diff에 내 변경만 보인다.
3. 본문 첫 줄에 `#B번호 머지 후 리뷰`라고 적는다.
4. B가 머지되고 **B 브랜치를 지우면** GitHub이 내 PR의 base를 자동으로 `main`으로 바꿔 준다.

**유의:** 아래에 다른 PR이 쌓여 있는 PR은 **Squash merge 하지 않는다.** 커밋이 새로 만들어져서 위 PR들이 같은 변경을 두 번 가진 것처럼 보이고 충돌이 난다.

---

## 5. 리뷰와 머지

- 리뷰어 **1명 이상 승인** 후, **작성자가** 머지한다.
- 머지 방식은 **Create a merge commit** (쌓인 PR과 섞여도 안전).
- 머지 후 브랜치는 지운다 (GitHub 버튼 또는 `git push origin --delete <브랜치>`).
- `main`과 충돌하면 내 브랜치에 `main`을 merge해서 푼다. **이미 리뷰 중인 브랜치에 force push 하지 않는다** (리뷰 코멘트 위치가 사라지고, 위에 쌓인 브랜치가 깨진다).
- 리뷰 코멘트는 고친 커밋을 올린 뒤 "반영했어요 (커밋 해시)"로 답하고, 해결 표시는 리뷰어가 한다.

---

## 6. 배포 (APK)

### 시연·내부 공유용 (지금 방식)

```powershell
.\gradlew.bat :app:assembleRelease
# → app\build\outputs\apk\release\app-release.apk
```

- release 빌드는 R8 축소 + 리소스 축소가 켜져 있다. **debug에서 되던 것이 release에서 깨질 수 있으니** 배포 전에 release APK를 꼭 설치해서 한 바퀴 돌려 본다 (특히 Pleos SDK: 내부에서 Gson·리플렉션을 써서, 규칙이 빠지면 모든 신호가 "확인 불가"로 읽힌다).
  깨지면 `app/proguard-rules.pro`에 keep 규칙을 추가한다.
- 지금 release는 **디버그 키로 서명**된다 (`app/build.gradle.kts`). 시연용으로만 쓴다.

### 버전 올리기

`app/build.gradle.kts`의 `versionCode`(정수, 매번 +1)와 `versionName`(사람이 읽는 버전)을 **배포 PR 안에서** 올린다. `versionCode`가 같거나 낮으면 기존 설치 위에 덮어 설치되지 않는다.

### GitHub Release에 올리기

`main`에 머지된 커밋에서만 만든다.

```bash
git switch main && git pull
gh release create v2.1.0 app/build/outputs/apk/release/app-release.apk \
  --title "카곰 v2.1.0" --notes "변경 사항 요약 (머지된 PR 번호)"
```

### 앱마켓·외부 제출 (정식 배포)

- **정식 서명 키**로 서명해야 한다. 키는 레포에 넣지 않고 팀이 따로 보관한다 (잃어버리면 같은 앱으로 업데이트할 수 없다).
  서명 정보는 `~/.gradle/gradle.properties`나 환경 변수로 넘기고, `build.gradle.kts`에 비밀번호를 적지 않는다.
- `applicationId`가 아직 `com.example.roro`다. 마켓에 올리기 전에 정식 패키지명으로 바꿔야 한다 (한 번 올리면 못 바꾼다).
- Pleos의 `PRIVATE_*` API는 값과 앱마켓 승인이 보장되지 않는다. 쓰게 되면 `data/`의 신호 소스 안에만 두고, 없어도 앱이 돌아가게 만든다 (NFR-06).

---

## 7. 그 밖의 유의사항

- **AI로 코드를 쓸 때**: 코드 규칙(구조, 토큰, Full/Split, 기어 P 제한, 저장 호환, 생성 에셋 등)은 `CLAUDE.md`에 있다. Claude Code는 자동으로 읽고, Codex·Cursor 등은 `AGENTS.md`를 거쳐 같은 파일을 읽는다. 다른 도구를 쓰면 `CLAUDE.md`를 프롬프트나 규칙 파일에 넣는다. 규칙이 바뀌면 `CLAUDE.md`만 고친다.
- **디버그 전용 기능**: 시연 패널과 `demo_*` adb 옵션은 debuggable 빌드에서만 열린다. release에서 시연하려면 debug APK를 쓴다.
- **권한**: Pleos 차량·위치 권한(`AndroidManifest.xml`)이 바뀌면 PR 본문에 적는다. 위치는 랜드마크 방문 판정에만 쓰고 저장하지 않는다.
- **요구사항 문서**: `docs/requirements.md`의 완료 표시가 실제 코드와 다를 수 있다 (11/6 Figma 리빌드 때 말풍선·질문 메뉴·TTS가 빠졌다). 기능을 다시 넣거나 뺄 때 문서도 같이 고친다.
- **큰 바이너리**: 렌더 산출물이 계속 쌓이면 레포가 무거워진다. 한 PR에서 에셋을 여러 번 다시 만들었다면 마지막 결과만 남도록 커밋을 정리한 뒤 올린다 (리뷰 시작 전에만).

---

## 한눈에 보는 체크리스트

**PR 올리기 전**
- [ ] 최신 `main`(또는 쌓을 PR 브랜치)에서 딴 브랜치, 이름 규칙 지킴
- [ ] `compileDebugKotlin`, `testDebugUnitTest` 통과
- [ ] 에뮬레이터 Full·Split 둘 다 확인, 주행 중 제한 확인
- [ ] `local.properties`·키·`build/` 안 들어감
- [ ] 에셋을 바꿨으면 스크립트도 같은 커밋, 명령과 크기 변화를 본문에

**머지 전**
- [ ] 승인 1명 이상
- [ ] 쌓인 PR이 있으면 Squash 금지, 머지 순서 확인

**배포 전**
- [ ] `main`에서 release APK 빌드, 설치해서 한 바퀴 확인
- [ ] `versionCode`/`versionName` 올림
- [ ] 정식 배포면 정식 키·패키지명 확인
