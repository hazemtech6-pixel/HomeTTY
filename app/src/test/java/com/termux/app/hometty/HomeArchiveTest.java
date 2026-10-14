package com.termux.app.hometty;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.junit.*;
import static org.junit.Assert.*;
public class HomeArchiveTest {
    private Path temporary;
    @Before public void setup() throws Exception { temporary=Files.createTempDirectory("hometty-backup-test"); }
    @After public void cleanup() throws Exception { try (java.util.stream.Stream<Path> paths=Files.walk(temporary)) { paths.sorted(Comparator.reverseOrder()).forEach(path -> { try { Files.delete(path); } catch (IOException ignored) {} }); } }
    private void write(Path root,String path,String text) throws Exception { Path target=root.resolve(path); Files.createDirectories(target.getParent()); Files.write(target,text.getBytes("UTF-8")); }
    @Test public void homeFilesEmptyFoldersPermissionsAndPortableStateRoundTrip() throws Exception {
        Path home=temporary.resolve("source"),cache=temporary.resolve("cache"),destination=temporary.resolve("destination"); Files.createDirectories(home); Files.createDirectories(destination);
        write(home,".config/hometty/config.toml",HomeConfig.DEFAULT); write(home,"notes/file.txt","hello"); Files.createDirectories(home.resolve("empty"));
        write(home,"script","#!/bin/sh\n"); home.resolve("script").toFile().setExecutable(true,true);
        write(home,".local/share/hometty/launcher.properties","uses.app=3\ngrant.plugin=trusted\nwidgets=123\n");
        write(home,".local/share/hometty/test.sealed","device secret"); write(home,".cache/file","cached");
        ByteArrayOutputStream archive=new ByteArrayOutputStream(); HomeArchive.create(home.toFile(),archive,null);
        HomeArchive.Staged staged=HomeArchive.stage(new ByteArrayInputStream(archive.toByteArray()),cache.toFile(),null);
        write(destination,"notes/file.txt","old"); HomeArchive.restore(staged,destination.toFile(),null);
        assertEquals("hello",new String(Files.readAllBytes(destination.resolve("notes/file.txt")),"UTF-8"));
        assertTrue(Files.isDirectory(destination.resolve("empty"))); assertTrue(destination.resolve("script").toFile().canExecute());
        assertFalse(Files.exists(destination.resolve(".local/share/hometty/test.sealed"))); assertFalse(Files.exists(destination.resolve(".cache/file")));
        String state=new String(Files.readAllBytes(destination.resolve(".local/share/hometty/launcher.properties")),"UTF-8"); assertTrue(state.contains("uses.app=3")); assertFalse(state.contains("grant.")); assertFalse(state.contains("widgets"));
        assertEquals("old",new String(Files.readAllBytes(destination.resolve(".cache/hometty/"+staged.directory.getName()+"/rollback/notes/file.txt")),"UTF-8"));
    }
    @Test public void traversalSecretsAndForgedCapabilityStateAreRejected() throws Exception {
        for (String path:new String[]{"home/../escape","home/.local/share/hometty/test.sealed","home/.local/share/hometty/launcher.properties.bak","home/.config/hometty/config.toml.bak","home/.local/share/hometty/launcher.properties"}) {
            ByteArrayOutputStream data=new ByteArrayOutputStream();
            try (ZipOutputStream zip=new ZipOutputStream(data)) { zip.putNextEntry(new ZipEntry("hometty-backup.properties")); zip.write("format=1\napplication_id=com.hazemtech.termux.hometty\n".getBytes("UTF-8")); zip.closeEntry(); zip.putNextEntry(new ZipEntry(path)); zip.write("grant.bad=trusted\n".getBytes("UTF-8")); zip.closeEntry(); }
            try { HomeArchive.stage(new ByteArrayInputStream(data.toByteArray()),temporary.resolve("cache").toFile(),null); fail(path); } catch (IOException expected) {}
        }
    }
    @Test public void existingSymlinksCannotRedirectAReviewedRestore() throws Exception {
        Path home=temporary.resolve("source"),destination=temporary.resolve("destination"),outside=temporary.resolve("outside"); Files.createDirectories(home); Files.createDirectories(destination); Files.createDirectories(outside);
        write(home,"notes/file.txt","new"); write(outside,"file.txt","outside");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream(); HomeArchive.create(home.toFile(),bytes,null);
        HomeArchive.Staged staged=HomeArchive.stage(new ByteArrayInputStream(bytes.toByteArray()),temporary.resolve("cache").toFile(),null);
        Files.createSymbolicLink(destination.resolve("notes"),outside);
        try { HomeArchive.restore(staged,destination.toFile(),null); fail(); } catch (IOException expected) {}
        assertEquals("outside",new String(Files.readAllBytes(outside.resolve("file.txt")),"UTF-8"));
    }
    @Test public void interruptedRestoreRollsBackReplacedFiles() throws Exception {
        Path home=temporary.resolve("source"),destination=temporary.resolve("destination"); Files.createDirectories(home); Files.createDirectories(destination);
        for (String name:new String[]{"a","b"}) { write(home,name,"new"); write(destination,name,"old"); }
        ByteArrayOutputStream bytes=new ByteArrayOutputStream(); HomeArchive.create(home.toFile(),bytes,null);
        HomeArchive.Staged staged=HomeArchive.stage(new ByteArrayInputStream(bytes.toByteArray()),temporary.resolve("cache").toFile(),null);
        try { HomeArchive.restore(staged,destination.toFile(),(size,count) -> Thread.currentThread().interrupt()); fail(); }
        catch (InterruptedIOException expected) { assertTrue(Thread.interrupted()); }
        finally { Thread.interrupted(); }
        for (String name:new String[]{"a","b"}) assertEquals("old",new String(Files.readAllBytes(destination.resolve(name)),"UTF-8"));
    }
    @Test public void journalRecoversAfterAProducerDisappearsMidRestore() throws Exception {
        Path home=temporary.resolve("source"),destination=temporary.resolve("destination"); Files.createDirectories(home); Files.createDirectories(destination);
        for (String name:new String[]{"a","b"}) { write(home,name,"new"); write(destination,name,"old"); }
        ByteArrayOutputStream bytes=new ByteArrayOutputStream(); HomeArchive.create(home.toFile(),bytes,null);
        HomeArchive.Staged staged=HomeArchive.stage(new ByteArrayInputStream(bytes.toByteArray()),temporary.resolve("cache").toFile(),null);
        try { HomeArchive.restore(staged,destination.toFile(),(size,count) -> { throw new IllegalStateException("simulated process loss"); }); fail(); } catch (IllegalStateException expected) {}
        assertTrue(HomeArchive.needsRecovery(destination.toFile())); HomeArchive.recover(destination.toFile()); assertFalse(HomeArchive.needsRecovery(destination.toFile()));
        for (String name:new String[]{"a","b"}) assertEquals("old",new String(Files.readAllBytes(destination.resolve(name)),"UTF-8"));
    }
}
