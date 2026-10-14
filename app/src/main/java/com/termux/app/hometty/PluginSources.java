package com.termux.app.hometty;
import org.tomlj.*;
import java.util.*;
/** Human catalog registrations live in TOML, separate from runtime grants. */
public final class PluginSources {
    public static final class Source {
        public final String name,url;
        Source(String name,String url) { this.name=name; this.url=url; }
    }
    public static List<Source> parse(TomlTable configuration) {
        Object value=configuration.get("plugin_sources"); if (value==null) return Collections.emptyList();
        if (!(value instanceof TomlArray)) throw new IllegalArgumentException("plugin_sources must be an array of tables");
        TomlArray array=(TomlArray)value; if (array.size()>100) throw new IllegalArgumentException("At most 100 plugin sources");
        List<Source> result=new ArrayList<>(); Set<String> urls=new HashSet<>();
        for (int index=0;index<array.size();index++) {
            if (!(array.get(index) instanceof TomlTable)) throw new IllegalArgumentException("Plugin sources must be tables");
            TomlTable item=array.getTable(index); String url=item.getString("url"),name=item.getString("name",()->"Catalog"); PluginCatalog.validateUrl(url);
            if (name.length()>256 || !urls.add(url)) throw new IllegalArgumentException("Invalid or duplicate plugin source");
            result.add(new Source(name,url));
        }
        return result;
    }
    public static String register(String configuration,String address) {
        java.net.URI uri=PluginCatalog.validateUrl(address);
        TomlParseResult before=HomeConfig.parse(configuration);
        for (Source existing:parse(before)) if (existing.url.equals(address)) return configuration;
        String result=configuration.replaceAll("\\s+$","")+"\n\n[[plugin_sources]]\nname = "+ConfigEdits.literal(uri.getHost())+"\nurl = "+ConfigEdits.literal(address)+"\n";
        HomeConfig.parse(result); return result;
    }
}
