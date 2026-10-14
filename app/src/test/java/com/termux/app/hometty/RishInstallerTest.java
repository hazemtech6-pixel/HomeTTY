package com.termux.app.hometty;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;
public class RishInstallerTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    @Test public void payloadRequiresReviewAndCorrectIdentity() throws Exception {
        File home=folder.newFolder("home"),cache=folder.newFolder("cache");
        File payload=RishInstaller.stage(new ByteArrayInputStream(new byte[]{'d','e','x','\n','0','3','9',0,1}),cache);
        assertFalse(new File(home,".local/share/hometty/rish/rish").exists());
        File launcher=RishInstaller.install(payload,home);
        String text=new String(Files.readAllBytes(launcher.toPath()),StandardCharsets.UTF_8);
        assertTrue(text.contains("RISH_APPLICATION_ID=com.hazemtech.termux.hometty"));
        assertTrue(RishInstaller.command(home).startsWith("/system/bin/sh "));
        Files.write(payload.toPath(),new byte[]{1});
        try { RishInstaller.install(payload,home); fail("Changed staged payload accepted"); } catch(IOException expected) { assertTrue(expected.getMessage().contains("changed")); }
    }
    @Test public void rejectNonDex() throws Exception {
        try { RishInstaller.stage(new ByteArrayInputStream("malicious script".getBytes(StandardCharsets.UTF_8)),folder.newFolder()); fail(); } catch(IOException expected) { assertTrue(expected.getMessage().contains("exported")); }
    }
}
