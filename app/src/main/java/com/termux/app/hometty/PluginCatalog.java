package com.termux.app.hometty;
import org.tomlj.*;
import java.net.URI;
import java.util.*;
/** Catalogs are data. Checksums identify reviewed manifests; parsing cannot install. */
public final class PluginCatalog {
    public static final class Entry {
        public final String id,name,version,url,sha256;
        Entry(TomlTable table,boolean bundled) {
            id=table.getString("id"); name=table.getString("name"); version=table.getString("version"); url=table.getString("url"); sha256=table.getString("sha256");
            if (id==null || !id.matches("[a-z][a-z0-9_-]{0,63}") || name==null || name.isEmpty() || name.length()>256 || version==null || version.length()>64 || sha256==null || !sha256.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Invalid catalog entry");
            if (!(bundled && url!=null && url.matches("asset:/plugins/[a-z0-9_-]+\\.toml"))) validateUrl(url);
        }
        public PluginManifest verify(String source) {
            if (!sha256.equals(PluginManifest.digest(source))) throw new IllegalArgumentException("Manifest checksum mismatch");
            PluginManifest manifest=new PluginManifest(source);
            if (!id.equals(manifest.id) || !version.equals(manifest.version)) throw new IllegalArgumentException("Manifest identity/version mismatch");
            return manifest;
        }
    }
    public final List<Entry> entries;
    public PluginCatalog(String source,boolean bundled) {
        TomlParseResult result=Toml.parse(source); if (result.hasErrors() || !Long.valueOf(1).equals(result.get("catalog.format"))) throw new IllegalArgumentException("Unsupported catalog format");
        TomlArray items=result.getArray("plugins"); List<Entry> parsed=new ArrayList<>(); Set<String> ids=new HashSet<>();
        if (items!=null) { if (items.size()>200) throw new IllegalArgumentException("Catalog exceeds 200 entries"); for (int index=0;index<items.size();index++) { if (!(items.get(index) instanceof TomlTable)) throw new IllegalArgumentException("Catalog entries must be tables"); Entry entry=new Entry(items.getTable(index),bundled); if (!ids.add(entry.id)) throw new IllegalArgumentException("Duplicate catalog plugin ID"); parsed.add(entry); } }
        entries=Collections.unmodifiableList(parsed);
    }
    public static URI validateUrl(String address) {
        URI uri=URI.create(address==null?"":address);
        if (!"https".equals(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null || uri.getFragment()!=null) throw new IllegalArgumentException("Use an HTTPS catalog URL without credentials or fragment");
        return uri;
    }
    public static URI resolveSource(String address) {
        URI uri=validateUrl(address);
        if ("github.com".equalsIgnoreCase(uri.getHost())) {
            String[] parts=uri.getRawPath().split("/",6);
            if (parts.length==6 && "blob".equals(parts[3]) && !parts[1].isEmpty() && !parts[2].isEmpty() && !parts[4].isEmpty() && !parts[5].isEmpty()) return URI.create("https://raw.githubusercontent.com/"+parts[1]+"/"+parts[2]+"/"+parts[4]+"/"+parts[5]);
        }
        return uri;
    }
}
