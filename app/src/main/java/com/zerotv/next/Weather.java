package com.zerotv.next;

import android.content.SharedPreferences;
import android.text.format.DateFormat;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Locale;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class Weather {
    static final String DEFAULT_LOCATION = "New Delhi";
    static final double DEFAULT_LATITUDE = 28.6139;
    static final double DEFAULT_LONGITUDE = 77.2090;
    private static final String FORECAST_QUERY = "&current=temperature_2m,weather_code,is_day"
            + "&temperature_unit=celsius&forecast_days=1&timeformat=unixtime";
    private static final String AIR_QUALITY_QUERY = "&hourly=pm2_5,pm10,nitrogen_dioxide,sulphur_dioxide,carbon_monoxide,ozone"
            + "&past_days=2&forecast_days=1&domains=cams_global&timeformat=unixtime";
    private static final long MAX_AGE = 3 * 60 * 60 * 1000L;
    private final SharedPreferences cache;
    private final String coordinates;
    final String location;

    Weather(SharedPreferences cache, String location, double latitude, double longitude) {
        this.cache = cache;
        this.location = location;
        coordinates = "?latitude=" + latitude + "&longitude=" + longitude;
    }

    String summary() {
        long age = System.currentTimeMillis() - cache.getLong("observed_at", 0);
        if (age < -900_000 || age > MAX_AGE) {
            return null;
        }
        int temperature = cache.getInt("temperature", 0);
        int code = cache.getInt("weather_code", -1);
        return temperature + "°C  ·  " + condition(code);
    }

    void refresh() throws IOException, JSONException {
        JSONObject current = fetch("https://api.open-meteo.com/v1/forecast" + coordinates
                + FORECAST_QUERY).getJSONObject("current");
        double temperature = current.getDouble("temperature_2m");
        int code = current.getInt("weather_code");
        int day = current.getInt("is_day");
        long observed = current.getLong("time") * 1000;
        long age = System.currentTimeMillis() - observed;
        if (!(temperature >= -90 && temperature <= 65)
                || code < 0 || code > 99 || day < 0 || day > 1 || age < -900_000 || age > MAX_AGE) {
            throw new IOException("Weather response is out of range");
        }
        cache.edit().putInt("temperature", (int) Math.round(temperature))
                .putInt("weather_code", code).putLong("observed_at", observed)
                .putBoolean("is_day", day == 1).putLong("weather_fetched_at", System.currentTimeMillis()).apply();
    }

    Integer airQualityIndex() {
        long age = System.currentTimeMillis() - cache.getLong("india_aqi_observed_at", 0);
        int index = cache.getInt("india_aqi", -1);
        return age < -900_000 || age > MAX_AGE || index < 0 || index > 500 ? null : index;
    }

    void refreshAirQuality() throws IOException, JSONException {
        JSONObject response = fetch("https://air-quality-api.open-meteo.com/v1/air-quality" + coordinates
                + AIR_QUALITY_QUERY);
        JSONObject hourly = response.getJSONObject("hourly");
        JSONObject units = response.getJSONObject("hourly_units");
        if (!"unixtime".equals(units.getString("time"))) {
            throw new IOException("Unexpected air quality timestamp unit");
        }
        JSONArray timestamps = hourly.getJSONArray("time");
        long[] times = new long[timestamps.length()];
        for (int i = 0; i < times.length; i++) times[i] = timestamps.getLong(i) * 1000;
        double[][] samples = new double[IndianAqi.POLLUTANTS.length][times.length];
        for (int pollutant = 0; pollutant < IndianAqi.POLLUTANTS.length; pollutant++) {
            String name = IndianAqi.POLLUTANTS[pollutant];
            if (!"μg/m³".equals(units.getString(name))) {
                throw new IOException("Unexpected concentration unit for " + name);
            }
            JSONArray values = hourly.getJSONArray(name);
            if (values.length() != times.length) throw new IOException("Incomplete pollutant series");
            for (int hour = 0; hour < times.length; hour++) {
                samples[pollutant][hour] = values.optDouble(hour, Double.NaN);
            }
        }
        IndianAqi.Reading reading;
        try {
            reading = IndianAqi.calculate(times, samples, System.currentTimeMillis());
        } catch (IllegalArgumentException invalid) {
            throw new IOException("Invalid hourly air quality data", invalid);
        }
        if (reading == null) {
            throw new IOException("Insufficient recent pollutant data for Indian AQI");
        }
        JSONArray history = new JSONArray();
        for (int hour = 24; hour >= 0; hour--) {
            IndianAqi.Reading point = IndianAqi.calculate(times, samples, reading.observedAt - hour * 3_600_000L);
            history.put(point == null ? -1 : point.index);
        }
        JSONArray averages = new JSONArray();
        for (double value : reading.averages) averages.put(Double.isNaN(value) ? JSONObject.NULL : value);
        cache.edit().putInt("india_aqi", reading.index)
                .putLong("india_aqi_observed_at", reading.observedAt)
                .putString("india_aqi_history", history.toString())
                .putString("india_aqi_averages", averages.toString())
                .putInt("india_aqi_dominant", reading.dominantPollutant).apply();
    }

    int code() { return summary() == null ? -1 : cache.getInt("weather_code", -1); }

    boolean isDay() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return cache.getBoolean("is_day", hour >= 6 && hour < 18);
    }

    String updatedTime() {
        long time = cache.getLong("india_aqi_observed_at", 0);
        return time == 0 ? "AQI update pending" : "AQI model time  " + DateFormat.format("d MMM, h:mm a", time);
    }

    int[] history() {
        int[] values = new int[25];
        java.util.Arrays.fill(values, -1);
        try {
            JSONArray cached = new JSONArray(cache.getString("india_aqi_history", "[]"));
            int count = Math.min(values.length, cached.length());
            for (int i = 0; i < count; i++) {
                int value = cached.optInt(cached.length() - count + i, -1);
                values[values.length - count + i] = value >= 0 && value <= 500 ? value : -1;
            }
        } catch (JSONException ignored) {
            // A refresh will replace an invalid cache.
        }
        return values;
    }

    String[] pollutantDetails() {
        String[] labels = {"PM2.5 · 24h", "PM10 · 24h", "NO₂ · 24h", "SO₂ · 24h", "CO · 8h", "Ozone · 8h"};
        String[] result = new String[labels.length];
        JSONArray averages;
        try {
            averages = new JSONArray(cache.getString("india_aqi_averages", "[]"));
        } catch (JSONException ignored) {
            averages = new JSONArray();
        }
        for (int i = 0; i < labels.length; i++) {
            double value = averages.optDouble(i, Double.NaN);
            String concentration = Double.isNaN(value) ? "Unavailable"
                    : String.format(Locale.getDefault(), "%.1f %s", value, i == 4 ? "mg/m³" : "μg/m³");
            result[i] = labels[i] + "    " + concentration
                    + (i == cache.getInt("india_aqi_dominant", -1) ? "  ·  Highest index" : "");
        }
        return result;
    }

    static String airQualityCategory(int index) {
        if (index <= 50) return "Good";
        if (index <= 100) return "Satisfactory";
        if (index <= 200) return "Moderate";
        if (index <= 300) return "Poor";
        if (index <= 400) return "Very poor";
        return "Severe";
    }

    static int airQualityColor(int index) {
        if (index <= 50) return 0xFF9AE6B4;
        if (index <= 100) return 0xFFC9E69A;
        if (index <= 200) return 0xFFF6DF91;
        if (index <= 300) return 0xFFFFC08C;
        if (index <= 400) return 0xFFFFABA8;
        return 0xFFFF9FBD;
    }

    private JSONObject fetch(String endpoint) throws IOException, JSONException {
        HttpsURLConnection connection = (HttpsURLConnection) new URL(endpoint).openConnection();
        connection.setConnectTimeout(7000);
        connection.setReadTimeout(7000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept", "application/json");
        try {
            if (connection.getResponseCode() != 200) {
                throw new IOException("Conditions HTTP " + connection.getResponseCode());
            }
            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                char[] buffer = new char[2048];
                int read;
                while ((read = reader.read(buffer)) != -1) {
                    body.append(buffer, 0, read);
                    if (body.length() > 65536) {
                        throw new IOException("Weather response is too large");
                    }
                }
            }
            return new JSONObject(body.toString());
        } finally {
            connection.disconnect();
        }
    }

    private String condition(int code) {
        if (code == 0) return "Clear";
        if (code < 3) return "Partly cloudy";
        if (code == 3) return "Overcast";
        if (code <= 48) return "Fog";
        if (code <= 57) return "Drizzle";
        if (code <= 67) return "Rain";
        if (code <= 77) return "Snow";
        if (code <= 82) return "Showers";
        if (code <= 86) return "Snow showers";
        return "Thunderstorms";
    }
}
