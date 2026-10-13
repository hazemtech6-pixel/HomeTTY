package com.termux.app.hometty;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;
public class SearchIndexTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    @Test public void userHiddenEntryOverridesSystemAndHomeSymlinksAreExcluded() throws Exception {
        File home=temporary.newFolder("home"),prefix=temporary.newFolder("prefix"),outside=temporary.newFolder("outside");
        File user=new File(home,".local/share/applications"),system=new File(prefix,"share/applications"); assertTrue(user.mkdirs()); assertTrue(system.mkdirs());
        Files.write(new File(system,"browser.desktop").toPath(),"[Desktop Entry]\nType=Application\nName=Browser\nExec=browser\n".getBytes("UTF-8"));
        Files.write(new File(user,"browser.desktop").toPath(),"[Desktop Entry]\nType=Application\nHidden=true\n".getBytes("UTF-8"));
        Files.write(new File(home,"notes.txt").toPath(),"notes".getBytes("UTF-8"));
        Files.write(new File(outside,"private.txt").toPath(),"private".getBytes("UTF-8"));
        Files.createSymbolicLink(new File(home,"linked").toPath(),outside.toPath());
        List<SearchIndex.Entry> entries=SearchIndex.build(home,prefix);
        assertEquals(1,entries.size()); assertEquals("notes.txt",entries.get(0).title); assertEquals("file",entries.get(0).kind);
    }
}
