package com.termux.app.hometty;
import java.io.*;
import java.util.*;
/** Exact, bounded manifest snapshot; execution always revalidates content and grants. */
public final class LocalPlugin {
    public final File file;
    public final PluginManifest manifest;
    public final String digest;
    public LocalPlugin(File file) throws IOException {
        this.file=HomeFiles.contained(HomeConfig.HOME,file);
        File directory=new File(HomeConfig.HOME,".config/hometty/plugins");
        if (!this.file.getParentFile().equals(directory)) throw new IOException("Plugin must be in the HomeTTY configuration directory");
        String source=BoundedFiles.read(this.file,262144); manifest=new PluginManifest(source); digest=PluginManifest.digest(source);
    }
    public static List<LocalPlugin> list() {
        File[] files=new File(HomeConfig.HOME,".config/hometty/plugins").listFiles((directory,name)->name.endsWith(".toml"));
        List<LocalPlugin> result=new ArrayList<>(); if (files==null) return result; Arrays.sort(files); int count=0;
        for (File file:files) { if (++count>200) break; try { result.add(new LocalPlugin(file)); } catch (Exception invalid) { /* Invalid manifests never create active surfaces. */ } }
        return result;
    }
    public boolean granted(LauncherState state) { return digest.equals(state.get("grant."+manifest.id,"")); }
}
