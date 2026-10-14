package com.termux.app.hometty;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;
public class WeatherCacheTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    @Test public void freshCacheAvoidsRequestsAndStaleFailureIsLabelled() throws Exception {
        File cache=temporary.newFolder("weather"); AtomicInteger calls=new AtomicInteger(); WeatherProvider provider=(lat,lon)->{ calls.incrementAndGet(); return "Sunny"; };
        assertEquals("Sunny",WeatherCache.current(provider,cache,30,31,1000));
        assertEquals("Sunny",WeatherCache.current(provider,cache,30,31,2000)); assertEquals(1,calls.get());
        String stale=WeatherCache.current((lat,lon)->{ throw new IOException("offline"); },cache,30,31,1000+WeatherCache.TTL);
        assertTrue(stale.contains("Cached weather"));
        WeatherCache.current(provider,cache,30,32,2000); assertEquals(2,calls.get());
    }
}
