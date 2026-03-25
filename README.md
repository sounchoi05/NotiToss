# NotifyToss / 노티토스

안드로이드 네이티브 앱 프로젝트입니다.

NotifyToss는 앱 알림, SMS, MMS 수신 내용을 규칙에 따라 다른 채널로 전달합니다.

## 주요 기능

- 알림내역: 수신된 앱별 알림 목록 확인, 알림 기반 규칙 생성
- 전달규칙: 설치 앱 다중 선택, 검색어 설정, 문자/웹훅/소리 알림 다중 전달
- 전달내역: 성공/실패 상태 확인, 재전송, 다시 전달, 삭제
- 앱설정: 규칙 백업/복원, 데이터 백업/복원, 캐시 삭제, 전체 데이터 삭제

## 전달 방식

- 문자 전송
- 웹훅 전송
- 소리 알림

웹훅과 SMS 전송은 일시적인 오류에 대비해 자동 재시도를 수행합니다.
Discord 웹훅은 전용 payload 형식으로 자동 처리되어 테스트 전송과 실제 전달 모두 바로 사용할 수 있습니다.

## 기술 스택

- Kotlin
- Jetpack Compose
- Room
- Notification Listener Service
- SMS BroadcastReceiver
- MMS ContentObserver

## 권한 안내

앱 사용을 위해 아래 권한/설정이 필요합니다.

- 알림 접근 권한 허용
- SMS 수신/읽기/전송 권한 허용
- 재부팅 후 자동 시작을 위한 시스템 브로드캐스트 허용
- 장시간 백그라운드 유지를 위한 배터리 최적화 제외 권장

MMS 감시는 기기 제조사 정책과 기본 문자 앱 동작에 따라 차이가 있을 수 있습니다.

## 빌드

아래 명령으로 디버그 APK를 생성할 수 있습니다.

```powershell
.\gradlew.bat assembleDebug
```

설치된 도구 경로:

- Java: `C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot`
- Gradle: `C:\DEV\tools\gradle\gradle-8.7`

생성된 APK:

- `app\build\outputs\apk\debug\app-debug.apk`

## 패키지명

- `com.ckchoi.notitoss`
