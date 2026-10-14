package com.termux.app.hometty;

import org.tomlj.*;
import java.util.*;

/** Declarative plugins cannot execute code merely by being registered or parsed. */
public final class PluginManifest {
    public final String id, name, kind, target, surface, version, text;
    public final Map<String,String> colors;
    public final Set<String> capabilities;
    public PluginManifest(String source) {
        TomlParseResult toml=HomeConfig.parse(source);
        if (toml.hasErrors()) throw new IllegalArgumentException(toml.errors().toString());
        id=toml.getString("plugin.id"); name=toml.getString("plugin.name");
        surface=toml.getString("plugin.surface",()->"tool"); version=toml.getString("plugin.version",()->"0");
        if (!Arrays.asList("tool","command","widget","tab","theme","integration").contains(surface)) throw new IllegalArgumentException("Unsupported plugin surface");
        text=toml.getString("view.text",()->name==null?"":name);
        if (text.length()>4096 || version.length()>64) throw new IllegalArgumentException("Plugin metadata too long");
        Map<String,String> theme=new LinkedHashMap<>();
        if ("theme".equals(surface)) {
            for (String color:Arrays.asList("background","foreground","accent")) {
                String value=toml.getString("theme."+color); if (value!=null) { if (!value.matches("#[0-9a-fA-F]{6}(?:[0-9a-fA-F]{2})?")) throw new IllegalArgumentException("Theme colors must be hexadecimal RGB or ARGB"); theme.put(color,value); }
            }
            if (theme.isEmpty()) throw new IllegalArgumentException("Theme has no colors");
            kind="theme"; target="";
        } else { kind=toml.getString("action.kind"); target=toml.getString("action.target"); }
        colors=Collections.unmodifiableMap(theme);
        if (id==null || !id.matches("[a-z][a-z0-9_-]{0,63}") || name==null || name.isEmpty() || name.length()>256 || target==null || (!"theme".equals(surface) && target.isEmpty())) throw new IllegalArgumentException("Missing or invalid plugin metadata");
        TomlArray requested=toml.getArray("plugin.capabilities"); Set<String> values=new LinkedHashSet<>();
        if (requested!=null) for (int i=0;i<requested.size();i++) { String capability=requested.getString(i); if (!Arrays.asList("open_web","launch_app","shell","android_settings","widget","tab","theme").contains(capability)) throw new IllegalArgumentException("Unsupported capability: "+capability); values.add(capability); }
        capabilities=Collections.unmodifiableSet(values);
        String required;
        if ("web".equals(kind)) { required="open_web"; java.net.URI uri=java.net.URI.create(target); if (!"https".equals(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null) throw new IllegalArgumentException("Web actions require HTTPS URLs without credentials"); }
        else if ("app".equals(kind)) { required="launch_app"; if (!target.matches("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+")) throw new IllegalArgumentException("Invalid application package"); }
        else if ("shell".equals(kind)) required="shell";
        else if ("settings".equals(kind)) { required="android_settings"; if (!Arrays.asList("wifi","bluetooth","calendar","calculator","android").contains(target)) throw new IllegalArgumentException("Unsupported Android settings target"); }
        else if ("theme".equals(kind)) required="theme";
        else throw new IllegalArgumentException("Unsupported plugin action");
        if (("widget".equals(surface) || "tab".equals(surface)) && !values.contains(surface)) throw new IllegalArgumentException("Surface requires capability: "+surface);
        if (!values.contains(required)) throw new IllegalArgumentException("Action requires capability: "+required);
    }
    public boolean canRun(Set<String> granted) { return granted.containsAll(capabilities); }
    public static String digest(String source) {
        try { byte[] bytes=java.security.MessageDigest.getInstance("SHA-256").digest(source.getBytes(java.nio.charset.StandardCharsets.UTF_8)); StringBuilder hex=new StringBuilder(); for (byte b:bytes) hex.append(String.format(java.util.Locale.ROOT,"%02x",b & 255)); return hex.toString(); }
        catch (java.security.NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
}
