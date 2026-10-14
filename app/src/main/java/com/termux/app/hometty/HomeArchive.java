package com.termux.app.hometty;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Portable Home files. Device secrets, grants and caches are deliberately nonportable. */
public final class HomeArchive {
    private static final String APP="com.hazemtech.termux.hometty";
    private static final long MAX_BYTES=8L*1024*1024*1024;
    private static final int MAX_ENTRIES=100000;
    public interface Progress { void update(long bytes,int files); }
    public static final class Staged {
        public final File directory;
        public final List<String> files;
        public final List<String> directories;
        public final long bytes;
        private Staged(File directory,List<String> files,List<String> directories,long bytes) { this.directory=directory; this.files=Collections.unmodifiableList(files); this.directories=Collections.unmodifiableList(directories); this.bytes=bytes; }
    }
    private static boolean excluded(String path) {
        if (path.equals(".cache") || path.startsWith(".cache/")) return true;
        if (path.startsWith(".config/hometty/config.toml.") && (path.endsWith(".bak") || path.endsWith(".new"))) return true;
        if (path.startsWith(".local/share/hometty/")) {
            String name=path.substring(".local/share/hometty/".length());
            return name.startsWith("password-attempts") || name.contains(".sealed") || name.startsWith("restore-journal") || name.startsWith("launcher.properties.");
        }
        return false;
    }
    private static String validPath(String path) throws IOException {
        if (path.isEmpty() || path.length()>4096 || path.startsWith("/") || path.indexOf('\\')>=0 || path.indexOf('\0')>=0) throw new IOException("Invalid backup path");
        for (String part:path.split("/",-1)) if (part.isEmpty() || part.equals(".") || part.equals("..")) throw new IOException("Invalid backup path");
        if (excluded(path)) throw new IOException("Backup contains nonportable secrets or cache state");
        return path;
    }
    private static void interrupted() throws IOException { if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Backup operation interrupted"); }
    private static long copy(InputStream input,OutputStream output,long remaining) throws IOException {
        byte[] buffer=new byte[32768]; long size=0; int count;
        while ((count=input.read(buffer))!=-1) { interrupted(); size+=count; if (size>remaining) throw new IOException("Home backup exceeds the 8 GiB limit"); output.write(buffer,0,count); }
        return size;
    }
    public static void create(File home,OutputStream destination,Progress progress) throws IOException {
        File root=home.getCanonicalFile(); List<File> pending=new ArrayList<>(); pending.add(root); long bytes=0; int files=0,links=0,entries=0;
        try (ZipOutputStream archive=new ZipOutputStream(destination)) {
            while (!pending.isEmpty()) {
                interrupted(); File file=pending.remove(pending.size()-1);
                String path=root.toURI().relativize(file.toURI()).getPath(); if (path.endsWith("/")) path=path.substring(0,path.length()-1);
                if (!path.isEmpty() && excluded(path)) continue;
                if (!file.getCanonicalPath().equals(file.getAbsolutePath())) { links++; continue; }
                if (file.isDirectory()) {
                    if (!path.isEmpty()) { if (++entries>=MAX_ENTRIES) throw new IOException("Too many Home entries"); validPath(path); archive.putNextEntry(new ZipEntry("home/"+path+"/")); archive.closeEntry(); }
                    File[] children=file.listFiles(); if (children==null) throw new IOException("Cannot read backup folder");
                    if (pending.size()+children.length+files>MAX_ENTRIES) throw new IOException("Too many Home files"); Collections.addAll(pending,children); continue;
                }
                if (!file.isFile()) { links++; continue; }
                validPath(path); if (++entries>=MAX_ENTRIES) throw new IOException("Too many Home files"); files++;
                ZipEntry entry=new ZipEntry("home/"+path); entry.setTime(file.lastModified());
                entry.setExtra(new byte[]{0x54,0x48,1,0,(byte)(file.canExecute()?1:0)}); archive.putNextEntry(entry);
                if (path.equals(".local/share/hometty/launcher.properties")) {
                    if (file.length()>1048576) throw new IOException("Launcher state too large");
                    Properties original=new Properties(),portable=new Properties(); try (InputStream input=new FileInputStream(file)) { original.load(input); }
                    for (String key:original.stringPropertyNames()) if (key.startsWith("uses.") || key.startsWith("label.") || key.startsWith("pin.")) portable.setProperty(key,original.getProperty(key));
                    ByteArrayOutputStream data=new ByteArrayOutputStream(); portable.store(data,"Portable HomeTTY usage and legacy labels/pins; grants excluded");
                    byte[] payload=data.toByteArray(); bytes+=payload.length; if (bytes>MAX_BYTES) throw new IOException("Backup too large"); archive.write(payload);
                } else try (InputStream input=new FileInputStream(file)) { bytes+=copy(input,archive,MAX_BYTES-bytes); }
                archive.closeEntry(); if (progress!=null) progress.update(bytes,files);
            }
            Properties manifest=new Properties(); manifest.setProperty("format","1"); manifest.setProperty("application_id",APP); manifest.setProperty("skipped_links",Integer.toString(links));
            archive.putNextEntry(new ZipEntry("hometty-backup.properties")); manifest.store(archive,"HomeTTY portable Home backup"); archive.closeEntry();
        }
    }
    private static boolean executable(ZipEntry entry) throws IOException {
        byte[] extra=entry.getExtra(); if (extra==null) return false;
        for (int position=0;position+4<=extra.length;) {
            int id=(extra[position]&255)|((extra[position+1]&255)<<8), length=(extra[position+2]&255)|((extra[position+3]&255)<<8); position+=4;
            if (position+length>extra.length) throw new IOException("Malformed backup metadata");
            if (id==0x4854) { if (length!=1 || (extra[position]!=0 && extra[position]!=1)) throw new IOException("Invalid executable flag"); return extra[position]==1; }
            position+=length;
        }
        return false;
    }
    public static Staged stage(InputStream source,File cache,Progress progress) throws IOException {
        File directory=new File(cache,"restore-"+UUID.randomUUID()); if (!directory.mkdirs()) throw new IOException("Cannot stage restore");
        List<String> files=new ArrayList<>(),directories=new ArrayList<>(); Set<String> seen=new HashSet<>(); Properties manifest=null; long bytes=0; int entries=0;
        try (ZipInputStream archive=new ZipInputStream(source)) {
            ZipEntry entry;
            while ((entry=archive.getNextEntry())!=null) {
                interrupted(); if (++entries>MAX_ENTRIES || !seen.add(entry.getName())) throw new IOException("Too many or duplicate backup entries");
                if (entry.getName().equals("hometty-backup.properties")) {
                    ByteArrayOutputStream data=new ByteArrayOutputStream(); copy(archive,data,8192); manifest=new Properties(); manifest.load(new ByteArrayInputStream(data.toByteArray()));
                } else {
                    if (!entry.getName().startsWith("home/")) throw new IOException("Unknown backup entry");
                    String path=entry.getName().substring(5); if (entry.isDirectory() && path.endsWith("/")) path=path.substring(0,path.length()-1); validPath(path);
                    File target=HomeFiles.contained(directory,new File(directory,"payload/"+path));
                    if (entry.isDirectory()) { if (!target.isDirectory() && !target.mkdirs()) throw new IOException("Cannot stage directory"); directories.add(path); }
                    else {
                        if (!target.getParentFile().isDirectory() && !target.getParentFile().mkdirs()) throw new IOException("Cannot stage file parent");
                        if (target.exists()) throw new IOException("Conflicting backup paths");
                        try (FileOutputStream output=new FileOutputStream(target)) { bytes+=copy(archive,output,MAX_BYTES-bytes); output.getFD().sync(); }
                        target.setExecutable(executable(entry),true); if (entry.getTime()>0) target.setLastModified(entry.getTime()); files.add(path);
                        if (progress!=null) progress.update(bytes,files.size());
                    }
                }
                archive.closeEntry();
            }
            if (manifest==null || !"1".equals(manifest.getProperty("format")) || !APP.equals(manifest.getProperty("application_id"))) throw new IOException("Not a HomeTTY Home backup");
            File config=new File(directory,"payload/.config/hometty/config.toml");
            if (config.exists()) { if (config.length()>262144) throw new IOException("Configuration too large"); try (InputStream input=new FileInputStream(config); ByteArrayOutputStream data=new ByteArrayOutputStream()) { copy(input,data,262144); HomeConfig.parse(new String(data.toByteArray(),StandardCharsets.UTF_8)); } }
            File launcher=new File(directory,"payload/.local/share/hometty/launcher.properties");
            if (launcher.exists()) { if (launcher.length()>1048576) throw new IOException("Launcher state too large"); Properties values=new Properties(); try (InputStream input=new FileInputStream(launcher)) { values.load(input); } for (String key:values.stringPropertyNames()) if (!key.startsWith("uses.") && !key.startsWith("label.") && !key.startsWith("pin.")) throw new IOException("Backup contains device-bound launcher grants"); }
            return new Staged(directory,files,directories,bytes);
        } catch (RuntimeException error) { throw new IOException("Invalid backup configuration",error); }
    }
    /** Merge reviewed files; existing files are retained in the staging rollback folder. */
    public static void restore(Staged staged,File home,Progress progress) throws IOException {
        File root=home.getCanonicalFile(); List<String> changed=new ArrayList<>(); Set<String> old=new HashSet<>(); long bytes=0;
        // Complete preflight before modifying Home.
        for (String path:staged.directories) { validPath(path); File target=HomeFiles.contained(root,new File(root,path)); if (target.exists() && !target.isDirectory()) throw new IOException("A directory would replace an existing file"); }
        for (String path:staged.files) { validPath(path); File target=HomeFiles.contained(root,new File(root,path)); if (target.isDirectory()) throw new IOException("A file would replace an existing directory"); }
        try {
            for (String path:staged.directories) { File target=HomeFiles.contained(root,new File(root,path)); if (!target.isDirectory() && !target.mkdirs()) throw new IOException("Cannot restore directory"); }
            for (String path:staged.files) {
                interrupted(); File source=HomeFiles.contained(staged.directory,new File(staged.directory,"payload/"+path));
                File target=HomeFiles.contained(root,new File(root,path));
                if (target.exists()) { File previous=new File(staged.directory,"rollback/"+path); copyFile(target,previous); old.add(path); }
                changed.add(path); copyFile(source,target); bytes+=source.length(); if (progress!=null) progress.update(bytes,changed.size());
            }
        } catch (IOException failure) {
            boolean wasInterrupted=Thread.interrupted();
            Collections.reverse(changed);
            for (String path:changed) try {
                File target=HomeFiles.contained(root,new File(root,path));
                if (old.contains(path)) copyFile(new File(staged.directory,"rollback/"+path),target);
                else if (target.exists() && !target.delete()) throw new IOException("Cannot remove partial restored file");
            } catch (IOException rollback) { failure.addSuppressed(rollback); }
            if (wasInterrupted) Thread.currentThread().interrupt();
            throw failure;
        }
    }
    private static void copyFile(File source,File target) throws IOException {
        if (!target.getParentFile().isDirectory() && !target.getParentFile().mkdirs()) throw new IOException("Cannot create restore folder");
        File temporary=File.createTempFile(".hometty-restore-", ".tmp",target.getParentFile());
        try {
            try (InputStream input=new FileInputStream(source); FileOutputStream output=new FileOutputStream(temporary)) { copy(input,output,MAX_BYTES); output.getFD().sync(); }
            temporary.setExecutable(source.canExecute(),true); temporary.setLastModified(source.lastModified());
            if (!temporary.renameTo(target)) throw new IOException("Cannot commit restored file");
        } finally { temporary.delete(); }
    }
}
