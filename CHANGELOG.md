# 변경 내역

버전은 `app/build.gradle.kts`의 `versionName`을 따른다. GitHub Release 태그는 `v<versionName>`.
배포 PR에서 `versionCode`/`versionName`을 올리고, 아래 "다음 버전" 내용을 새 버전 제목 아래로 옮긴다.

## 다음 버전

- 새 레포로 옮기며 정리: README·이 문서·`THIRD_PARTY_NOTICES.md` 추가, `.gitattributes`로 줄바꿈 통일, 프로젝트 이름 `Kagom`
- release 서명: `kagom.signing.*` Gradle 속성이나 `KAGOM_SIGNING_*` 환경 변수가 있으면 정식 키, 없으면 디버그 키
- 렌더 원본(곰 모델·스킨 텍스처)을 `tools/closet3d/source/`에 넣음 (예전에는 git 기록에서 꺼내 씀)
- `docs/requirements.md`에 11/6 리빌드 때 빠진 기능 표시

## 2.0 (2026-11-09, bearing-test에서 옮김)

시험 레포 `hyundai-bearing/bearing-test` `main` df0333c 기준. 아래 PR 번호는 **bearing-test의 번호**다.

**Figma 디자인으로 다시 만듦 (11/6)**
- 카곰 앱을 Figma "Kagom App"과 작업 가이드로 다시 만듦: 온보딩, 홈, 도전과제, 지도, 치장, 보상 팝업 (#2)
- PR·배포 가이드, PR 템플릿 (#1)

**가이드 v1.2 반영 (11/9)**
- 랜드마크 지도를 전국 → 시·도 → 시·군·구 3단계로 개편 (#9)
- 치장 목록 페이지 넘김, 버튼 오른쪽 정렬 (#8)
- 캐릭터 아래 상태 글자 제거 (#7)

**캐릭터·연출**
- 탭 반응을 부드럽게, 숨쉬기·눈 깜빡임 추가 (#6)
- 옷이 몸을 뚫던 문제 수정, 위아래로도 돌려보기 (#5)
- 보상 모달 애니메이션 (#4)

**버그 수정**
- 치장 썸네일에 다른 아이템 이미지가 보이던 문제 (#3)

**문서**
- AI 코드 작성 규칙을 `CLAUDE.md`에 추가하고 `AGENTS.md`로 연결 (#10), 둘 다 한국어로 (#11)

## 그 전 (2026-11-05까지, bearing-test)

- 3D 곰 아바타 데모(RoroAvatarDemo)에서 시작해 요구사항 필수 기능(FR 필수 30) 구현. 11/6 리빌드로 화면 구성이 바뀌었고, 빠진 기능은 `docs/requirements.md` 맨 위에 있다.
