# 글자 크기를 Pleos Connect 글자 단계로 (2026-11-10)

`KagomType` 값을 Pleos Connect 공식 글자 단계로 올린 변경의 확인 기록. 대응 표와 달라진 치수는 `docs/IMPLEMENTATION.md`의 "글자 크기 = Pleos Connect 글자 단계".

- 기준: Pleos Connect Developer Guide > Designing the UI > Typography (`document.pleos.ai`). Figma "Kagom App" 브리지는 이 작업 때 연결돼 있지 않아 Figma 화면과는 견주지 않았다 (글자를 일부러 Figma보다 키운 변경이다).
- Full: Pleos Connect v2.1.0 에뮬레이터, 앱 창 1682×1210 (`full-*.jpg`, 0.6배).
- Split: 폰 에뮬레이터 세로 1080×2400 (`split-*.jpg`, 0.4배). Pleos 창을 반으로 나눈 Split(841×1210)에서는 보지 않았다.
- Mock 신호(`--es source mock`), 팝업은 `--es demo_popup …`, 주행 중은 `--es demo_gear D`, 새 차는 `--ez demo_new_vehicle true`.

## 전 / 후 (Full 홈)

| 전 (Figma 크기 그대로) | 후 (Pleos 글자 단계) |
|---|---|
| ![전](full-home-before.jpg) | ![후](full-home.jpg) |

전 화면은 보상 팝업이 떠 있을 때 찍었다. 오른쪽 차량 상태 패널과 헤더 글자를 견주면 된다.

## Full

| 화면 | |
|---|---|
| 홈 (주행 중, 기어 D) | ![](full-home-driving.jpg) |
| 도전과제 | ![](full-quests.jpg) |
| 치장 (예시 데이터, 4×3) | ![](full-closet.jpg) |
| 치장 획득 안내 패널 | ![](full-closet-panel.jpg) |
| 지도 핀 상세 | ![](full-map-pin.jpg) |
| POP-03 랜드마크 해금 | ![](full-pop-landmark.jpg) |
| POP-05 정비 시트 | ![](full-pop-maintenance.jpg) |
| SCR-00 Carry-over | ![](full-carry-over.jpg) |
| SCR-02 이름 짓기 | ![](full-naming.jpg) |

## Split

| 홈 | 도전과제 | 치장 시트 | 지도 핀 상세 |
|---|---|---|---|
| ![](split-home.jpg) | ![](split-quests.jpg) | ![](split-closet-sheet.jpg) | ![](split-map-pin.jpg) |

| POP-03 | POP-05 | SCR-00 Carry-over | SCR-01 성향 테스트 |
|---|---|---|---|
| ![](split-pop-landmark.jpg) | ![](split-pop-maintenance.jpg) | ![](split-carry-over.jpg) | ![](split-personality.jpg) |

## 본 것

- 모든 화면에서 글자가 잘리거나 겹치지 않는다: 홈, 도전과제, 지도(전국·구획·핀 상세), 치장(그리드·획득 안내), POP-02 언박싱·공개, POP-03, POP-04, POP-05, SCR-00 Carry-over, SCR-01, SCR-02.
- 주행 중(기어 D): 홈 상태 행이 옅어지고 정비 시트 버튼이 꺼진다 (동작은 바꾸지 않았다).
- 못 본 것: 실차 화면, Pleos Split(841×1210), 1280×720 축소 화면, 시연 패널, 이름 10자·EXP 네 자리 같은 긴 값.
