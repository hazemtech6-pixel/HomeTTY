package com.termux.app.hometty;
import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;
public class PasswordHashTest {
    @Test public void independentSaltsAndVerification() throws Exception {
        PasswordHash a=PasswordHash.create("correct horse".toCharArray()),b=PasswordHash.create("correct horse".toCharArray());
        assertFalse(Arrays.equals(a.salt,b.salt)); assertFalse(Arrays.equals(a.hash,b.hash)); assertTrue(a.verify("correct horse".toCharArray())); assertFalse(a.verify("wrong".toCharArray()));
    }
}
