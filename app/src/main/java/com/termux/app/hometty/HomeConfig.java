package com.termux.app.hometty;

import android.util.AtomicFile;
import com.termux.shared.termux.TermuxConstants;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** One human-editable configuration authority; runtime data lives elsewhere. */
public final class HomeConfig {
    public static final File HOME = new File(TermuxConstants.TERMUX_HOME_DIR_PATH);
    public static final File DATA = new File(HOME, ".local/share/hometty");
    public static final File CACHE = new File(HOME, ".cache/hometty");
    public static final File FILE = new File(HOME, ".config/hometty/config.toml");
    public static final String DEFAULT = "# HomeTTY configuration\n[home]\ncolumns = 4\nclock = true\ndock = true\n\n[ai]\nenabled = false\n\n[security]\nlock_terminal = false\nlock_settings = false\nlock_apps = false\n\n[desktop]\nbackend = \"x11\"\n\n[privilege]\nrish = false\n";
    private final TomlParseResult values;
    public HomeConfig() throws IOException {
        if (HomeArchive.needsRecovery(HOME)) throw new IOException("A Home restore needs recovery; open the HomeTTY Home screen first");
        if (!FILE.getParentFile().isDirectory() && !FILE.getParentFile().mkdirs()) throw new IOException("Cannot create configuration directory");
        DATA.mkdirs(); CACHE.mkdirs();
        if (!FILE.exists()) save(DEFAULT);
        values = parse(read());
    }
    public static TomlParseResult parse(String text) {
        TomlParseResult result = Toml.parse(text);
        if (result.hasErrors()) throw new IllegalArgumentException(result.errors().toString());
        for (String key : new String[]{"home.clock", "home.dock", "home.media", "weather.enabled", "ai.enabled", "security.lock_terminal", "security.lock_settings", "security.lock_apps", "security.lock_desktop", "security.lock_privileged", "privilege.rish", "setup.api", "setup.widget", "setup.tasker", "setup.x11", "setup.desktop", "setup.tools"})
            if (result.contains(key) && !(result.get(key) instanceof Boolean)) throw new IllegalArgumentException(key + " must be true or false");
        if (result.contains("home.columns") && (!(result.get("home.columns") instanceof Long) || result.getLong("home.columns") < 2 || result.getLong("home.columns") > 8))
            throw new IllegalArgumentException("home.columns must be between 2 and 8");
        if (result.contains("home.rows") && (!(result.get("home.rows") instanceof Long) || result.getLong("home.rows")<1 || result.getLong("home.rows")>8)) throw new IllegalArgumentException("home.rows must be between 1 and 8");
        for (String key : new String[]{"weather.latitude", "weather.longitude"}) {
            if (result.contains(key)) {
                Object value = result.get(key);
                double limit = key.endsWith("latitude") ? 90 : 180;
                if (!(value instanceof Number) || !Double.isFinite(((Number)value).doubleValue()) || Math.abs(((Number)value).doubleValue()) > limit)
                    throw new IllegalArgumentException(key + " must be a finite coordinate between -" + limit + " and " + limit);
            }
        }
        if (result.contains("desktop.backend") && !java.util.Arrays.asList("x11", "vnc").contains(result.get("desktop.backend")))
            throw new IllegalArgumentException("desktop.backend must be x11 or vnc");
        if (result.contains("desktop.environment") && !java.util.Arrays.asList("xfce", "lxqt", "openbox", "mate", "gnome", "cinnamon", "plasma", "i3wm", "dwm", "bspwm", "awesome", "fluxbox", "icewm", "wmaker").contains(result.get("desktop.environment")))
            throw new IllegalArgumentException("Unknown desktop.environment");
        if (result.contains("ai.backend") && !java.util.Arrays.asList("external", "ollama").contains(result.get("ai.backend"))) throw new IllegalArgumentException("ai.backend must be external or ollama");
        for (String key : new String[]{"ai.endpoint","ai.model","ai.external_command"}) if (result.contains(key) && !(result.get(key) instanceof String)) throw new IllegalArgumentException(key+" must be text");
        for (String key : result.dottedKeySet()) {
            String lower = key.toLowerCase(java.util.Locale.ROOT);
            if (lower.endsWith("api_key") || lower.endsWith("password") || lower.endsWith("secret") || lower.endsWith("token"))
                throw new IllegalArgumentException("Secrets must be stored in Android Keystore, not TOML: " + key);
        }
        if (result.contains("app_overrides")) {
            if (!(result.get("app_overrides") instanceof org.tomlj.TomlTable)) throw new IllegalArgumentException("app_overrides must be a table");
            org.tomlj.TomlTable overrides=result.getTable("app_overrides");
            for (String component:overrides.keySet()) {
                Object value=overrides.get(java.util.Collections.singletonList(component)); if (!(value instanceof org.tomlj.TomlTable)) throw new IllegalArgumentException("App overrides must be tables");
                org.tomlj.TomlTable fields=(org.tomlj.TomlTable)value;
                for (String key:new String[]{"label","icon"}) if (fields.contains(key) && !(fields.get(key) instanceof String)) throw new IllegalArgumentException("App "+key+" override must be text");
            }
        }
        PluginSources.parse(result);
        for (String field:new String[]{"home.pins","home.layout"}) if (result.contains(field)) {
            if (!(result.get(field) instanceof org.tomlj.TomlArray)) throw new IllegalArgumentException(field+" must be an array of strings");
            org.tomlj.TomlArray pins=result.getArray(field);
            if (pins.size()>4096) throw new IllegalArgumentException(field+" must contain at most 4096 strings");
            for (int index=0;index<pins.size();index++) if (!(pins.get(index) instanceof String) || pins.getString(index).length()>4096) throw new IllegalArgumentException("Pin identifiers must be strings of at most 4096 characters");
            if ("home.layout".equals(field)) {
                java.util.List<String> slots=new java.util.ArrayList<>(); for (int index=0;index<pins.size();index++) slots.add(pins.getString(index));
                new HomeLayout(slots,result.contains("home.columns")?result.getLong("home.columns").intValue():4,result.contains("home.rows")?result.getLong("home.rows").intValue():3);
            }
        }
        if (result.contains("theme.plugin") && !(result.get("theme.plugin") instanceof String)) throw new IllegalArgumentException("theme.plugin must be a plugin ID");
        return result;
    }
    public boolean flag(String key, boolean fallback) { Boolean value = values.getBoolean(key); return value == null ? fallback : value; }
    public boolean has(String key) { return values.contains(key); }
    public java.util.List<String> strings(String key) {
        java.util.List<String> result=new java.util.ArrayList<>(); org.tomlj.TomlArray array=values.getArray(key);
        if (array!=null) for (int index=0;index<array.size();index++) result.add(array.getString(index));
        return result;
    }
    public String string(String key,String fallback) { String value=values.getString(key); return value==null ? fallback : value; }
    public String stringPath(java.util.List<String> path,String fallback) { String value=values.getString(path); return value==null?fallback:value; }
    public double number(String key,double fallback) { Object value=values.get(key); return value instanceof Number ? ((Number)value).doubleValue() : fallback; }
    public int columns() { Long value = values.getLong("home.columns"); return value == null ? 4 : value.intValue(); }
    public static String read() throws IOException {
        try (InputStream in = new AtomicFile(FILE).openRead(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] b = new byte[4096]; int n;
            while ((n = in.read(b)) != -1) { if (out.size() + n > 262144) throw new IOException("Configuration too large"); out.write(b, 0, n); }
            return out.toString("UTF-8");
        }
    }
    public static void save(String text) throws IOException {
        parse(text);
        AtomicFile file = new AtomicFile(FILE); FileOutputStream out = null;
        try { out = file.startWrite(); out.write(text.getBytes(StandardCharsets.UTF_8)); file.finishWrite(out); }
        catch (IOException e) { if (out != null) file.failWrite(out); throw e; }
    }
}
