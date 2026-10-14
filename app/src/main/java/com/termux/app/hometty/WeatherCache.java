package com.termux.app.hometty;
import java.io.*;

/** Fifteen-minute coordinate-specific cache; stale output is labelled on network failure. */
public final class WeatherCache {
    public static final long TTL=15*60*1000;
    public static String current(WeatherProvider provider,File directory,double latitude,double longitude,long now) throws Exception {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude) || Math.abs(latitude)>90 || Math.abs(longitude)>180) throw new IllegalArgumentException("Invalid weather coordinates");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create weather cache");
        File file=new File(directory,"weather-"+PluginManifest.digest(latitude+":"+longitude)+".cache");
        String cached=null; long written=0;
        if (file.isFile() && file.length()<131072) {
            try (DataInputStream input=new DataInputStream(new FileInputStream(file))) { written=input.readLong(); cached=input.readUTF(); }
            catch (IOException invalid) { cached=null; }
        }
        if (cached!=null && now>=written && now-written<TTL) return cached;
        String result;
        try { result=provider.current(latitude,longitude); }
        catch (Exception unavailable) { if (cached!=null) return cached+"\nCached weather · refresh unavailable"; throw unavailable; }
        if (result.length()>16384) throw new IOException("Weather text too long");
        File temporary=File.createTempFile("weather-", ".tmp",directory);
        try {
            try (FileOutputStream raw=new FileOutputStream(temporary); DataOutputStream output=new DataOutputStream(raw)) { output.writeLong(now); output.writeUTF(result); output.flush(); raw.getFD().sync(); }
            if (!temporary.renameTo(file)) throw new IOException("Cannot commit weather cache");
        } finally { temporary.delete(); }
        return result;
    }
}
