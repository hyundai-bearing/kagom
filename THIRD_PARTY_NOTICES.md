# 외부 자료와 라이선스

카곰이 쓰는 외부 라이브러리·폰트·데이터와 그 조건. 새로 넣거나 버전을 바꾸면 이 표도 고친다.

## 앱에 들어가는 것

| 이름 | 쓰는 곳 | 라이선스 | 지킬 것 |
|---|---|---|---|
| Asta Sans (© 2024 The Asta Sans Project Authors, [42dot/Asta-Sans](https://github.com/42dot/Asta-Sans)) | `app/src/main/res/font/asta_sans.ttf` | SIL Open Font License 1.1 | 라이선스 전문을 같이 배포한다 (`app/src/main/assets/licenses/AstaSans-OFL.txt`, APK에 들어감). 폰트만 따로 팔지 않는다 |
| OpenStreetMap 데이터 (© OpenStreetMap contributors) | 전국·시·도·시·군 경계 `app/src/main/assets/regions/korea_map.txt`, 랜드마크 좌표 (`tools/regions/`가 Overpass·Nominatim에서 받음) | [ODbL 1.0](https://opendatacommons.org/licenses/odbl/1-0/) | 지도 화면 오른쪽 아래 "© OpenStreetMap contributors" 표시를 지우지 않는다 (`MapScreen.kt`). 가공한 지도 데이터를 외부에 배포할 때는 ODbL의 출처 표시·동일 조건 공개 의무를 확인한다 |
| 행정동 경계 (원본: [통계청 통계지리정보서비스(SGIS)](https://sgis.kostat.go.kr), 가공: [vuski/admdongkor](https://github.com/vuski/admdongkor) 2026-10-01판) | 강릉 구획 경계 `app/src/main/assets/regions/gangneung_districts.txt` (`tools/regions/build_gangneung.py`로 추출·단순화) | 원본 공공누리 제1유형(출처 표시), 가공 데이터 [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) | 구획 지도 오른쪽 아래 "경계 © 통계청 SGIS · vuski/admdongkor" 표시를 지우지 않는다 (`MapScreen.kt`). 다른 지역으로 넓힐 때도 같은 출처를 적는다 |
| AndroidX Core, Activity, Jetpack Compose (UI, Foundation, Material3) | 앱 전체 | Apache License 2.0 | — |
| Pleos Playground SDK (`ai.pleos.playground:Vehicle` 2.3.2, `FusedLocation` 1.1.3) | `data/` | Pleos 개발자 이용 조건 | 레포에 넣지 않고 빌드 때 Pleos 저장소에서 받는다. `PRIVATE_*` API는 값 제공과 앱마켓 승인이 보장되지 않는다 |

## 빌드·테스트에만 쓰는 것

| 이름 | 쓰는 곳 | 라이선스 |
|---|---|---|
| Android 차량 라이브러리 (`android.car`) | `useLibrary("android.car")` (CarUxRestrictionsManager). 기기 시스템 라이브러리라 APK에 들어가지 않는다 | Android SDK 이용 약관 |
| JUnit 4.13.2 | 단위 테스트 | Eclipse Public License 1.0 |
| Blender 5.x | `tools/closet3d`, `tools/bear3d` 렌더 (레포에 들어 있지 않음) | GPL (도구로만 쓰고 결과물에는 영향 없음) |

## 확인이 필요한 것

- 치장 팝업 진열대 배경 (`app/src/main/res/drawable-nodpi/closet_display_stage.png`): 팀 디자인으로 전달받은 이미지이며, 외부 공개 전 출처와 이용 조건을 확인한다.
- **곰 3D 모델과 스킨 텍스처** (`tools/closet3d/source/`)와 거기서 렌더한 캐릭터 이미지(`app/src/main/assets/kagom/**`): 출처와 이용 조건을 아직 이 문서에 적지 않았다. 외부에 공개하기 전에 확인해서 적는다.
- **기획·디자인 자료** (`docs/spec.pdf`, `docs/design/**`): 팀이 받은 자료다. 레포를 공개로 바꾸기 전에 공개해도 되는지 확인한다.

## 랜드마크 사진

Wikimedia Commons에서 가져온 장소 참고 사진이다. 앱에서는 원본의 크기를 줄이고 WebP로 변환했다. 썸네일은 중앙을 잘라 사용한다. 가공한 사진과 썸네일에 각 원본의 라이선스를 동일하게 적용한다.

| 사진 | 원본 | 저작자 | 라이선스 |
|---|---|---|---|
| 정동진 해돋이 | [Jeongdongjin Beach.jpg](https://commons.wikimedia.org/wiki/File:Jeongdongjin_Beach.jpg) | Loewelad | [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/) |
| 정동진역 옛 역사 | [Jeongdongjin Station old building 20230430.jpg](https://commons.wikimedia.org/wiki/File:Jeongdongjin_Station_old_building_20230430.jpg) | Rsa | [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/) |
| 정동진역과 열차 | [Jeongdongjin station and train.jpg](https://commons.wikimedia.org/wiki/File:Jeongdongjin_station_and_train.jpg) | Smiley.toerist | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/) |
| 커피거리 입구 | [Gangneung coffee street entrance.jpg](https://commons.wikimedia.org/wiki/File:Gangneung_coffee_street_entrance.jpg) | Altostratus | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/) |
| 강릉 커피거리 | [Gangneung coffee street.jpg](https://commons.wikimedia.org/wiki/File:Gangneung_coffee_street.jpg) | Altostratus | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/) |
| 안목해변 | [Anmok Beach 20220430 005.jpg](https://commons.wikimedia.org/wiki/File:Anmok_Beach_20220430_005.jpg) | Mobius6 | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/) |
| 주문진항 풍경 | [Jumunjin Harbor 20220501 001.jpg](https://commons.wikimedia.org/wiki/File:Jumunjin_Harbor_20220501_001.jpg) | Mobius6 | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/) |
| 주문진항의 배 | [Jumunjin Harbor 20220501 003.jpg](https://commons.wikimedia.org/wiki/File:Jumunjin_Harbor_20220501_003.jpg) | Mobius6 | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/) |
| 주문진항 바닷가 | [Jumunjin Harbor 20220501 006.jpg](https://commons.wikimedia.org/wiki/File:Jumunjin_Harbor_20220501_006.jpg) | Mobius6 | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/) |
