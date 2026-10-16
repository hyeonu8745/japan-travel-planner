package com.example.japantravelplanner.weather;

/** WMO 날씨 코드 → 아이콘 종류·한국어 (Open-Meteo 문서의 코드표) */
public enum WeatherCondition {
    SUNNY("맑음"), PARTLY_CLOUDY("구름 조금"), CLOUDY("흐림"), FOG("안개"), DRIZZLE("이슬비"),
    RAIN("비"), SNOW("눈"), THUNDER("뇌우"), UNKNOWN("정보 없음");

    private final String label;

    WeatherCondition(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean wet() {
        return this == RAIN || this == THUNDER || this == DRIZZLE || this == SNOW;
    }

    public static WeatherCondition of(Integer code) {
        if (code == null) return UNKNOWN;
        if (code == 0) return SUNNY;
        if (code == 1 || code == 2) return PARTLY_CLOUDY;
        if (code == 3) return CLOUDY;
        if (code == 45 || code == 48) return FOG;
        if (code >= 51 && code <= 57) return DRIZZLE;
        if ((code >= 61 && code <= 67) || (code >= 80 && code <= 82)) return RAIN;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return SNOW;
        if (code >= 95 && code <= 99) return THUNDER;
        return UNKNOWN;
    }
}
