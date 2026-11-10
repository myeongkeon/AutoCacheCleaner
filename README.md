# Cache Wipe (캐시 와이프)

[한국어](#한국어) · [English](#english)

---

## 한국어

설치된 모든 앱의 **캐시를 한 번에** 지우거나, **고른 앱만** 지우는 안드로이드 앱입니다. 설정 > 앱 > 저장공간의 [캐시 삭제] 버튼을 앱마다 대신 눌러 줍니다.

- 광고 없음 · 결제 없음 · **인터넷 권한 없음** (아래 [개인정보](#개인정보) 참고)
- 앱 데이터(로그인·사진·설정)는 지우지 않고 캐시만 지웁니다
- 정리 전 앱별 캐시 크기, 정리 후 확보한 용량을 보여줍니다
- 선택한 앱 목록은 저장되어 다음에도 원탭, 정기 자동 정리, 빠른 설정 타일, 위젯 지원
- 선택: [Shizuku](https://shizuku.rikka.app/)를 쓰면 시스템 앱 캐시까지 몇 초 만에 정리 (고속 모드)

### 지원 현황
| 기기 | 상태 |
|---|---|
| 삼성 갤럭시 (One UI) | ✅ 갤럭시 S26 울트라(One UI 9)에서 160개 앱 전체 정리 160/160 성공 확인. 다른 기종은 제보를 기다립니다 |
| 순정 안드로이드 (Pixel 등) | ❌ 아직 동작하지 않습니다 (Android 17 에뮬레이터에서 [캐시 삭제] 버튼을 찾지 못함) |
| 기타 제조사 | ❓ 미확인 — 시도해 보시고 [이슈](../../issues)로 알려 주세요 |

요구사항: Android 10 이상.

### 설치
1. [Releases](../../releases)에서 최신 `CacheWipe-*.apk`를 받습니다.
2. 파일을 열어 설치합니다. "출처를 알 수 없는 앱" 허용이 필요합니다. 삼성은 **설정 > 보안 및 개인정보 보호 > Auto Blocker**가 켜져 있으면 설치가 막히니 잠시 꺼 주세요.
3. 앱을 열고 안내를 읽은 뒤 **사용정보 접근**과 **접근성 서비스**를 허용합니다.
   - Android 13 이상에서 접근성 항목이 회색이면: 설정 > 앱 > 캐시 와이프 > 우상단 ⋮ > **제한된 설정 허용** 후 다시 시도합니다.
4. 홈 화면에서 [전체 캐시 삭제] 또는 [선택 캐시 삭제]를 누릅니다. 도중에 화면을 끄거나 잠그면 멈춥니다(의도된 동작).

### 개인정보
- 앱에는 **인터넷 권한이 없습니다.** 어떤 데이터도 기기 밖으로 나갈 수 없습니다. (확인: `aapt dump permissions`)
- 접근성 서비스는 **설정 앱(com.android.settings)만** 감시하며, 화면 내용을 저장하지 않습니다.
- 자세한 내용: [PRIVACY.md](PRIVACY.md)

### 문제가 생겼을 때
앱 홈 화면 맨 아래 **[문제 신고용 진단 정보 보내기]**를 누르면 기기 정보와 최근 로그가 텍스트로 만들어집니다. 내용을 확인하고(앱 패키지명이 들어 있습니다) [이슈](../../issues/new/choose)에 붙여 넣어 주세요.

### APK 확인
릴리스에 `SHA256SUMS.txt`가 함께 올라갑니다. 서명 인증서 SHA-256은 항상 아래 값입니다. 다르면 설치하지 마세요.
```
d4dbe5e69835e12e019f1038dae433196274103f234d320bcb82182f3e227802
```

### 직접 빌드
```bash
cd android
./gradlew testDebugUnitTest assembleOpen   # 서명 설정이 없으면 unsigned APK
```
JDK 17+ (Android Studio 내장 JDK 가능)와 Android SDK가 필요합니다. 서명하려면 `~/.gradle/gradle.properties`에 `cachewipe.signing.storeFile / storePassword / keyAlias / keyPassword`를 넣으세요.

`assembleDebug`/`assembleRelease`는 광고·결제가 포함된 스토어용 구성입니다(공개 배포에는 쓰지 않음).

### 기여
이슈와 PR 환영합니다. 특히 **다른 기종에서의 동작 제보**가 가장 도움이 됩니다. [CONTRIBUTING.md](CONTRIBUTING.md) 참고.

### 사용 설명 영상
사용법 영상 매뉴얼은 유튜브 멤버십 전용으로 제공됩니다. 앱 자체는 영상과 관계없이 누구나 무료로 받아 쓸 수 있습니다.

### 라이선스
[LICENSE](LICENSE) 참고. 제3자 구성요소: Shizuku API (Apache-2.0), AndroidX·Jetpack Compose (Apache-2.0), Google Play Services Ads·Billing·UMP (스토어용 구성에만 포함, 공개 빌드에서는 동작하지 않음).

---

## English

Clear the cache of **all apps at once**, or only the ones you pick. It taps the [Clear cache] button in Settings > Apps > Storage for every app, for you.

- No ads, no purchases, **no INTERNET permission**
- Only cache is cleared; logins, photos and settings stay
- Shows each app's cache size before and the space freed after
- Remembers your selection, scheduled auto-clean, Quick Settings tile, widget
- Optional: with [Shizuku](https://shizuku.rikka.app/), system-app caches are cleared in seconds (fast mode)

### Compatibility
| Device | Status |
|---|---|
| Samsung Galaxy (One UI) | ✅ Verified on a Galaxy S26 Ultra (One UI 9): 160/160 apps cleared. Other models: reports welcome |
| Stock Android (Pixel etc.) | ❌ Not working yet (the [Clear cache] button is not exposed to accessibility on the Android 17 emulator) |
| Other vendors | ❓ Unknown — please try and open an [issue](../../issues) |

Requires Android 10+.

### Install
1. Download the latest `CacheWipe-*.apk` from [Releases](../../releases).
2. Open it and allow "install unknown apps". On Samsung, turn off **Auto Blocker** temporarily.
3. Open the app, read the notice, then grant **Usage access** and the **Accessibility service**. If the accessibility entry is greyed out on Android 13+: App info > ⋮ > **Allow restricted settings**.
4. Tap [Clear all cache] or [Clear selected cache]. Turning the screen off or locking the phone stops the run (by design).

### Privacy
The app has **no INTERNET permission**, so nothing can leave your device. The accessibility service only watches the Settings app and stores nothing. See [PRIVACY.md](PRIVACY.md).

### Something broke?
Tap **"Copy support info"** at the bottom of the home screen: it builds a text with your device info and the app's recent log. Review it (it contains package names of apps) and paste it into a [new issue](../../issues/new/choose).

### Verify the APK
Signing certificate SHA-256 (must always match): `d4dbe5e69835e12e019f1038dae433196274103f234d320bcb82182f3e227802`. Releases include `SHA256SUMS.txt`.

### Build
```bash
cd android && ./gradlew testDebugUnitTest assembleOpen
```

See [LICENSE](LICENSE) and [CONTRIBUTING.md](CONTRIBUTING.md).
