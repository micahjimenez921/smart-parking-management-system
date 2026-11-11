package security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** Stores passwords as salted PBKDF2 hashes, so the real password is never kept anywhere. */
public final class PasswordHasher {
    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 100;

    private static final int ITERATIONS = 120_000;
    private static final int KEY_BITS = 256;
    private static final int SALT_BYTES = 16;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static String dummyHash;

    private PasswordHasher() {
    }

    public static void validate(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw new IllegalArgumentException("The password must be at least " + MIN_LENGTH + " characters.");
        }
        if (password.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("The password can be at most " + MAX_LENGTH + " characters.");
        }
    }

    public static String hash(String password) {
        validate(password);
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] derived = derive(password.toCharArray(), salt, ITERATIONS);
        return "pbkdf2$" + ITERATIONS + "$"
                + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(derived);
    }

    public static boolean verify(String password, String stored) {
        if (password == null || stored == null) return false;
        try {
            String[] parts = stored.split("\\$");
            if (parts.length != 4 || !"pbkdf2".equals(parts[0])) return false;
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = derive(password.toCharArray(), salt, iterations);
            return MessageDigest.isEqual(expected, actual);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Does the same work as a real check, so an unknown username takes as long as a wrong password. */
    public static void verifyAgainstDummy(String password) {
        if (dummyHash == null) {
            dummyHash = hash("dummy-password-for-timing");
        }
        verify(password == null ? "" : password, dummyHash);
    }

    /** A random password made of letters and digits that are easy to tell apart. */
    public static String generate(int length) {
        String alphabet = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    private static byte[] derive(char[] password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_BITS);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Password hashing is not available on this system.", e);
        }
    }
}