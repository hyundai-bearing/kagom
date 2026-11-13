# 랜드마크 두 곳(안반데기·대관령 옛길) 확인 (2026-11-13)

Pleos Connect v2.1.0 에뮬레이터(2560×1440), 디버그 빌드. 캡처는 앱 창만 잘랐다 (Full 1682×1210, Split 슬롯 841×1210).
시연 훅 `--es demo_visit LM-GN-04`, `LM-GN-05`로 방문을 만들었다 (확인 뒤 에뮬레이터 저장 데이터는 원래대로 되돌렸다).

| 파일 | 본 것 |
|---|---|
| `full-popup-anbandegi.jpg`, `full-popup-daegwallyeong.jpg` | 해금 팝업: 아이콘(풍력발전기·숲길), 방문 도장, 보상 썸네일, "강릉 랜드마크 n / 5 방문" |
| `full-home-worn.jpg` | 홈: 바람개비 모자 + 옛길 배낭(앞에서는 어깨끈·가슴끈)을 멜빵 반바지 위에 입은 모습. 기존 렌더와 어긋나지 않는다 |
| `full-home-turned.jpg` | 홈에서 곰을 돌린 모습: 등에 멘 배낭과 돗자리 |
| `full-closet-head.jpg` | 옷장 머리 탭: 바람개비 모자 카드와 썸네일 |
| `full-map.jpg`, `full-map-gangneung.jpg` | 지도: 전국 "해금 4 / 5", 강릉시 구획 지도에 성산면(대관령 옛길)·왕산면(안반데기) 핀과 구획 색 |
| `full-detail-anbandegi.jpg`, `full-detail-daegwallyeong.jpg` | 랜드마크 상세: 새 풍경(고랭지 언덕과 풍력발전기, 전나무 숲길) |
| `split-closet.jpg`, `split-map.jpg` | Split 슬롯: 옷장, 전국 지도 |

Blender 렌더: `RIG dist=2.424`(기존과 같음), `FRAME ITM-H-03 worst=0.940`, `FRAME ITM-T-03 worst=0.893`. 새 파일 640장(아이템 레이어 2, 썸네일 2, 돌려보기 288, 동작 348), 기존 파일은 바뀌지 않았다.

못 본 것: Split에서 강릉시 구획 지도와 랜드마크 상세, 터치 반응 동작(손 흔들기 등)에서 새 옷이 따라 움직이는 모습(프레임은 만들어졌지만 앱에서 재생해 보지는 않았다), 주행 중(기어 P 아님)에 새 랜드마크에 도착했을 때의 토스트, 다른 스킨·다른 옷과의 조합, 세로 폰 에뮬레이터.
