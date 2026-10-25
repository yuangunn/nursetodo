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
- 잠금화면 카드 안 버튼에는 **화면을 여는 PendingIntent를 걸지 않는다.** 시스템이 무조건 PIN부터 요구한다. 잠금 위에서 열어야 하는 화면은 카드 전체 탭(contentIntent) + `showWhenLocked` 액티비티로 연다. 체크처럼 화면이 없는 동작은 브로드캐스트로 한다.
- 삼성 One UI 잠금화면 알림 스타일은 `Settings.System` 의 `lockscreen_minimizing_notification`(0 카드)이다. 앱은 읽을 수만 있고 쓰기는 안드로이드가 막는다(실기기 확인). 설정 화면은 `com.android.settings/.Settings$LockscreenNotificationActivity` 로 바로 연다.
- 라이브 알림 모드(`lockLive`)는 커스텀 RemoteViews 없이 BigTextStyle + `android.requestPromotedOngoing` 로 만든다. 커스텀 화면을 넣으면 시스템이 promoted 로 올려 주지 않는다. One UI 9 에서 `PROMOTED_ONGOING` 으로 올라가고 잠금화면에 늘 펼쳐 보임(실기기 확인).
- 삼성 Now Bar '실시간 정보'는 화이트리스트다. One UI 8.5(SM-A576S)에서 딱은 PROMOTED_ONGOING 인데도 목록에 없음. 앱별 토글은 `Settings.System` `key_now_bar_<패키지 . → _>`, 화면은 액션 `com.samsung.settings.NowBarLiveNotificationSettings`(aodservice). 사용자에게 Now Bar 켜라고 안내하지 말 것.
- 삼성은 라이브 알림도 `lockscreen_minimizing_notification`=0(카드)이어야 잠금화면에 보인다. 삼성 앱 알림 설정에는 라이브 토글이 없고 `APP_NOTIFICATION_PROMOTION_SETTINGS`는 앱 알림 설정으로 열린다(One UI 8.5).
- 툭처럼 보이는 잠금화면은 알림으로는 안 된다(헤더 강제, 라이브 알림은 커스텀 거부). `LockPageService`(specialUse 포그라운드 서비스, SCREEN_ON 수신) + SYSTEM_ALERT_WINDOW 로 화면이 켜질 때 `QuickAddActivity`(showWhenLocked)를 띄운다. 서비스 알림 = 잠금화면 카드(같은 ID 1).
- 삼성 실기기 검증은 Samsung Remote Test Lab + rdb(크롬에서만 연결됨)로 한다. `locksettings` 같은 명령은 막혀서 연결이 끊기고, 화면 잠금(PIN)은 설정할 수 없다.
- 계산기(`data/Infusion.kt`)의 priming은 별도 수액으로 채운 용량이라 총량에 **더한다**(사용자 확인 2026-10-25). 용량 입력값은 저장하지 않는다(공용 폰 안전).

## 빌드·검증

```bash
./gradlew testDebugUnitTest lintDebug assembleRelease
```

- 단위 테스트에는 Robolectric 렌더링/실행 테스트가 포함된다 (잠금화면 카드·위젯 inflate, 화면 실행).
- `TTAAK_SHOTS=<디렉터리> ./gradlew testDebugUnitTest --tests '*LaunchTest*'` 로 화면 스크린샷을 남길 수 있다.
