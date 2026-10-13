package com.termux.app.hometty;

import org.tomlj.*;
import java.util.*;

/** Declarative plugins cannot execute code merely by being registered or parsed. */
public final class PluginManifest {
    public final String id, name, kind, target;
    public final Set<String> capabilities;
    public PluginManifest(String source) {
        TomlParseResult toml=Toml.parse(source);
        if (toml.hasErrors()) throw new IllegalArgumentException(toml.errors().toString());
        id=toml.getString("plugin.id"); name=toml.getString("plugin.name"); kind=toml.getString("action.kind"); target=toml.getString("action.target");
        if (id==null || !id.matches("[a-z][a-z0-9_-]{0,63}") || name==null || name.isEmpty() || target==null || target.isEmpty()) throw new IllegalArgumentException("Missing or invalid plugin metadata");
        TomlArray requested=toml.getArray("plugin.capabilities"); Set<String> values=new LinkedHashSet<>();
        if (requested!=null) for (int i=0;i<requested.size();i++) { String capability=requested.getString(i); if (!Arrays.asList("open_web","launch_app","shell").contains(capability)) throw new IllegalArgumentException("Unsupported capability: "+capability); values.add(capability); }
        capabilities=Collections.unmodifiableSet(values);
        String required;
        if ("web".equals(kind)) { required="open_web"; java.net.URI uri=java.net.URI.create(target); if (!"https".equals(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null) throw new IllegalArgumentException("Web actions require HTTPS URLs without credentials"); }
        else if ("app".equals(kind)) { required="launch_app"; if (!target.matches("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+")) throw new IllegalArgumentException("Invalid application package"); }
        else if ("shell".equals(kind)) required="shell";
        else throw new IllegalArgumentException("Unsupported plugin action");
        if (!values.contains(required)) throw new IllegalArgumentException("Action requires capability: "+required);
    }
    public boolean canRun(Set<String> granted) { return granted.containsAll(capabilities); }
    public static String digest(String source) {
        try { byte[] bytes=java.security.MessageDigest.getInstance("SHA-256").digest(source.getBytes(java.nio.charset.StandardCharsets.UTF_8)); StringBuilder hex=new StringBuilder(); for (byte b:bytes) hex.append(String.format(java.util.Locale.ROOT,"%02x",b & 255)); return hex.toString(); }
        catch (java.security.NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
}
