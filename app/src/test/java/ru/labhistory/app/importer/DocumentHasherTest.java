package ru.labhistory.app.importer;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class DocumentHasherTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void createsStableSha256Fingerprint() throws Exception {
        File first = temporaryFolder.newFile("first.pdf");
        File second = temporaryFolder.newFile("second.pdf");
        Files.write(first.toPath(), "same document".getBytes(StandardCharsets.UTF_8));
        Files.write(second.toPath(), "same document".getBytes(StandardCharsets.UTF_8));

        assertEquals(DocumentHasher.sha256(first), DocumentHasher.sha256(second));
        assertEquals(64, DocumentHasher.sha256(first).length());
    }

    @Test
    public void differentContentHasDifferentFingerprint() throws Exception {
        File first = temporaryFolder.newFile("first.jpg");
        File second = temporaryFolder.newFile("second.jpg");
        Files.write(first.toPath(), new byte[]{1, 2, 3});
        Files.write(second.toPath(), new byte[]{1, 2, 4});

        assertNotEquals(DocumentHasher.sha256(first), DocumentHasher.sha256(second));
    }
}
