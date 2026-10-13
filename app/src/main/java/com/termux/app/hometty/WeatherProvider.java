package com.termux.app.hometty;

import org.json.JSONObject;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Extensible provider boundary. No location permission: coordinates are explicit TOML. */
public interface WeatherProvider {
    String current(double latitude,double longitude) throws Exception;
    final class OpenMeteo implements WeatherProvider {
        @Override public String current(double latitude,double longitude) throws Exception {
            if (!Double.isFinite(latitude) || !Double.isFinite(longitude) || Math.abs(latitude)>90 || Math.abs(longitude)>180) throw new IllegalArgumentException("Invalid weather coordinates");
            HttpURLConnection connection=(HttpURLConnection)new URL("https://api.open-meteo.com/v1/forecast?latitude="+latitude+"&longitude="+longitude+"&current=temperature_2m,weather_code&timezone=auto").openConnection();
            connection.setConnectTimeout(10000); connection.setReadTimeout(10000); connection.setInstanceFollowRedirects(false);
            try {
                if (connection.getResponseCode()!=200) throw new IOException("Weather HTTP "+connection.getResponseCode());
                try (InputStream in=connection.getInputStream(); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                    byte[] b=new byte[4096]; int n; while ((n=in.read(b))!=-1) { if (out.size()+n>131072) throw new IOException("Weather response too large"); out.write(b,0,n); }
                    JSONObject data=new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8)).getJSONObject("current");
                    return data.getDouble("temperature_2m")+" °C · "+description(data.getInt("weather_code"))+"\nOpen-Meteo · "+data.getString("time");
                }
            } finally { connection.disconnect(); }
        }
        private static String description(int code) {
            if (code==0) return "Clear"; if (code<=3) return "Cloudy"; if (code==45 || code==48) return "Fog";
            if (code>=51 && code<=67) return "Rain"; if (code>=71 && code<=77) return "Snow";
            if (code>=80 && code<=86) return "Showers"; if (code>=95) return "Thunderstorm"; return "Weather code "+code;
        }
    }
}
