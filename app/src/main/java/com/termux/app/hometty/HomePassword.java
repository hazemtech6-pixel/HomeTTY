package com.termux.app.hometty;

import android.app.*;
import android.text.InputType;
import android.view.WindowManager;
import android.widget.*;
import android.util.AtomicFile;
import java.io.*;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/** Optional HomeTTY password, encrypted hash record, off-thread slow verification. */
public final class HomePassword {
    private static final java.util.WeakHashMap<Activity, AlertDialog> active = new java.util.WeakHashMap<>();
    private static final java.util.WeakHashMap<Activity, Runnable> canceled = new java.util.WeakHashMap<>();
    public static void stop(Activity activity) {
        AlertDialog dialog = active.remove(activity); Runnable callback = canceled.remove(activity);
        if (dialog != null) { dialog.dismiss(); if (callback != null) callback.run(); }
    }
    private static final String RECORD = "lock_password";
    private static final ExecutorService worker = Executors.newSingleThreadExecutor();
    private static final AtomicFile attempts = new AtomicFile(new File(HomeConfig.DATA, "password-attempts"));
    public static boolean configured() { return SecretStore.contains(RECORD); }
    public static void remove() { SecretStore.delete(RECORD); attempts.delete(); }
    private static synchronized long[] attemptState() throws IOException {
        if (!attempts.getBaseFile().exists()) return new long[]{0,0};
        try (DataInputStream input = new DataInputStream(attempts.openRead())) {
            long failures = input.readLong(), deadline = input.readLong();
            if (failures < 0 || failures > 100 || deadline < 0) throw new IOException("Invalid password attempt state");
            return new long[]{failures, deadline};
        }
    }
    private static synchronized void failed(long previous) throws IOException {
        long failures = Math.min(100, previous + 1);
        long delay = failures < 5 ? 0 : Math.min(300000, 30000L * (1L << Math.min(4, failures - 5)));
        FileOutputStream stream = null;
        try {
            stream = attempts.startWrite(); DataOutputStream output = new DataOutputStream(stream);
            output.writeLong(failures); output.writeLong(System.currentTimeMillis() + delay); output.flush(); attempts.finishWrite(stream);
        } catch (IOException error) { if (stream != null) attempts.failWrite(stream); throw error; }
    }
    public static void prompt(Activity activity, boolean enroll, Runnable success, Runnable cancel) {
        LinearLayout fields = new LinearLayout(activity); fields.setOrientation(LinearLayout.VERTICAL); fields.setPadding(24,16,24,16);
        TextView status = new TextView(activity); status.setText(enroll ? "Use at least six characters. A PIN should have at least six digits." : "Enter your HomeTTY password or PIN."); fields.addView(status);
        EditText first = passwordField(activity, "Password / PIN"); fields.addView(first);
        EditText second = enroll ? passwordField(activity, "Repeat password / PIN") : null; if (second != null) fields.addView(second);
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle(enroll ? "Set HomeTTY password" : "Unlock HomeTTY").setView(fields)
            .setNegativeButton("Cancel", (d,w) -> cancel.run()).setPositiveButton(enroll ? "Save" : "Unlock", null).create();
        active.put(activity, dialog); canceled.put(activity, cancel);
        dialog.setOnDismissListener(d -> { if (active.get(activity) == dialog) { active.remove(activity); canceled.remove(activity); } });
        dialog.setOnCancelListener(d -> cancel.run()); dialog.show();
        dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            char[] password = new char[first.length()]; android.text.TextUtils.getChars(first.getText(), 0, first.length(), password, 0);
            if (enroll && (password.length < 6 || !android.text.TextUtils.equals(first.getText(), second.getText()))) {
                Arrays.fill(password, '\0'); status.setText("Passwords must match and contain at least six characters."); return;
            }
            first.getText().clear(); if (second != null) second.getText().clear();
            dialog.setCancelable(false); dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false); dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false); status.setText("Checking…");
            worker.execute(() -> {
                boolean verified = false; String error = null;
                try {
                    if (enroll) {
                        byte[] record = PasswordHash.create(password).encode();
                        try { SecretStore.put(RECORD, record); attempts.delete(); } finally { Arrays.fill(record, (byte)0); }
                        verified = true;
                    } else {
                        long[] state = attemptState();
                        if (state[1] > System.currentTimeMillis()) error = "Try again in " + ((state[1] - System.currentTimeMillis() + 999) / 1000) + " seconds.";
                        else {
                            byte[] record = SecretStore.get(RECORD);
                            try { verified = PasswordHash.decode(record).verify(password); } finally { Arrays.fill(record, (byte)0); }
                            if (verified) attempts.delete(); else { failed(state[0]); error = "Incorrect password."; }
                        }
                    }
                } catch (Exception exception) { error = "Password check failed: " + exception.getMessage(); }
                finally { Arrays.fill(password, '\0'); }
                final boolean accepted = verified; final String message = error;
                activity.runOnUiThread(() -> {
                    if (active.get(activity) != dialog || activity.isFinishing() || activity.isDestroyed()) { dialog.dismiss(); return; }
                    if (accepted) { dialog.dismiss(); success.run(); }
                    else { status.setText(message); dialog.setCancelable(true); dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true); dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true); }
                });
            });
        });
    }
    private static EditText passwordField(Activity activity, String hint) {
        EditText field = new EditText(activity); field.setHint(hint);
        field.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(128)});
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD); field.setSingleLine(true); return field;
    }
}
