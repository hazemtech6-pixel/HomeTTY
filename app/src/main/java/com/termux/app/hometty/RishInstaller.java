package com.termux.app.hometty;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/** Locally exported provider payloads are staged, reviewed, and never auto-executed. */
final class RishInstaller {
    static File stage(InputStream input, File cache) throws Exception {
        if (!cache.isDirectory() && !cache.mkdirs()) throw new IOException("Cannot create RISH staging directory");
        File temporary=File.createTempFile("rish-", ".dex",cache);
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256"); long size=0;
            try (FileOutputStream output=new FileOutputStream(temporary)) {
                byte[] buffer=new byte[8192]; int count;
                while ((count=input.read(buffer))!=-1) { if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("RISH import canceled"); size+=count; if (size>16*1024*1024) throw new IOException("RISH payload exceeds 16 MiB"); digest.update(buffer,0,count); output.write(buffer,0,count); }
                output.getFD().sync();
            }
            byte[] magic=new byte[8]; try (FileInputStream source=new FileInputStream(temporary)) { if (source.read(magic)!=8 || magic[0]!='d' || magic[1]!='e' || magic[2]!='x' || magic[3]!='\n' || magic[7]!=0) throw new IOException("Select the provider's exported rish_shizuku.dex"); }
            StringBuilder hex=new StringBuilder(); for (byte value:digest.digest()) hex.append(String.format(Locale.ROOT,"%02x",value&255));
            File target=new File(cache,"rish-"+hex+".dex"); if (target.exists()) throw new IOException("This payload is already staged");
            if (!temporary.renameTo(target)) throw new IOException("Cannot stage RISH payload"); return target;
        } finally { temporary.delete(); }
    }
    static File install(File staged,File home) throws IOException {
        if (!staged.getName().matches("rish-[a-f0-9]{64}\\.dex")) throw new IOException("Invalid staged payload");
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256"); long size=0;
            try (InputStream input=new FileInputStream(staged)) { byte[] buffer=new byte[8192]; int count; while ((count=input.read(buffer))!=-1) { size+=count; if (size>16*1024*1024) throw new IOException("RISH payload exceeds limit"); digest.update(buffer,0,count); } }
            StringBuilder hex=new StringBuilder(); for (byte value:digest.digest()) hex.append(String.format(Locale.ROOT,"%02x",value&255));
            if (!staged.getName().equals("rish-"+hex+".dex")) throw new IOException("Staged RISH payload changed; import it again");
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IOException(impossible); }
        File directory=HomeFiles.contained(home,new File(home,".local/share/hometty/rish"));
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create RISH directory");
        File destination=HomeFiles.contained(home,new File(directory,staged.getName()));
        if (!destination.exists()) {
            File temporary=File.createTempFile(".rish-", ".dex",directory);
            try {
                try (InputStream input=new FileInputStream(staged); FileOutputStream output=new FileOutputStream(temporary)) { byte[] buffer=new byte[8192]; int count; while ((count=input.read(buffer))!=-1) output.write(buffer,0,count); output.getFD().sync(); }
                if (!temporary.setReadOnly()) throw new IOException("Cannot make RISH DEX read-only");
                if (!temporary.renameTo(destination)) throw new IOException("Cannot install RISH payload");
            } finally { temporary.delete(); }
        }
        String command="#!/system/bin/sh\nexport RISH_APPLICATION_ID=com.hazemtech.termux.hometty\nexec /system/bin/app_process -Djava.class.path="+PrivilegeBackends.quote(destination.getAbsolutePath())+" /system/bin --nice-name=rish rikka.shizuku.shell.ShizukuShellLoader \"$@\"\n";
        File launcher=HomeFiles.contained(home,new File(directory,"rish")),temporary=File.createTempFile(".launcher-", ".tmp",directory);
        try { try (FileOutputStream output=new FileOutputStream(temporary)) { output.write(command.getBytes(StandardCharsets.UTF_8)); output.getFD().sync(); } if (!temporary.setExecutable(true,true) || !temporary.renameTo(launcher)) throw new IOException("Cannot install RISH launcher"); } finally { temporary.delete(); }
        return launcher;
    }
    static String command(File home) throws IOException {
        File launcher=HomeFiles.contained(home,new File(home,".local/share/hometty/rish/rish"));
        if (!launcher.isFile()) throw new IOException("Import the provider's exported RISH payload first");
        return "/system/bin/sh "+PrivilegeBackends.quote(launcher.getAbsolutePath());
    }
}
