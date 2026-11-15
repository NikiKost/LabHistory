package ru.labhistory.app.backup;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public final class BackupCrypto {
    private static final byte[] MAGIC = "LABHIST\0".getBytes(StandardCharsets.US_ASCII);
    private static final int FORMAT_VERSION = 1;
    private static final int ITERATIONS = 210_000;
    private static final int MIN_ITERATIONS = 100_000;
    private static final int MAX_ITERATIONS = 1_000_000;
    private static final int SALT_LENGTH = 16;
    private static final int IV_LENGTH = 12;
    private static final int KEY_BITS = 256;
    private static final int GCM_TAG_BITS = 128;

    private BackupCrypto() {
    }

    public static void encrypt(InputStream plaintext, OutputStream encrypted, char[] password)
            throws IOException {
        requirePassword(password);
        byte[] salt = new byte[SALT_LENGTH];
        byte[] iv = new byte[IV_LENGTH];
        SecureRandom random = new SecureRandom();
        random.nextBytes(salt);
        random.nextBytes(iv);

        byte[] header = createHeader(ITERATIONS, salt, iv);
        try {
            Cipher cipher = createCipher(Cipher.ENCRYPT_MODE, password, ITERATIONS, salt, iv);
            cipher.updateAAD(header);
            encrypted.write(header);
            transform(plaintext, encrypted, cipher);
        } catch (GeneralSecurityException error) {
            throw new IOException("Не удалось зашифровать резервную копию", error);
        } finally {
            Arrays.fill(salt, (byte) 0);
            Arrays.fill(iv, (byte) 0);
        }
    }

    public static void decrypt(InputStream encrypted, File plaintext, char[] password)
            throws IOException {
        requirePassword(password);
        byte[] header = readExactly(encrypted, MAGIC.length + 8 + SALT_LENGTH + IV_LENGTH);
        int iterations = readHeaderInt(header, MAGIC.length + 4);
        validateHeader(header, iterations);
        byte[] salt = Arrays.copyOfRange(header, MAGIC.length + 8, MAGIC.length + 8 + SALT_LENGTH);
        byte[] iv = Arrays.copyOfRange(header, header.length - IV_LENGTH, header.length);

        boolean completed = false;
        try (OutputStream output = new FileOutputStream(plaintext)) {
            Cipher cipher = createCipher(Cipher.DECRYPT_MODE, password, iterations, salt, iv);
            cipher.updateAAD(header);
            transform(encrypted, output, cipher);
            completed = true;
        } catch (GeneralSecurityException error) {
            throw new IOException("Неверный пароль или резервная копия повреждена", error);
        } finally {
            Arrays.fill(salt, (byte) 0);
            Arrays.fill(iv, (byte) 0);
            Arrays.fill(header, (byte) 0);
            if (!completed && plaintext.exists()) {
                //noinspection ResultOfMethodCallIgnored
                plaintext.delete();
            }
        }
    }

    private static Cipher createCipher(
            int mode,
            char[] password,
            int iterations,
            byte[] salt,
            byte[] iv
    ) throws GeneralSecurityException {
        PBEKeySpec specification = new PBEKeySpec(password, salt, iterations, KEY_BITS);
        byte[] encodedKey = null;
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            encodedKey = factory.generateSecret(specification).getEncoded();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    mode,
                    new SecretKeySpec(encodedKey, "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, iv)
            );
            return cipher;
        } finally {
            specification.clearPassword();
            if (encodedKey != null) {
                Arrays.fill(encodedKey, (byte) 0);
            }
        }
    }

    private static void transform(InputStream input, OutputStream output, Cipher cipher)
            throws IOException, GeneralSecurityException {
        byte[] buffer = new byte[32 * 1024];
        int read;
        while ((read = input.read(buffer)) != -1) {
            byte[] transformed = cipher.update(buffer, 0, read);
            if (transformed != null) {
                output.write(transformed);
            }
        }
        byte[] finalBytes = cipher.doFinal();
        if (finalBytes != null) {
            output.write(finalBytes);
        }
        Arrays.fill(buffer, (byte) 0);
    }

    private static byte[] createHeader(int iterations, byte[] salt, byte[] iv) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.write(MAGIC);
            output.writeInt(FORMAT_VERSION);
            output.writeInt(iterations);
            output.write(salt);
            output.write(iv);
        }
        return bytes.toByteArray();
    }

    private static void validateHeader(byte[] header, int iterations) throws IOException {
        for (int index = 0; index < MAGIC.length; index++) {
            if (header[index] != MAGIC[index]) {
                throw new IOException("Это не резервная копия LabHistory");
            }
        }
        int version = readHeaderInt(header, MAGIC.length);
        if (version != FORMAT_VERSION) {
            throw new IOException("Версия резервной копии пока не поддерживается");
        }
        if (iterations < MIN_ITERATIONS || iterations > MAX_ITERATIONS) {
            throw new IOException("Некорректные параметры резервной копии");
        }
    }

    private static int readHeaderInt(byte[] value, int offset) {
        return ((value[offset] & 0xff) << 24)
                | ((value[offset + 1] & 0xff) << 16)
                | ((value[offset + 2] & 0xff) << 8)
                | (value[offset + 3] & 0xff);
    }

    private static byte[] readExactly(InputStream input, int length) throws IOException {
        byte[] result = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = input.read(result, offset, length - offset);
            if (read == -1) {
                throw new EOFException("Резервная копия обрезана или повреждена");
            }
            offset += read;
        }
        return result;
    }

    private static void requirePassword(char[] password) {
        if (password == null || password.length < 8) {
            throw new IllegalArgumentException("Пароль должен содержать не менее 8 символов");
        }
    }
}
