# Smart Spa Showroom

Pokazowa aplikacja Android dla modelu MIAMI2021.

## Cechy

- działa całkowicie lokalnie
- nie łączy się z jacuzzi, Wi-Fi, MQTT ani serwerem
- demonstracyjne temperatury i statusy
- ekran Start, Moje SPA, Harmonogram i Statystyki
- ciemny wygląd inspirowany projektem Smart Spa
- szybkie sesje: 1, 5, 10, 15, 20 i 30 minut

## Budowanie w CodeMagic

1. Wgraj cały katalog projektu do repozytorium GitHub.
2. W CodeMagic wybierz Android / Gradle.
3. Ustaw build type na `debug` albo `release`.
4. Komenda budowania:

```bash
./gradlew assembleDebug
```

APK znajdziesz w:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Aplikacja jest demonstracją interfejsu. Nie steruje żadnym fizycznym urządzeniem.
