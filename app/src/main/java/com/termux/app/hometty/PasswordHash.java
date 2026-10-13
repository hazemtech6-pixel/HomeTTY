package com.termux.app.hometty;

import java.security.*;
import java.util.Arrays;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** PBKDF2-HMAC-SHA256 with per-password random salts; never store passwords. */
public final class PasswordHash {
    public static final int ITERATIONS = 600000;
    public final byte[] salt, hash;
    private PasswordHash(byte[] salt, byte[] hash) { this.salt = salt.clone(); this.hash = hash.clone(); }
    public static PasswordHash create(char[] password) throws GeneralSecurityException {
        byte[] salt = new byte[16]; new SecureRandom().nextBytes(salt);
        return new PasswordHash(salt, derive(password, salt));
    }
    public boolean verify(char[] password) throws GeneralSecurityException {
        byte[] actual = derive(password, salt);
        try { return MessageDigest.isEqual(hash, actual); } finally { Arrays.fill(actual, (byte) 0); }
    }
    private static byte[] derive(char[] password, byte[] salt) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, 256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        finally { spec.clearPassword(); }
    }
}
