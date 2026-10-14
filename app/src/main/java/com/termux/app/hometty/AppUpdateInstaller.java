package com.termux.app.hometty;
import android.app.Activity;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import androidx.core.content.FileProvider;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;

/** Stages a user-selected update without executing it; Android provides final consent. */
public final class AppUpdateInstaller {
    public static final int REQUEST_SOURCE=19002, REQUEST_INSTALL=19001;
    private static final long LIMIT=200L*1024*1024;
    public static File stage(Context context,InputStream source) throws Exception {
        File directory=new File(context.getFilesDir(),"hometty-companions");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot stage update");
        File temporary=File.createTempFile("update-", ".apk",directory);
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256"); long size=0;
            try (FileOutputStream output=new FileOutputStream(temporary)) {
                byte[] buffer=new byte[32768]; int count;
                while ((count=source.read(buffer))!=-1) { size+=count; if (size>LIMIT) throw new IOException("Update exceeds 200 MiB"); output.write(buffer,0,count); digest.update(buffer,0,count); }
                output.getFD().sync();
            }
            inspect(context,temporary);
            StringBuilder hex=new StringBuilder(); for (byte value:digest.digest()) hex.append(String.format(Locale.ROOT,"%02x",value&255));
            File target=new File(directory,"app-update-"+hex+".apk");
            if (target.exists()) { verify(context,target); }
            else if (!temporary.renameTo(target)) throw new IOException("Cannot commit update file");
            return target;
        } finally { temporary.delete(); }
    }
    @SuppressWarnings("deprecation") public static PackageInfo inspect(Context context,File file) throws Exception {
        if (!file.isFile() || file.length()==0 || file.length()>LIMIT) throw new IOException("Invalid update file size");
        PackageManager manager=context.getPackageManager();
        PackageInfo archive=manager.getPackageArchiveInfo(file.getAbsolutePath(),PackageManager.GET_SIGNATURES);
        PackageInfo installed=manager.getPackageInfo(context.getPackageName(),PackageManager.GET_SIGNATURES);
        if (archive==null || !context.getPackageName().equals(archive.packageName) || !Objects.equals(installed.sharedUserId,archive.sharedUserId)) throw new SecurityException("This APK is not a compatible HomeTTY application update");
        if (archive.signatures==null || installed.signatures==null || !new HashSet<Signature>(Arrays.asList(installed.signatures)).equals(new HashSet<Signature>(Arrays.asList(archive.signatures)))) throw new SecurityException("Update signing certificate does not match installed HomeTTY");
        if (version(archive)<=version(installed)) throw new SecurityException("Select a newer HomeTTY version; downgrades and same-version replacements are refused");
        return archive;
    }
    private static long version(PackageInfo info) { return Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode; }
    public static PackageInfo verify(Context context,File file) throws Exception {
        File directory=new File(context.getFilesDir(),"hometty-companions").getCanonicalFile();
        if (!directory.equals(file.getCanonicalFile().getParentFile()) || !file.getName().matches("app-update-[a-f0-9]{64}\\.apk")) throw new SecurityException("Invalid staged update path");
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try (InputStream input=new FileInputStream(file)) { byte[] buffer=new byte[32768]; int count; long size=0; while ((count=input.read(buffer))!=-1) { size+=count; if (size>LIMIT) throw new IOException("Update exceeds size limit"); digest.update(buffer,0,count); } }
        StringBuilder hex=new StringBuilder(); for (byte value:digest.digest()) hex.append(String.format(Locale.ROOT,"%02x",value&255));
        if (!file.getName().equals("app-update-"+hex+".apk")) throw new SecurityException("Staged update changed after review");
        return inspect(context,file);
    }
    public static boolean request(Activity activity,File file) throws Exception {
        verify(activity,file);
        if (Build.VERSION.SDK_INT>=26 && !activity.getPackageManager().canRequestPackageInstalls()) {
            activity.startActivityForResult(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+activity.getPackageName())),REQUEST_SOURCE); return false;
        }
        Uri uri=FileProvider.getUriForFile(activity,activity.getPackageName()+".companions",file);
        activity.startActivityForResult(new Intent(Intent.ACTION_INSTALL_PACKAGE).setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).putExtra(Intent.EXTRA_RETURN_RESULT,true),REQUEST_INSTALL);
        return true;
    }
}
