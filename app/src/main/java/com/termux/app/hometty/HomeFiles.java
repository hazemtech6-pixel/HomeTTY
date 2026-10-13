package com.termux.app.hometty;

import java.io.*;

/** Private-home file operations. No recursive deletion or implicit overwrite. */
public final class HomeFiles {
    public static File contained(File home, File entry) throws IOException {
        File root=home.getCanonicalFile(), file=entry.getCanonicalFile();
        if (!file.equals(root) && !file.getPath().startsWith(root.getPath()+File.separator)) throw new IOException("Path is outside Home");
        if (!file.getPath().equals(entry.getAbsolutePath())) throw new IOException("Use the target path instead of a symlink");
        return file;
    }
    public static String validName(String name) throws IOException {
        name=name.trim();
        if (name.isEmpty() || name.equals(".") || name.equals("..") || name.indexOf('/')>=0 || name.indexOf('\\')>=0 || name.indexOf('\0')>=0) throw new IOException("Enter a single file or folder name");
        return name;
    }
    public static File child(File home,File directory,String name) throws IOException {
        name=validName(name);
        File parent=contained(home,directory); if (!parent.isDirectory()) throw new IOException("Parent is not a folder");
        return contained(home,new File(parent,name));
    }
    public static void create(File home,File directory,String name,boolean folder) throws IOException {
        File entry=child(home,directory,name);
        if (entry.exists() || !(folder?entry.mkdir():entry.createNewFile())) throw new IOException("Name already exists or cannot be created");
    }
    public static void rename(File home,File entry,String name) throws IOException {
        File original=contained(home,entry); if (original.equals(home.getCanonicalFile())) throw new IOException("Cannot rename Home");
        File target=child(home,original.getParentFile(),name);
        if (target.exists() || !original.renameTo(target)) throw new IOException("Destination exists or rename failed");
    }
    public static void delete(File home,File entry) throws IOException {
        File file=contained(home,entry); if (file.equals(home.getCanonicalFile())) throw new IOException("Cannot delete Home");
        if (!file.delete()) throw new IOException("Delete failed; folders must be empty");
    }
}
