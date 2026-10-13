package com.termux.app;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.termux.app.hometty.*;
import java.io.*;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class SecretStoreTest {
    @Test public void passwordRecordIsEncryptedAndTamperingIsRejected() throws Exception {
        new HomeConfig();
        String name = "test_password_record";
        byte[] record = PasswordHash.create("instrumentation password".toCharArray()).encode();
        File file = new File(HomeConfig.DATA, name + ".sealed");
        try {
            SecretStore.put(name, record);
            assertTrue(SecretStore.contains(name));
            byte[] decoded = SecretStore.get(name);
            try { assertArrayEquals(record, decoded); assertTrue(PasswordHash.decode(decoded).verify("instrumentation password".toCharArray())); }
            finally { Arrays.fill(decoded, (byte)0); }
            try (RandomAccessFile bytes = new RandomAccessFile(file, "rw")) {
                assertTrue(bytes.length() > record.length);
                bytes.seek(bytes.length() - 1); int value = bytes.read(); bytes.seek(bytes.length() - 1); bytes.write(value ^ 1);
            }
            try { SecretStore.get(name); fail("Tampered ciphertext decrypted"); } catch (javax.crypto.AEADBadTagException expected) {}
        } finally { Arrays.fill(record, (byte)0); SecretStore.delete(name); }
    }
}
