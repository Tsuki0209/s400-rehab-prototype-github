package jp.rehab.s400;

final class MiBeaconDecryptor {
    private final byte[] bindKey;
    private final byte[] scaleMac;

    MiBeaconDecryptor(String bindKeyHex, String scaleMacText) {
        this.bindKey = hexToBytes(bindKeyHex);
        this.scaleMac = parseMac(scaleMacText);
        if (bindKey.length != 16) throw new IllegalArgumentException("bindkey must be 16 bytes");
        if (scaleMac.length != 6) throw new IllegalArgumentException("MAC must be 6 bytes");
    }

    byte[] decrypt(byte[] frame) {
        try {
            if (frame == null || frame.length < 5) return null;
            int frctrl = (frame[0] & 0xFF) | ((frame[1] & 0xFF) << 8);
            boolean objectIncluded = ((frctrl >>> 6) & 1) != 0;
            boolean capabilityIncluded = ((frctrl >>> 5) & 1) != 0;
            boolean macIncluded = ((frctrl >>> 4) & 1) != 0;
            boolean encrypted = ((frctrl >>> 3) & 1) != 0;
            int version = frctrl >>> 12;
            int idx = 5;
            if (!objectIncluded) return null;

            byte[] xiaomiMac;
            if (macIncluded) {
                if (frame.length < idx + 6) return null;
                xiaomiMac = new byte[6];
                for (int i = 0; i < 6; i++) xiaomiMac[i] = frame[idx + 5 - i];
                idx += 6;
            } else {
                xiaomiMac = scaleMac.clone();
            }

            if (capabilityIncluded) {
                if (frame.length < idx + 1) return null;
                int capabilityTypes = frame[idx++] & 0xFF;
                if ((capabilityTypes & 0x20) != 0) {
                    if (frame.length < idx + 1) return null;
                    idx++;
                }
            }

            if (!encrypted) return copyOfRange(frame, idx, frame.length);
            if (version <= 3 || frame.length < idx + 9) return null;

            byte[] nonce = new byte[12];
            for (int i = 0; i < 6; i++) nonce[i] = xiaomiMac[5 - i];
            System.arraycopy(frame, 2, nonce, 6, 3);
            System.arraycopy(frame, frame.length - 7, nonce, 9, 3);

            byte[] ciphertext = copyOfRange(frame, idx, frame.length - 4);
            byte[] mic = copyOfRange(frame, frame.length - 4, frame.length);
            byte[] withTag = new byte[ciphertext.length + mic.length];
            System.arraycopy(ciphertext, 0, withTag, 0, ciphertext.length);
            System.arraycopy(mic, 0, withTag, ciphertext.length, mic.length);

            return AesCcm.decrypt(bindKey, nonce, new byte[]{0x11}, withTag, 4);
        } catch (Exception e) {
            return null;
        }
    }

    static byte[] parseMac(String text) {
        String s = text.replace(":", "").replace("-", "").replace(" ", "");
        return hexToBytes(s);
    }

    static byte[] hexToBytes(String text) {
        String s = text.replace(" ", "").replace(":", "").toLowerCase();
        if ((s.length() & 1) != 0) throw new IllegalArgumentException("odd hex length");
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(s.charAt(i * 2), 16);
            int lo = Character.digit(s.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) throw new IllegalArgumentException("invalid hex");
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    private static byte[] copyOfRange(byte[] src, int from, int to) {
        int len = Math.max(0, to - from);
        byte[] out = new byte[len];
        System.arraycopy(src, from, out, 0, len);
        return out;
    }
}
