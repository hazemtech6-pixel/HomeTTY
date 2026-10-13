package com.termux.app.hometty;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.file.*;
import static org.junit.Assert.*;
public class HomeFilesTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    @Test public void operationsRejectTraversalSymlinksOverwriteAndRecursiveDeletion() throws Exception {
        File home=temporary.newFolder("home"),outside=temporary.newFolder("outside");
        HomeFiles.create(home,home,"notes",false); HomeFiles.create(home,home,"existing",false);
        try { HomeFiles.rename(home,new File(home,"notes"),"existing"); fail(); } catch (IOException expected) {}
        for (String name:new String[]{"../escape","..","/absolute",""}) { try { HomeFiles.create(home,home,name,false); fail(name); } catch (IOException expected) {} }
        Files.createSymbolicLink(new File(home,"linked").toPath(),outside.toPath());
        try { HomeFiles.create(home,new File(home,"linked"),"escape",false); fail(); } catch (IOException expected) {}
        HomeFiles.create(home,home,"folder",true); HomeFiles.create(home,new File(home,"folder"),"inside",false);
        try { HomeFiles.delete(home,new File(home,"folder")); fail(); } catch (IOException expected) {}
        HomeFiles.rename(home,new File(home,"notes"),"renamed"); assertTrue(new File(home,"renamed").exists());
        HomeFiles.delete(home,new File(home,"renamed")); assertFalse(new File(home,"renamed").exists());
    }
}
