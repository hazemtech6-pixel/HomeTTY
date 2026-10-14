package com.termux.app.hometty;

import android.util.AtomicFile;
import java.io.*;
import java.util.Properties;

/** State is separate from TOML and survives process death without partial writes. */
final class LauncherState {
    private final Properties values = new Properties();
    private final AtomicFile file = new AtomicFile(new File(HomeConfig.DATA, "launcher.properties"));
    LauncherState() throws IOException {
        if (file.getBaseFile().exists()) try (InputStream in = file.openRead()) { values.load(in); }
    }
    java.util.Set<String> keys() { return new java.util.HashSet<>(values.stringPropertyNames()); }
    String get(String key, String fallback) { return values.getProperty(key, fallback); }
    boolean pinned(String id) { return Boolean.parseBoolean(get("pin." + id, "false")); }
    int uses(String id) { try { return Integer.parseInt(get("uses." + id, "0")); } catch (NumberFormatException e) { return 0; } }
    void put(String key, String value) throws IOException {
        Object old = values.setProperty(key, value); FileOutputStream out = null;
        try { out = file.startWrite(); values.store(out, "HomeTTY runtime state"); file.finishWrite(out); }
        catch (IOException e) { if (out != null) file.failWrite(out); if (old == null) values.remove(key); else values.put(key, old); throw e; }
    }
}
