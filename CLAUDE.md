# CLAUDE.md

## 소통 규칙

- **사용자에게 하는 모든 답변은 한국어로 작성한다.** 짧은 상태 보고, CI 결과, 자동 알림 후 답변도 예외 없다.
- PR 제목·본문, 커밋 메시지 요약, 코드 주석도 한국어를 기본으로 한다. (클래스·함수명 등 식별자는 영어)

## 프로젝트

**딱(ttaak)**: iOS 앱 툭(Took)을 오마주한 안드로이드 투두 앱. 인트라넷(폐쇄망) 폰의 잠금화면에서 오늘 할 일을 보고 체크하는 것이 핵심이다.

- Kotlin + Jetpack Compose, minSdk 26 / targetSdk 35
- **INTERNET 권한을 추가하지 않는다.** 데이터는 앱 내부 JSON 파일(`TaskStore`)에만 저장한다.
- 잠금화면 카드는 공개(PUBLIC) + 고정(ongoing) 알림의 커스텀 RemoteViews다 (`lock/LockCard.kt`).
- 앱을 고칠 때는 `app/build.gradle.kts`의 `versionCode`/`versionName`을 올려 기존 설치 위에 덮어 설치되게 한다.

## 빌드·검증

```bash
./gradlew testDebugUnitTest lintDebug assembleRelease
```

- 단위 테스트에는 Robolectric 렌더링/실행 테스트가 포함된다 (잠금화면 카드·위젯 inflate, 화면 실행).
- `TTAAK_SHOTS=<디렉터리> ./gradlew testDebugUnitTest --tests '*LaunchTest*'` 로 화면 스크린샷을 남길 수 있다.
