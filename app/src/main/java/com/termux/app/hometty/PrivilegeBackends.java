package com.termux.app.hometty;

import android.content.Context;
import android.content.pm.*;
import java.io.File;
import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuProvider;

/** Capability detection deliberately does not whitelist a manager package name. */
public final class PrivilegeBackends {
    public enum Kind { ANDROID, SHIZUKU, ROOT }
    public static String describe(Context context) {
        StringBuilder text=new StringBuilder("Android: ordinary app permissions\n");
        try {
            PermissionInfo permission=context.getPackageManager().getPermissionInfo(ShizukuProvider.PERMISSION,0);
            ApplicationInfo owner=context.getPackageManager().getApplicationInfo(permission.packageName,0);
            text.append("Compatible provider: ").append(owner.loadLabel(context.getPackageManager())).append(" (declares ").append(permission.name).append(")\n");
        } catch (PackageManager.NameNotFoundException e) { text.append("No installed owner of the Shizuku API permission\n"); }
        try {
            if (!Shizuku.pingBinder()) text.append("Shizuku protocol: no live binder\n");
            else if (Shizuku.isPreV11()) text.append("Shizuku protocol: provider too old\n");
            else {
                boolean granted=Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED;
                text.append("Shizuku protocol: live, ").append(granted ? "authorized" : "authorization required").append("\n");
                if (granted) text.append("Provider UID: ").append(Shizuku.getUid()).append("\n");
            }
        } catch (RuntimeException e) { text.append("Provider unavailable: ").append(e.getMessage()).append("\n"); }
        text.append("Root executable: ").append(rootExecutable()==null ? "not found" : "available; user approval still required");
        return text.toString();
    }
    public static void requestShizuku() {
        if (!Shizuku.pingBinder() || Shizuku.isPreV11()) throw new IllegalStateException("Start a compatible Shizuku provider first");
        if (Shizuku.checkSelfPermission()!=PackageManager.PERMISSION_GRANTED) Shizuku.requestPermission(1200);
    }
    public static String rootExecutable() {
        for (String path:new String[]{"/system/bin/su","/system/xbin/su","/sbin/su","/su/bin/su"}) if (new File(path).canExecute()) return path;
        return null;
    }
    public static String rootCommand(String command) {
        String executable=rootExecutable(); if (executable==null) throw new IllegalStateException("No root executable available");
        return executable+" -c "+quote(command);
    }
    public static String quote(String argument) { return "'"+argument.replace("'","'\\''")+"'"; }
}
