package com.termux.app.hometty;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class PluginManifestTest {
    private String manifest(String cap,String kind,String target) { return "[plugin]\nid='example'\nname='Example'\ncapabilities=['"+cap+"']\n[action]\nkind='"+kind+"'\ntarget='"+target+"'\n"; }
    @Test public void registrationDoesNotGrantExecution() { PluginManifest p=new PluginManifest(manifest("open_web","web","https://example.com")); assertFalse(p.canRun(Collections.emptySet())); assertTrue(p.canRun(Collections.singleton("open_web"))); }
    @Test public void actionRequiresMatchingCapability() { try { new PluginManifest(manifest("open_web","shell","id")); fail(); } catch (IllegalArgumentException expected) {} }
    @Test public void unsafeSchemesRejected() { for (String target:Arrays.asList("file:///etc/passwd","javascript:alert(1)","https://user:pass@example.com")) { try { new PluginManifest(manifest("open_web","web",target)); fail(); } catch (IllegalArgumentException expected) {} } }
    @Test public void contentChangeInvalidatesGrantDigest() { assertNotEquals(PluginManifest.digest(manifest("shell","shell","id")),PluginManifest.digest(manifest("shell","shell","rm file"))); }
}
