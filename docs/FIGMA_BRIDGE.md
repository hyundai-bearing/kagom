# Figma 연결 (Figma MCP Bridge)

AI 도구(Claude Code, Codex, Cursor 등)가 Figma "Kagom App" 파일을 직접 읽게 하는 방법.
화면·컴포넌트 PNG를 내보내 레포에 올리지 않고(예전 `docs/design/<날짜>/*.zip`), 구현할 때 Figma에서 바로 읽는다.

> **확인 (2026-11-09, Windows 11 · Figma 데스크톱 126.11 · Node 26 · 브리지 0.0.25)**: 원본을 복제한 내 초안 파일에서 연결, `list_files`·`get_metadata`·`get_node`·`get_screenshot` 동작 확인. 원본 파일(보기 권한)에서는 플러그인을 켤 수 없었다 → "어느 파일에서 켜나".

## 무엇인가

[gethopp/figma-mcp-bridge](https://github.com/gethopp/figma-mcp-bridge) (MIT). Figma **데스크톱 앱**에서 도는 플러그인과 내 PC에서 도는 MCP 서버가 로컬 WebSocket(`ws://localhost:1994/ws`)으로 이어진다.

```
Figma 데스크톱 앱 ── 플러그인 ──(localhost:1994)── MCP 서버(npx) ── AI 도구
```

- Figma REST API를 쓰지 않아 **호출 한도가 없다**. 공식 Figma MCP는 View/Collab 좌석이 월 20회, Starter 팀 파일은 금방 막혔다(11/6).
- 플러그인을 켠 파일만 읽는다. 여러 파일을 동시에 켜 두면 `fileKey`로 고른다.
- 서버는 `127.0.0.1`에서만 듣는다. 인증이 없으니 `FIGMA_BRIDGE_HOST`를 바꿔 밖에 열지 않는다.

## 준비물

| 필요한 것 | 메모 |
|---|---|
| Figma **데스크톱 앱** | 브라우저판 Figma는 개발 플러그인을 못 불러온다 |
| Node.js (LTS) | `npx`로 MCP 서버를 받는다 |
| Kagom 파일 **편집 권한** | 보기 권한만 있는 파일에서는 플러그인을 켤 수 없다 (아래 "어느 파일에서 켜나") |

Windows에서 설치 (PowerShell):

```powershell
winget install --id OpenJS.NodeJS.LTS -e
winget install --id Figma.Figma -e
# 새 터미널을 열고 확인
node --version; npx --version
```

## 연결하기

### 1. MCP 서버를 AI 도구에 등록

버전을 고정한다 (플러그인과 서버 버전을 맞추고, 패키지가 바뀌어도 모르게 업데이트되지 않게). 지금 기준 `0.0.25`.

**Claude Code** (이 레포 폴더에서, 내 PC에만 등록):

```powershell
# Windows (네이티브): npx는 cmd /c 로 감싸야 한다
claude mcp add figma-bridge -- cmd /c npx -y @gethopp/figma-mcp-bridge@0.0.25
```
```bash
# macOS / Linux / WSL
claude mcp add figma-bridge -- npx -y @gethopp/figma-mcp-bridge@0.0.25
```

`claude mcp list`에 `figma-bridge`가 보이면 된다. 등록 후 Claude Code를 다시 시작한다.

**Codex·Cursor·Claude Desktop 등** (각 도구의 MCP 설정 JSON):

```json
{
  "figma-bridge": {
    "command": "npx",
    "args": ["-y", "@gethopp/figma-mcp-bridge@0.0.25"]
  }
}
```

### 2. Figma 플러그인 불러오기 (한 번만)

1. [릴리스 페이지](https://github.com/gethopp/figma-mcp-bridge/releases)에서 서버와 같은 버전의 `figma-mcp-bridge-v0.0.25.zip`을 받아 레포 **밖** 아무 폴더에 푼다.
2. Figma 데스크톱 앱 → 메뉴 `Plugins > Development > Import plugin from manifest…` → 푼 폴더의 `plugin/manifest.json`을 고른다.

### 3. 파일에서 플러그인 켜기 (작업할 때마다)

1. Figma 데스크톱 앱에서 Kagom 파일을 연다.
2. `Plugins > Development > Figma MCP Bridge`를 실행한다. 플러그인 창이 떠 있는 동안만 연결된다.
3. AI 도구에서 "연결된 Figma 파일 보여 줘"라고 하면 `list_files`로 파일 이름과 `fileKey`가 나와야 한다.

## 어느 파일에서 켜나

원본 [Kagom App](https://www.figma.com/design/F4cs5Mwtl1sljhVb6MQrJK/Kagom-App?node-id=5-1700) (`F4cs5Mwtl1sljhVb6MQrJK`)은 디자이너 파일이고 우리 계정은 **보기 권한**이다.
보기 권한 파일에서는 플러그인 메뉴·Actions의 "Import plugin"이 나오지 않고, Dev Mode는 유료 좌석이 필요해 "Request access"만 뜬다. 그래서 **원본을 내 초안으로 복제해서** 그 사본에서 켠다.

1. 원본을 연 채로 메인 메뉴(왼쪽 위 Figma 아이콘) → `File > Duplicate to your drafts`
2. 팀을 고른다: **무료 Starter 팀**(예: "085's Starter team")의 초안. 교육용 팀(prove)은 학생 인증 전에는 사본도 "View only"로 열려 쓸 수 없었다.
3. 열린 사본 `Kagom App (Copy)`에서 플러그인을 켠다 (아래 "연결하기" 2·3).

- 사본은 복제한 시점의 내용이다. 디자인이 바뀌면 다시 복제한다 (Figma 버전 이름을 `docs/design/README.md`에 같이 적는다).
- 디자이너에게 원본 편집 권한을 받으면 복제 없이 원본에서 켜도 된다.
- 초안 파일은 `list_files`의 `fileKey`가 `unsaved-…`처럼 나온다. 파일이 하나만 연결돼 있으면 `fileKey` 없이 불러도 된다. 노드 번호는 원본과 같다 (복제해도 유지됨, `5:1700` = "Kagom App" 페이지 확인).
**원본 파일은 고치지 않는다.** 이 플러그인에는 쓰기 도구도 있지만, 디자인 수정은 디자이너가 한다.

## 쓰는 법

AI 도구에 Figma 노드 번호나 링크를 주고 시키면 된다. 링크의 `node-id=5-1700`은 노드 `5:1700`이다.
화면별 노드 번호는 작업 가이드 PDF 부록 A(화면)·부록 C(컴포넌트)에 있다.

| 하고 싶은 것 | 쓰는 도구 |
|---|---|
| 연결된 파일·페이지 보기 | `list_files`, `get_metadata` |
| 화면 구조·크기·색·글자 읽기 | `get_node`, `get_design_context` |
| 지금 Figma에서 고른 것 읽기 | `get_selection` |
| 디자인 토큰(색·간격·글자 변수) | `get_variable_defs`, `get_styles` |
| 화면 그림 보기·저장 (PR 비교용) | `get_screenshot`, `save_screenshots` |
| 프로토타입 흐름 | `get_prototype_connections`, `trace_prototype_flow` |

예: "Figma 노드 149:4593(SCR-05 L3 강릉)을 읽고 `MapScreen.kt`와 다른 점을 찾아줘", "SCR-06 Split 화면 스크린샷을 저장해서 에뮬레이터 화면과 비교해줘".

- 읽기 도구만 쓴다. 쓰기 도구(`set_*`, `create_*`, `delete_nodes`)는 쓰지 않고, 임의 스크립트 실행(`run_script`)은 켜지 않는다(`FIGMA_BRIDGE_ENABLE_RUN_SCRIPT`를 설정하지 않음).
- `save_screenshots`로 받은 그림은 레포에 커밋하지 않는다. 필요하면 PR 본문에 붙인다.
- `get_screenshot`은 그림을 JSON 글자(`exports[].base64`)로 돌려준다. `scale`(예: 0.5)로 크기를 줄여 받는다.

## 디자인이 바뀌면

zip을 받아 올리는 대신 (`docs/design/README.md` "새 버전을 받으면"):

1. 작업 가이드 PDF만 `docs/design/<받은 날짜>/`에 넣는다 (Figma 밖 문서라 브리지로 못 읽는다).
2. `docs/design/README.md`에 날짜, Figma 버전 이름(파일 `Version history`), 바뀐 화면의 노드 번호를 한 줄씩 적는다.
3. 구현은 브리지로 Figma를 직접 읽어서 한다. PR 본문에 어느 날짜·버전 기준인지 적는다.

## 문제가 생기면

| 증상 | 확인 |
|---|---|
| AI 도구에 `figma-bridge` 도구가 없다 | `claude mcp list`, AI 도구 재시작. Windows Claude Code는 `cmd /c npx` 형태인지 |
| 연결된 파일이 없다고 나온다 | Figma 데스크톱 앱에서 그 파일에 플러그인 창이 떠 있는지. 브라우저판이 아닌지 |
| 플러그인 메뉴에 안 보인다 | `Import plugin from manifest`를 다시. 같은 PC·같은 Figma 계정인지 |
| 보기 전용 파일이라 막힌다 ("You can only view and comment", Dev Mode "Request access") | "어느 파일에서 켜나": 초안으로 복제 |
| 복제한 사본이 "View only", "Verification required" | 교육용 팀(prove) 초안이다. Starter 팀 초안으로 다시 복제한다 |
| 1994 포트를 다른 프로그램이 쓴다 | 그 프로그램을 끄거나, 브리지를 띄운 AI 도구를 하나만 남긴다 (여러 개면 하나가 대표로 연결한다) |

## 확인 기록

- [x] 원본 파일(`F4cs5…`): 보기 권한이라 플러그인 불가, Dev Mode는 유료 좌석 필요 (2026-11-09)
- [x] Starter 팀 초안 사본에서 연결: 플러그인 창 "WebSocket Connected", `list_files`에 `Kagom App (Copy)` (2026-11-09)
- [x] `get_metadata`: 페이지 3개 (Components `0:1`, Kagom App `5:1700`, Archive `152:4833`)
- [x] `get_node 5:1700`, `get_screenshot 6:1717`(SCR-01 Full step 1/3) 정상
