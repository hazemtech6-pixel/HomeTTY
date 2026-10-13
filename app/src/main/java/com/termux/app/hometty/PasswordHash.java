package com.termux.app.hometty;

import java.security.*;
import java.util.Arrays;
import javax.crypto.SecretKeyFactory;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
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
    public byte[] encode() { return ByteBuffer.allocate(56).putInt(1).putInt(ITERATIONS).put(salt).put(hash).array(); }
    public static PasswordHash decode(byte[] encoded) {
        if (encoded.length != 56) throw new IllegalArgumentException("Invalid password record");
        ByteBuffer input = ByteBuffer.wrap(encoded);
        if (input.getInt() != 1 || input.getInt() != ITERATIONS) throw new IllegalArgumentException("Unsupported password record");
        byte[] salt = new byte[16], hash = new byte[32]; input.get(salt); input.get(hash);
        return new PasswordHash(salt, hash);
    }
    static byte[] compatibleDerive(char[] password, byte[] salt, int iterations) throws GeneralSecurityException {
        // Android 7 lacks the SHA256 SecretKeyFactory. Its HMAC primitive provides
        // the identical PBKDF2 output, without reducing the work factor.
        java.nio.ByteBuffer encoded = java.nio.charset.StandardCharsets.UTF_8.encode(java.nio.CharBuffer.wrap(password));
        byte[] secret = new byte[encoded.remaining()]; encoded.get(secret);
        if (encoded.hasArray()) Arrays.fill(encoded.array(), (byte)0);
        try {
            Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret.length == 0 ? new byte[1] : secret, "HmacSHA256"));
            byte[] block = Arrays.copyOf(salt, salt.length + 4); block[block.length - 1] = 1;
            byte[] current = mac.doFinal(block), result = current.clone();
            for (int round = 1; round < iterations; round++) {
                byte[] next = mac.doFinal(current); Arrays.fill(current, (byte)0); current = next;
                for (int index = 0; index < result.length; index++) result[index] ^= current[index];
            }
            Arrays.fill(current, (byte)0); return result;
        } finally { Arrays.fill(secret, (byte)0); }
    }
    private static byte[] derive(char[] password, byte[] salt) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, 256);
        try {
            try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
            catch (NoSuchAlgorithmException unavailable) { return compatibleDerive(password, salt, ITERATIONS); }
        }
        finally { spec.clearPassword(); }
    }
}
