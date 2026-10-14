package com.termux.app.hometty;
import org.junit.Test;
import static org.junit.Assert.*;
public class PluginCatalogTest {
    private final String manifest="[plugin]\nid='wifi'\nname='Wi-Fi'\nversion='1'\ncapabilities=['android_settings']\n[action]\nkind='settings'\ntarget='wifi'\n";
    @Test public void manifestDigestAndIdentityMustMatchBeforeImport() {
        String catalog="[catalog]\nformat=1\n[[plugins]]\nid='wifi'\nname='Wi-Fi'\nversion='1'\nurl='https://example.com/wifi.toml'\nsha256='"+PluginManifest.digest(manifest)+"'";
        PluginCatalog.Entry entry=new PluginCatalog(catalog,false).entries.get(0); assertEquals("wifi",entry.verify(manifest).id);
        try { entry.verify(manifest+"# changed"); fail(); } catch (IllegalArgumentException expected) {}
        try { new PluginCatalog(catalog.replace("https://example.com/wifi.toml","asset:/plugins/wifi.toml"),false); fail(); } catch (IllegalArgumentException expected) {}
    }
    @Test public void githubBlobSourcesResolveWithoutRunningCode() {
        assertEquals("https://raw.githubusercontent.com/owner/repo/revision/catalog.toml",PluginCatalog.resolveSource("https://github.com/owner/repo/blob/revision/catalog.toml").toString());
        assertEquals(1,PluginSources.parse(HomeConfig.parse(PluginSources.register(HomeConfig.DEFAULT,"https://example.com/catalog.toml"))).size());
    }
    @Test public void surfacesRequireExplicitCapabilitiesAndValidThemes() {
        try { new PluginManifest(manifest.replace("[action]","surface='widget'\n[action]")); fail(); } catch (IllegalArgumentException expected) {}
        PluginManifest theme=new PluginManifest("[plugin]\nid='night'\nname='Night'\nsurface='theme'\ncapabilities=['theme']\n[theme]\nbackground='#101418'"); assertEquals("theme",theme.kind);
        try { new PluginManifest("[plugin]\nid='night'\nname='Night'\nsurface='theme'\ncapabilities=[]\n[theme]\nbackground='#101418'"); fail(); } catch (IllegalArgumentException expected) {}
    }
}
