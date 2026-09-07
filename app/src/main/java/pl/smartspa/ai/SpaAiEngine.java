package pl.smartspa.ai;

/**
 * Lekki silnik rekomendacji Smart Spa.
 * Nie steruje jacuzzi samodzielnie — wylicza tylko rekomendację.
 */
public final class SpaAiEngine {
    private SpaAiEngine() {}

    public static int recommendedTemperature(int outsideTemp, int weatherCode) {
        int result;
        if (outsideTemp <= 7) result = 38;
        else if (outsideTemp <= 20) result = 37;
        else if (outsideTemp <= 27) result = 36;
        else result = 35;

        if ((weatherCode >= 51 && weatherCode <= 67) ||
            (weatherCode >= 71 && weatherCode <= 86)) {
            result = Math.min(40, result + 1);
        }
        return Math.max(20, Math.min(40, result));
    }

    public static String weatherDescription(int code) {
        if (code == 0) return "Bezchmurnie";
        if (code <= 3) return "Pogodnie / częściowe zachmurzenie";
        if (code <= 48) return "Mgła / zachmurzenie";
        if (code <= 55) return "Mżawka";
        if (code <= 67) return "Deszcz";
        if (code <= 77) return "Śnieg";
        if (code <= 82) return "Przelotne opady";
        if (code <= 86) return "Opady śniegu";
        return "Burza";
    }

    public static String shortWeatherDescription(int code) {
        if (code == 0) return "bezchmurnie";
        if (code <= 3) return "częściowe zachmurzenie";
        if (code <= 48) return "mgła lub zachmurzenie";
        if (code <= 55) return "mżawka";
        if (code <= 67) return "deszcz";
        if (code <= 77) return "śnieg";
        if (code <= 82) return "przelotne opady";
        if (code <= 86) return "opady śniegu";
        return "burza";
    }
}
