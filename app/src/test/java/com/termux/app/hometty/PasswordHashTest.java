package com.termux.app.hometty;
import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;
public class PasswordHashTest {
    @Test public void compatibleImplementationMatchesPublishedVector() throws Exception {
        byte[] result = PasswordHash.compatibleDerive("password".toCharArray(), "salt".getBytes("UTF-8"), 1);
        StringBuilder hex = new StringBuilder(); for (byte value : result) hex.append(String.format("%02x", value & 255));
        assertEquals("120fb6cffcf8b32c43e7225256c4f837a86548c92ccc35480805987cb70be17b", hex.toString());
    }
    @Test public void recordsRoundTripAndRejectCorruption() throws Exception {
        PasswordHash value = PasswordHash.create("example password".toCharArray());
        assertTrue(PasswordHash.decode(value.encode()).verify("example password".toCharArray()));
        byte[] malformed = value.encode(); malformed[0] = 9;
        try { PasswordHash.decode(malformed); fail(); } catch (IllegalArgumentException expected) {}
    }
    @Test public void independentSaltsAndVerification() throws Exception {
        PasswordHash a=PasswordHash.create("correct horse".toCharArray()),b=PasswordHash.create("correct horse".toCharArray());
        assertFalse(Arrays.equals(a.salt,b.salt)); assertFalse(Arrays.equals(a.hash,b.hash)); assertTrue(a.verify("correct horse".toCharArray())); assertFalse(a.verify("wrong".toCharArray()));
    }
}
