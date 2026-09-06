# Smart Spa 0.8.0

Android application for Lay-Z-Spa / ESP8266 control over WebSocket.

## 0.8.0 – Inteligentna temperatura
- Open-Meteo weather integration (no API key for the intended non-commercial use).
- SPA location can be selected by searching for a Polish town; GPS permission is not required.
- Current outdoor temperature and weather condition are displayed in the main dashboard.
- Smart recommendation for SPA water temperature (20–40 °C).
- **USTAW ZALECANĄ** requires an explicit user tap; the app never changes the target automatically.
- Android system notifications are scheduled every 6 hours when a SPA location is configured.
- Notification permission is requested on Android 13+.
- Existing ESP8266 control, 1–30 minute session timer and both home-screen widgets remain.

## Build
Upload the complete project directory to GitHub and build it with CodeMagic.
