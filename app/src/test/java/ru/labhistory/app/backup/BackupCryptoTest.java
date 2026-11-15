package ru.labhistory.app.backup;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;

public class BackupCryptoTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void encryptAndDecryptRoundTrip() throws Exception {
        byte[] original = "профили, документы и показатели".getBytes("UTF-8");
        ByteArrayOutputStream encrypted = new ByteArrayOutputStream();

        BackupCrypto.encrypt(
                new ByteArrayInputStream(original),
                encrypted,
                "correct horse battery staple".toCharArray()
        );

        File restored = temporaryFolder.newFile("restored.zip");
        BackupCrypto.decrypt(
                new ByteArrayInputStream(encrypted.toByteArray()),
                restored,
                "correct horse battery staple".toCharArray()
        );
        assertArrayEquals(original, Files.readAllBytes(restored.toPath()));
    }

    @Test
    public void wrongPasswordDoesNotLeavePlaintext() throws Exception {
        ByteArrayOutputStream encrypted = new ByteArrayOutputStream();
        BackupCrypto.encrypt(
                new ByteArrayInputStream(new byte[]{1, 2, 3}),
                encrypted,
                "password-one".toCharArray()
        );
        File restored = new File(temporaryFolder.getRoot(), "wrong-password.zip");

        assertThrows(
                java.io.IOException.class,
                () -> BackupCrypto.decrypt(
                        new ByteArrayInputStream(encrypted.toByteArray()),
                        restored,
                        "password-two".toCharArray()
                )
        );
        assertFalse(restored.exists());
    }

    @Test
    public void tamperedBackupIsRejected() throws Exception {
        ByteArrayOutputStream encrypted = new ByteArrayOutputStream();
        BackupCrypto.encrypt(
                new ByteArrayInputStream(new byte[]{4, 5, 6}),
                encrypted,
                "password-three".toCharArray()
        );
        byte[] tampered = encrypted.toByteArray();
        tampered[tampered.length - 1] ^= 1;
        File restored = new File(temporaryFolder.getRoot(), "tampered.zip");

        assertThrows(
                java.io.IOException.class,
                () -> BackupCrypto.decrypt(
                        new ByteArrayInputStream(tampered),
                        restored,
                        "password-three".toCharArray()
                )
        );
        assertFalse(restored.exists());
        Arrays.fill(tampered, (byte) 0);
    }
}
