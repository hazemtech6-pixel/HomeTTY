package com.termux.app.hometty;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Bounded local index; callers build it off the Android main thread. */
public final class SearchIndex {
    public static final class Entry {
        public final String kind, title, target, command;
        public Entry(String kind, String title, String target, String command) { this.kind=kind; this.title=title; this.target=target; this.command=command; }
        public String id() { return kind + ":" + target; }
    }
    public static List<Entry> build(File home, File prefix) throws IOException {
        List<Entry> result = new ArrayList<>(); Set<String> desktopIds = new HashSet<>();
        for (File directory : new File[]{new File(home, ".local/share/applications"), new File(prefix, "share/applications")}) {
            File[] entries = directory.listFiles((dir,name) -> name.endsWith(".desktop")); if (entries == null) continue;
            Arrays.sort(entries);
            int count = 0;
            for (File entry : entries) {
                if (++count > 500 || Thread.currentThread().isInterrupted()) break;
                try {
                    if (!entry.getCanonicalPath().startsWith(directory.getCanonicalPath() + "/") || entry.length() > 65536 || desktopIds.contains(entry.getName())) continue;
                    StringBuilder source = new StringBuilder();
                    try (Reader reader = new InputStreamReader(new FileInputStream(entry), StandardCharsets.UTF_8)) {
                        char[] buffer = new char[4096]; int n; while ((n=reader.read(buffer))!=-1) { if (source.length()+n>65536) throw new IOException("Desktop entry too large"); source.append(buffer,0,n); }
                    }
                    DesktopEntry application = DesktopEntry.parse(source.toString(), entry.getAbsolutePath());
                    // User entries, including Hidden=true, override matching system entries.
                    desktopIds.add(entry.getName());
                    if (application != null) result.add(new Entry("desktop", application.name, entry.getAbsolutePath(), application.command));
                } catch (IOException | IllegalArgumentException ignored) { /* Invalid entries are never executable search results. */ }
            }
        }
        File[] commands = new File(prefix, "bin").listFiles();
        if (commands != null) { Arrays.sort(commands); int count=0; for (File command:commands) { if (++count>3000) break; if (command.isFile() && command.canExecute()) result.add(new Entry("command",command.getName(),command.getAbsolutePath(),"exec " + DesktopEntry.quote(command.getAbsolutePath()))); } }
        File pluginDirectory=new File(home,".config/hometty/plugins"); File[] plugins=pluginDirectory.listFiles((dir,name)->name.endsWith(".toml"));
        if (plugins!=null) { Arrays.sort(plugins); int count=0; for (File file:plugins) {
            if (++count>200) break;
            try { HomeFiles.contained(home,file); PluginManifest manifest=new PluginManifest(BoundedFiles.read(file,262144)); if ("command".equals(manifest.surface)) result.add(new Entry("plugin",manifest.name,file.getAbsolutePath(),"")); }
            catch (IOException | RuntimeException invalid) { /* No executable result for invalid manifest. */ }
        } }
        scan(home, home.getCanonicalPath(), 0, result, new int[]{0});
        return Collections.unmodifiableList(result);
    }
    private static void scan(File directory,String home,int depth,List<Entry> entries,int[] count) throws IOException {
        if (depth>6 || count[0]>=5000 || Thread.currentThread().isInterrupted()) return;
        File[] files=directory.listFiles(); if (files==null) return; Arrays.sort(files);
        for (File file:files) {
            if (count[0]>=5000 || Thread.currentThread().isInterrupted()) return;
            if (file.getName().startsWith(".")) continue;
            String path=file.getCanonicalPath();
            if (!path.startsWith(home+"/") || !file.getAbsolutePath().equals(path)) continue;
            count[0]++; entries.add(new Entry("file",path.substring(home.length()+1),path,""));
            if (file.isDirectory()) scan(file,home,depth+1,entries,count);
        }
    }
}
