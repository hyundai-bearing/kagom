# 렌더 원본

`tools/closet3d/build_closet.py`가 읽는 곰 모델과 스킨 텍스처. 앱(APK)에는 들어가지 않고, 캐릭터·치장 에셋(`app/src/main/assets/kagom`)을 다시 만들 때만 쓴다. 명령은 `build_closet.py` 머리말에 있다.

| 파일 | 내용 |
|---|---|
| `pebble_bear.glb` | 리깅된 곰 모델 |
| `skins/<스킨>.png` | 스킨 8종의 base color 텍스처 (`tools/bear3d/recolor_skins.ps1`이 민트 텍스처에서 색을 바꿔 만든 것) |

시험 레포 bearing-test 934bcd4의 `app/src/main/assets/models/pebble_bear.glb`, `app/src/main/assets/skins/*.png`와 같은 파일이다.
원본을 바꾸면 에셋도 다시 만들어 같은 PR에 넣는다.
