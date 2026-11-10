# 화면 전환·UX 보강 확인 화면 (PR #8, 2026-11-10)

`feature/ux-polish` 빌드를 에뮬레이터에서 찍은 화면이다. 무엇이 어느 그림인지는 PR #8 본문의 "화면"에 적었다.

- `full-*.jpg`: Pleos Connect 에뮬레이터 (가로, 앱 창만 잘라 0.6배)
- `split-*.jpg`: 폰 에뮬레이터 세로 (0.45배)
- 이름에 `-in`, `-out`, `-move`, `fog`가 붙은 것은 움직이는 도중이다. 애니메이션 배속을 5~8배 느리게 하고 찍었다 (`adb shell settings put global animator_duration_scale 8`).
- `split-header-before.jpg`만 고치기 전 빌드다 (이름 10자일 때 EXP가 잘리던 모습).
- 화면의 프로필·수치는 시험 데이터다.
