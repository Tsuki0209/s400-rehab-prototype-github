package jp.rehab.s400;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.util.Arrays;

/** Minimal AES-CCM decryptor for the MiBeacon profile used by S400. */
final class AesCcm {
    private AesCcm() {}

    static byte[] decrypt(byte[] key, byte[] nonce, byte[] aad, byte[] ciphertext, int tagLen) throws GeneralSecurityException {
        if (nonce.length < 7 || nonce.length > 13) throw new GeneralSecurityException("invalid nonce");
        if (tagLen < 4 || tagLen > 16 || (tagLen & 1) != 0) throw new GeneralSecurityException("invalid tag");
        if (ciphertext.length < tagLen) throw new GeneralSecurityException("short ciphertext");

        int q = 15 - nonce.length;
        int n = ciphertext.length - tagLen;
        if (n >= (1 << (8 * q))) throw new GeneralSecurityException("message too long");

        Cipher aes = Cipher.getInstance("AES/ECB/NoPadding");
        aes.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));

        byte[] plaintext = new byte[n];
        int offset = 0;
        int counter = 1;
        while (offset < n) {
            byte[] s = ctrBlock(aes, nonce, q, counter++);
            int len = Math.min(16, n - offset);
            for (int i = 0; i < len; i++) plaintext[offset + i] = (byte) (ciphertext[offset + i] ^ s[i]);
            offset += len;
        }

        byte[] mac = cbcMac(aes, key, nonce, aad, plaintext, tagLen, q);
        byte[] s0 = ctrBlock(aes, nonce, q, 0);
        byte[] expected = new byte[tagLen];
        byte[] tag = Arrays.copyOfRange(ciphertext, n, ciphertext.length);
        for (int i = 0; i < tagLen; i++) expected[i] = (byte) (mac[i] ^ s0[i]);

        if (!constantTimeEquals(expected, tag)) throw new GeneralSecurityException("CCM tag mismatch");
        return plaintext;
    }

    private static byte[] cbcMac(Cipher aes, byte[] key, byte[] nonce, byte[] aad, byte[] plaintext,
                                 int tagLen, int q) throws GeneralSecurityException {
        int flags = (aad.length > 0 ? 0x40 : 0) | (((tagLen - 2) / 2) << 3) | (q - 1);
        byte[] b0 = new byte[16];
        b0[0] = (byte) flags;
        System.arraycopy(nonce, 0, b0, 1, nonce.length);
        writeLength(b0, 1 + nonce.length, plaintext.length, q);

        byte[] x = aes.doFinal(b0);

        if (aad.length > 0) {
            byte[] block = new byte[16];
            if (aad.length < 0xFF00) {
                block[0] = (byte) ((aad.length >>> 8) & 0xFF);
                block[1] = (byte) (aad.length & 0xFF);
                System.arraycopy(aad, 0, block, 2, Math.min(aad.length, 14));
            } else {
                throw new GeneralSecurityException("AAD too long");
            }
            x = aes.doFinal(xor16(x, block));
        }

        for (int off = 0; off < plaintext.length; off += 16) {
            byte[] block = new byte[16];
            int len = Math.min(16, plaintext.length - off);
            System.arraycopy(plaintext, off, block, 0, len);
            x = aes.doFinal(xor16(x, block));
        }
        return x;
    }

    private static byte[] ctrBlock(Cipher aes, byte[] nonce, int q, int counter) throws GeneralSecurityException {
        byte[] a = new byte[16];
        a[0] = (byte) (q - 1);
        System.arraycopy(nonce, 0, a, 1, nonce.length);
        writeLength(a, 1 + nonce.length, counter, q);
        return aes.doFinal(a);
    }

    private static void writeLength(byte[] out, int offset, int value, int q) {
        for (int i = q - 1; i >= 0; i--) {
            out[offset + i] = (byte) (value & 0xFF);
            value >>>= 8;
        }
    }

    private static byte[] xor16(byte[] a, byte[] b) {
        byte[] out = new byte[16];
        for (int i = 0; i < 16; i++) out[i] = (byte) (a[i] ^ b[i]);
        return out;
    }

    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) return false;
        int diff = 0;
        for (int i = 0; i < a.length; i++) diff |= a[i] ^ b[i];
        return diff == 0;
    }
}
