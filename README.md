# Lay-Z-Spa Miami – Android

Kompletny projekt Android/Gradle przeznaczony do budowania bez Android Studio:
**GitHub → Codemagic → APK**.

## Struktura

- `app/` – moduł aplikacji
- `app/src/main/java/pl/bartek/layzspa/MainActivity.java` – kod aplikacji
- `app/src/main/res/values/styles.xml` – motyw
- `app/src/main/AndroidManifest.xml` – manifest i uprawnienia
- `build.gradle` – konfiguracja główna
- `settings.gradle` – konfiguracja projektu
- `gradle.properties` – ustawienia Gradle/AndroidX
- `codemagic.yaml` – workflow Codemagic

## Codemagic

Workflow `android-debug` używa Gradle dostępnego w środowisku Codemagic i wykonuje:

`gradle --no-daemon --stacktrace assembleDebug`

Gotowy APK:

`app/build/outputs/apk/debug/app-debug.apk`

## Połączenie z ESP8266

Domyślny adres: `192.168.1.40`

WebSocket: `ws://192.168.1.40:81/`
