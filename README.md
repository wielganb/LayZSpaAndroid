# Lay-Z-Spa Miami – Android native app

Native Android controller for the ESP8266 firmware from visualapproach/WiFi-remote-for-Bestway-Lay-Z-SPA.

## Current setup
- ESP8266: `192.168.1.40`
- WebSocket: `ws://192.168.1.40:81/`
- Model: Miami
- Firmware reported by the ESP is shown in the app.
- The IP address is saved locally.
- Automatic reconnect every 3 seconds.

## Implemented commands
- CMD 0: target temperature
- CMD 2: bubbles
- CMD 3: heater
- CMD 4: pump/filter
- CMD 11: HydroJet

The app listens for `STATES` and `OTHER` WebSocket JSON messages.

## GitHub + Codemagic
1. Create a new GitHub repository, e.g. `LayZSpaAndroid`.
2. Upload the complete contents of this folder to the repository root.
3. Connect the repository to Codemagic.
4. Start the `android-debug` workflow.
5. Download the generated `app-debug.apk` from the build artifacts.

The ESP8266 firmware repository remains unchanged.
