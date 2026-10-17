# 야요 WebGUI fork 관리

이 fork의 `main`은 업스트림 WebGUI에 Paper 서버 지원과 야요 개발용 변경을 통합한 브랜치다.

- `origin`: `https://github.com/mc-webgui/webgui.git` — 업스트림을 가져오는 원격
- `fork`: `git@github.com:yayokorea/webgui.git` — 통합한 코드를 올리는 원격
- 로컬 `main`의 추적 대상: `fork/main`
- `feat/paper-server`: Paper 지원을 개발했던 기존 브랜치. 2.0.0 기반 작업은 `main`에 병합되어 있다.
- `archive/paper-server-on-1.8.1` 태그: rebase 전 Paper 작업 보관본

## 업스트림 반영

작업 트리가 깨끗한 상태에서 실행한다. 공유한 `main`은 rebase하거나 강제 push하지 않는다.

```bash
git switch main
git fetch origin
git fetch fork
git merge --ff-only fork/main
git merge origin/main
# 충돌이 있으면 해결하고, 변경 범위에 맞게 빌드와 테스트를 실행한다.
git push fork main
```

## 새 작업

통합된 `main`에서 작업 브랜치를 만들고, 변경을 커밋한 뒤 `main`으로 병합한다.

```bash
git switch main
git pull --ff-only fork main
git switch -c feat/my-change
# 작업 후 필요한 파일만 git add하고 커밋한다.
git switch main
git merge --no-ff feat/my-change
git push fork main
```

Paper 또는 `common/` 변경은 `CONTRIBUTING.md`에 따라 `./gradlew :paper:build`로 검증한다.
클라이언트 모드도 변경했다면 해당 Minecraft 대상의 빌드와 테스트를 함께 실행한다.
