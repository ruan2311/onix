package com.onix.launcher;

import java.net.URLEncoder;
import java.util.Locale;

/**
 * Clima da tela inicial (Open-Meteo, sem chave). Só busca a cidade que a pessoa escolheu: o Onix
 * não pede localização. Devolve o JSON da API como veio; a página só usa os números que ela valida.
 */
final class Weather {

    private static final int MAX_BYTES = 256 * 1024;

    private Weather() {
    }

    /** Agora, as próximas horas e os próximos dias, no fuso da própria cidade. */
    static String forecast(double lat, double lon) throws Exception {
        String url = String.format(Locale.ROOT,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                        + "&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m,is_day"
                        + "&hourly=temperature_2m,weather_code,is_day"
                        + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max"
                        + "&timezone=auto&forecast_days=6", lat, lon);
        return new String(WeeklyPicks.download(url, MAX_BYTES), "UTF-8");
    }

    /** Cidades com esse nome (com estado e país, para separar as Campo Grande da vida). */
    static String searchCity(String name, String lang) throws Exception {
        String url = "https://geocoding-api.open-meteo.com/v1/search?count=6&format=json&language=" + lang
                + "&name=" + URLEncoder.encode(name, "UTF-8");
        return new String(WeeklyPicks.download(url, MAX_BYTES), "UTF-8");
    }
}
