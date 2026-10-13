package com.termux.app.hometty;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.provider.Settings;
import androidx.core.content.FileProvider;
import java.io.*;
import java.util.Arrays;
import java.util.HashSet;

/** Installs bundled, matching-signer HomeTTY companions only through Android consent. */
public final class CompanionInstaller {
    public static final int REQUEST_INSTALL = 18001;
    public static final int REQUEST_SOURCE = 18002;
    public static final String[] NAMES = {"api", "widget", "tasker", "x11"};

    private static String packageName(Activity activity, String name) {
        if (!Arrays.asList(NAMES).contains(name)) throw new IllegalArgumentException("Unknown companion");
        return activity.getPackageName() + "." + name;
    }

    public static boolean bundled(Activity activity, String name) {
        packageName(activity, name);
        try { return Arrays.asList(activity.getAssets().list("companions")).contains(name + ".apk"); }
        catch (IOException e) { return false; }
    }

    @SuppressWarnings("deprecation")
    private static boolean matchingSigner(PackageInfo owner, PackageInfo companion) {
        if (owner.signatures == null || companion.signatures == null || owner.signatures.length == 0) return false;
        return new HashSet<Signature>(Arrays.asList(owner.signatures))
            .equals(new HashSet<Signature>(Arrays.asList(companion.signatures)));
    }

    @SuppressWarnings("deprecation")
    public static boolean installed(Activity activity, String name) {
        try {
            PackageManager manager = activity.getPackageManager();
            PackageInfo companion = manager.getPackageInfo(packageName(activity, name), PackageManager.GET_SIGNATURES);
            PackageInfo owner = manager.getPackageInfo(activity.getPackageName(), PackageManager.GET_SIGNATURES);
            return matchingSigner(owner, companion);
        } catch (PackageManager.NameNotFoundException e) { return false; }
    }

    @SuppressWarnings("deprecation")
    public static void request(Activity activity, String name) throws IOException, PackageManager.NameNotFoundException {
        String expectedPackage = packageName(activity, name);
        if (android.os.Build.VERSION.SDK_INT >= 26 && !activity.getPackageManager().canRequestPackageInstalls()) {
            activity.startActivityForResult(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + activity.getPackageName())), REQUEST_SOURCE);
            return;
        }
        File directory = new File(activity.getFilesDir(), "hometty-companions");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot stage companion");
        File target = new File(directory, name + ".apk");
        android.util.AtomicFile file = new android.util.AtomicFile(target);
        FileOutputStream out = null;
        try (InputStream in = activity.getAssets().open("companions/" + name + ".apk")) {
            out = file.startWrite(); byte[] buffer = new byte[8192]; int count; long size = 0;
            while ((count = in.read(buffer)) != -1) {
                size += count; if (size > 100 * 1024 * 1024) throw new IOException("Companion exceeds size limit");
                out.write(buffer, 0, count);
            }
            file.finishWrite(out); out = null;
        } catch (IOException e) { if (out != null) file.failWrite(out); throw e; }
        PackageManager manager = activity.getPackageManager();
        PackageInfo archive = manager.getPackageArchiveInfo(target.getAbsolutePath(), PackageManager.GET_SIGNATURES);
        PackageInfo owner = manager.getPackageInfo(activity.getPackageName(), PackageManager.GET_SIGNATURES);
        if (archive == null || !expectedPackage.equals(archive.packageName)
            || !activity.getPackageName().equals(archive.sharedUserId) || !matchingSigner(owner, archive)) {
            throw new SecurityException("Companion identity or signer does not match HomeTTY");
        }
        Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".companions", target);
        activity.startActivityForResult(new Intent(Intent.ACTION_INSTALL_PACKAGE).setData(uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .putExtra(Intent.EXTRA_RETURN_RESULT, true), REQUEST_INSTALL);
    }
}
