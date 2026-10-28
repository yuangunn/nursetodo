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
- 딱 화면(`QuickAddActivity`)은 툭 잠금화면 모양: 테마 `windowShowWallpaper`(불투명, 배경화면이 비침 → 키가드를 가림) + 시계 + 유리 카드(검정 32%) + `＋ 할 일 추가` 알약, 위로 밀어 닫기. `EXTRA_LOOK_FIRST`면 입력칸을 접은 채로(화면 켜짐·카드 탭), 아니면 바로 입력(타일·위젯 `+`).
- 딱 화면 줄: 동그라미만 체크(350ms 체크 표시 후 toggle), 글씨 탭 = `requestDismissKeyguard` 후 MainActivity, 길게 = 제자리 이름 고치기. 고치기 저장 뒤 `clearFocus()` 필수 — 안 하면 하드웨어 엔터의 남은 키 입력이 옆 줄 동그라미로 넘어가 체크됨(실기기 adb 로 재현).
- '몇 분 뒤' 알람(`lock/Alarms.kt`): `TaskStore.alarms`(JSON "alarms") + `setExactAndAllowWhileIdle`(USE_EXACT_ALARM, 12 이하는 SCHEDULE_EXACT_ALARM) — 사용자 요청으로 상태바 ⏰ 없게 setAlarmClock 은 안 씀. 울리면 목록에서 빼고 `AlarmRingService`(mediaPlayback FGS)가 `AlarmSound` 로 직접 재생(USAGE_ALARM, 울리는 동안 STREAM_ALARM 을 설정 크기로 맞췄다 되돌림) + 진동, 조용한 채널 `alarm_ring` 알림의 fullScreenIntent 로 `AlarmActivity`. 10분 뒤 자동 정지. 채널 소리로는 알람음·크기를 못 바꿔서 이렇게 함. 부팅·업데이트 땐 `refreshSurfaces` → `Alarms.scheduleAll`. 울리는 중(`AlarmRingService.ringing`)엔 `LockPageService` 가 딱 화면을 띄우지 않음 — 채널로 판단하면 시스템 자동 묶음 요약(같은 채널)에 걸려 딱 화면이 영영 안 뜸(실기기 버그).
- A안(딱 화면) 확정(v1.3): `lockPage` 기본 true. `LockCard.pageMode`(lockPage && 오버레이 권한)면 카드·라이브 대신 조용한 채널 `lock_page` 알림만(상시 서비스용). 안드로이드가 FGS 알림을 MIN→LOW 로 올려 상태바 ✓ 가 남음 → 사용자가 채널을 끄면 숨겨지고 서비스는 계속(설정의 '상태바의 딱 아이콘 숨기기'). 오버레이 설정은 안드로이드 11+ 에서 목록 화면으로 열림.
- 글자·줄 간격: `Settings.cardSp`(sp, 1.3.0 까지의 순번 `cardTextSize` 는 읽을 때 16/19/23 으로 옮김), `rowGap`(딱 화면 줄 글자 위아래 여백 dp, 동그라미 누름 칸 높이도 `circleBoxHeight` 로 같이 줄어듦).
- 다른 앱 알림: `lock/NotiListener`(NotificationListenerService, 사용자가 알림 접근 허용). `notis` StateFlow 를 딱 화면이 구독하고 `Settings.notiApps`(패키지 이름, 비면 모든 앱)로 거름. 누르면 `requestDismissKeyguard` 뒤 contentIntent 를 BAL 허용 옵션(API 34+)으로 보냄. APK 설치본은 '제한된 설정'(packageSource=3 LOCAL_FILE)이라 앱 정보 ⋮ 에서 풀어야 함 — 에뮬레이터에서 파일 앱 설치로 재현·확인.
- 딱 화면 바로가기: `Settings.shortcuts`(최대 9 저장, id = calc | alarm | add | "app:패키지/액티비티", name 비면 앱 이름). 보이는 칸은 `shortcutStyle`(0 이름 3줄 9, 1 이름 2줄 6, 2 아이콘 4×2 8, `shortcutSlots()`). 할 일 추가 버튼은 한 군데만: 보이는 바로가기에 add 가 있으면 그것, 아니면 `addButton`(0 카드 아래 알약, 1 카드 안 마지막 줄 오른쪽 메모 버튼, 적는 동안 숨김). 매니페스트 `<queries>` 로 런처 앱 목록. 앱 열기는 `requestDismissKeyguard` 후.
- 끝낸 일은 잠금 카드·라이브·위젯·딱 화면에서 빼고, 앱 `TaskList` 맨 아래 접히는 '완료된 할 일'에만. `Task.doneAt`(ms) 저장, 만든 시각은 `createdAt = id`(만든 순간 ms).
- 툭처럼 보이는 잠금화면은 알림으로는 안 된다(헤더 강제, 라이브 알림은 커스텀 거부). `LockPageService`(specialUse 포그라운드 서비스, SCREEN_ON 수신) + SYSTEM_ALERT_WINDOW 로 화면이 켜질 때 `QuickAddActivity`(showWhenLocked)를 띄운다. 서비스 알림 = 잠금화면 카드(같은 ID 1).
- 삼성 실기기 검증은 Samsung Remote Test Lab + rdb(크롬에서만 연결됨)로 한다. `locksettings` 같은 명령은 막혀서 연결이 끊기고, 화면 잠금(PIN)은 설정할 수 없다.
- 계산기(`data/Infusion.kt`)의 priming은 별도 수액으로 채운 용량이라 총량에 **더한다**(사용자 확인 2026-10-25). 용량 입력값은 저장하지 않는다(공용 폰 안전).

## 빌드·검증

```bash
./gradlew testDebugUnitTest lintDebug assembleRelease
```

- 단위 테스트에는 Robolectric 렌더링/실행 테스트가 포함된다 (잠금화면 카드·위젯 inflate, 화면 실행).
- `TTAAK_SHOTS=<디렉터리> ./gradlew testDebugUnitTest --tests '*LaunchTest*'` 로 화면 스크린샷을 남길 수 있다.
