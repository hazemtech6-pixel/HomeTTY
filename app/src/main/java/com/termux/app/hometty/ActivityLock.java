package com.termux.app.hometty;

import android.app.*;
import android.content.Intent;
import android.view.*;
import android.widget.Toast;
import java.util.WeakHashMap;

/** Device-credential gate for direct activity entry, including shortcuts and notifications. */
public final class ActivityLock {
    public static final int REQUEST = 18940;
    private static final WeakHashMap<Activity,Boolean> unlocked=new WeakHashMap<>();
    private static final WeakHashMap<Activity,Boolean> pending=new WeakHashMap<>();
    public static void authorized(Activity activity) { unlocked.put(activity, true); activity.findViewById(android.R.id.content).setVisibility(View.VISIBLE); }
    public static boolean enabled(String key) {
        try { return new HomeConfig().flag(key,false); } catch (Exception e) { return true; }
    }
    public static void resume(Activity activity,String key) {
        if (!enabled(key) || Boolean.TRUE.equals(unlocked.get(activity))) return;
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        activity.findViewById(android.R.id.content).setVisibility(View.INVISIBLE);
        if (Boolean.TRUE.equals(pending.get(activity))) return;
        if (HomePassword.configured()) {
            pending.put(activity, true);
            HomePassword.prompt(activity, false, () -> {
                pending.remove(activity); unlocked.put(activity, true); activity.findViewById(android.R.id.content).setVisibility(View.VISIBLE);
            }, () -> { pending.remove(activity); activity.finish(); });
            return;
        }
        KeyguardManager manager=activity.getSystemService(KeyguardManager.class);
        Intent credential=manager.createConfirmDeviceCredentialIntent("Unlock HomeTTY","Confirm your device credential");
        if (credential==null) { Toast.makeText(activity,"An Android screen lock is required",Toast.LENGTH_LONG).show(); activity.finish(); return; }
        pending.put(activity,true);
        try { activity.startActivityForResult(credential,REQUEST); } catch (RuntimeException e) { pending.remove(activity); activity.finish(); }
    }
    public static void stop(Activity activity) {
        HomePassword.stop(activity);
        if (!Boolean.TRUE.equals(pending.get(activity))) { unlocked.remove(activity); }
    }
    public static boolean result(Activity activity,int request,int result) {
        if (request!=REQUEST) return false;
        pending.remove(activity);
        if (result==Activity.RESULT_OK) { unlocked.put(activity,true); activity.findViewById(android.R.id.content).setVisibility(View.VISIBLE); }
        else activity.finish();
        return true;
    }
}
