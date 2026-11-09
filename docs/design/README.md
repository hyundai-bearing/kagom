# 디자인 기준 자료

Figma "Kagom App" 내보내기와 개발 에이전트 작업 가이드를 **받은 날짜별로** 나눠 둔다.
zip 안 파일의 날짜는 내려받은 시각이라 버전 구분에 쓸 수 없다. 버전은 아래 표와 가이드 PDF 첫 장을 본다.

**구현 기준은 가장 최근 폴더(`2026-11-09`)다.** 이전 폴더는 비교·되돌아보기용.

| 폴더 | 파일 | 내용 |
|---|---|---|
| `2026-11-06/` | `Kagom_Agent_Handoff_Guide.pdf` | 작업 가이드 이전판 (PDF 생성 11-06 11:35) |
| | `Kagom_App_screens.zip` | 화면 PNG 118장 (Figma "Kagom App" 페이지) |
| | `Kagom_App_components.zip` | 컴포넌트 시트 PNG 13장 (Figma "Components" 페이지) |
| `2026-11-09/` | `Kagom_Agent_Handoff_Guide_v1.2.pdf` | 작업 가이드 **v1.2** (기준일 11-06, PDF 생성 11-06 17:54) |
| | `Kagom_App_screens.zip` | 화면 PNG 119장 |
| | `Kagom_App_components.zip` | 컴포넌트 시트 PNG 13장 |
| | `Kagom_App_archive_old_SCR-05_map.zip` | Figma "Archive" 페이지: 예전 SCR-05 강릉 단일 안개 지도 8장 + 안내 1장. **구현하지 않는다** |

## 2026-11-06 → 2026-11-09 달라진 점

가이드 v1.2 첫 장의 "리뷰 반영" 내용과 zip 비교 결과.

**화면 (`Kagom_App_screens.zip`)**: 같은 이름 109장 중 58장 그대로, 51장 바뀜, 10장 추가, 9장 빠짐

- SCR-05 지도: 강릉 단일 안개 지도(`Gangneung`, `popup=주문진항`, `tooltip=정동진`, `SCR-05 popup/*`)가 빠지고 3단계 레이어로 바뀜
  - 추가: `layer=1 전국`, `layer=2 강원`, `layer=3 강릉` (Full·Split), `layer=3 강릉/popup=주문진항`, `layer=3 강릉/popup=주문진 해수욕장(미방문)`
  - 빠진 예전 화면은 `Kagom_App_archive_old_SCR-05_map.zip`에 `OLD SCR-05 …`로 보관
- SCR-06 치장: 한 페이지를 넘을 때의 화면 추가 (`tab=head/grid=overflow`, Full·Split) → 페이지네이션, 원래대로/저장하기 버튼 오른쪽 정렬
- 바뀐 51장: SCR-02 이름 짓기 10장, SCR-03 홈 9장, SCR-06 치장 7장, POP-01~04 10장, `guide/*` 15장. 가이드 v1.2의 "캐릭터 상태 텍스트(대기/아픔 …) 제거, 치장 프리뷰 장착 칩 행 제거"와 맞물린다
- `guide/col-24` 빠짐

**컴포넌트 (`Kagom_App_components.zip`)**: 13장 중 `Kagom components.png` 1장만 바뀜. 지금 들어 있는 항목은 가이드 v1.2 3.1·부록 C 참고 (pagination, Map parts `mapLayer/L1~L3`, landmarkDetailPopup 등)

**가이드**: v1.2에서 SCR-05 3단계 지도 + 구획 상태 4종, 미방문 랜드마크 정보 공개, SCR-06 페이지네이션·버튼 우측 정렬, 캐릭터 상태 텍스트 제거가 반영됐다. 부록 A 노드 ID에 SCR-05 `layer=*`, SCR-06 `grid=overflow` 프레임이 추가됐다.

## 새 버전을 받으면

1. `docs/design/<받은 날짜>/` 폴더를 만들고 같은 이름 규칙으로 넣는다 (`Kagom_App_screens.zip`, `Kagom_App_components.zip`, 가이드는 `_v<버전>.pdf`).
2. 이 표와 "달라진 점"에 한 줄씩 추가한다.
3. `docs/IMPLEMENTATION.md` 첫 줄의 기준 자료 경로를 새 폴더로 바꾼다.
