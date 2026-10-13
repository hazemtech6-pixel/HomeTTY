package com.termux.app.hometty;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import java.io.*;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/** Ciphertext on disk, non-exportable encryption key in Android Keystore. */
public final class SecretStore {
    private static final String ALIAS = "hometty.secrets.v1";
    private static javax.crypto.SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (!store.containsAlias(ALIAS)) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            generator.generateKey();
        }
        return (javax.crypto.SecretKey) store.getKey(ALIAS, null);
    }
    private static AtomicFile file(String name) {
        if (!name.matches("[a-z0-9_-]{1,64}")) throw new IllegalArgumentException("Invalid secret name");
        return new AtomicFile(new File(HomeConfig.DATA, name + ".sealed"));
    }
    public static synchronized void put(String name, byte[] secret) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key());
        AtomicFile file = file(name); FileOutputStream raw = null;
        try {
            raw = file.startWrite(); DataOutputStream out = new DataOutputStream(raw);
            byte[] iv = cipher.getIV(); out.writeInt(iv.length); out.write(iv); out.write(cipher.doFinal(secret)); out.flush(); file.finishWrite(raw);
        } catch (Exception e) { if (raw != null) file.failWrite(raw); throw e; }
    }
    public static synchronized byte[] get(String name) throws Exception {
        try (DataInputStream in = new DataInputStream(file(name).openRead())) {
            int n = in.readInt(); if (n != 12) throw new IOException("Invalid secret envelope");
            byte[] iv = new byte[n]; in.readFully(iv); ByteArrayOutputStream data = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024]; while ((n = in.read(buffer)) != -1) { if (data.size() + n > 65536) throw new IOException("Secret too large"); data.write(buffer, 0, n); }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            return cipher.doFinal(data.toByteArray());
        }
    }
}
