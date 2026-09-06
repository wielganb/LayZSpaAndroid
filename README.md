# Smart Spa 0.8.3

Android application for Lay-Z-Spa / ESP8266 control over WebSocket.

## 0.8.3 – Dashboard referencyjny + AI
- Sesja startuje po **włączeniu BĄBELKÓW**, a nie po włączeniu grzania.
- Wyłączenie bąbelków kończy aktywną sesję.
- Grzanie może być sterowane niezależnie od sesji.
- Przyciski szybkiego wyboru czasu: 1, 5, 10, 15, 20, 25 i 30 minut.
- Szybkie czasy są rozmieszczone w dwóch rzędach, z większymi odstępami i wyższymi przyciskami.
- Zwiększono pionowe odstępy między sekcjami dashboardu i wysokość głównych przycisków sterowania.
- Zachowano pełny wybór czasu 1–30 min przez NumberPicker.
- Przebudowano przedni ekran według ustalonego projektu: większe odstępy, osobna sekcja SESJA SPA, czytelny czas pozostały + długość sesji oraz równe przyciski sterowania.
- Przywrócono oznaczenia skali 20°C / 40°C na pokrętle temperatury.
- Logika rekomendacji pogodowej została wydzielona do katalogu `pl.smartspa.ai` (`SpaAiEngine`), aby funkcje „AI” były oddzielone od interfejsu.

## 0.8.0 – Inteligentna temperatura
- Open-Meteo weather integration (no API key for the intended non-commercial use).
- SPA location can be selected by searching for a Polish town; GPS permission is not required.
- Current outdoor temperature and weather condition are displayed on the main dashboard.
- Smart recommendation for SPA water temperature (20–40 °C).
- **USTAW ZALECANĄ** requires an explicit user tap; the app never changes the target automatically.
- Android system notifications are scheduled every 6 hours when a SPA location is configured.
- Notification permission is requested on Android 13+.
- Existing ESP8266 control and both home-screen widgets remain.

## Build
Upload the complete project directory to GitHub and build it with CodeMagic.
