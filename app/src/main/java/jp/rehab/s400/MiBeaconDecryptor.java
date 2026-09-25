package jp.rehab.s400;

final class MiBeaconDecryptor {

    static final class Result {
        final byte[] plaintext;
        final String error;

        final int frameControl;
        final int version;

        final boolean objectIncluded;
        final boolean capabilityIncluded;
        final boolean macIncluded;
        final boolean encrypted;

        final int productId;
        final int frameCounter;

        final String nonceMacHex;
        final String extCounterHex;

        Result(
                byte[] plaintext,
                String error,
                int frameControl,
                int version,
                boolean objectIncluded,
                boolean capabilityIncluded,
                boolean macIncluded,
                boolean encrypted,
                int productId,
                int frameCounter,
                String nonceMacHex,
                String extCounterHex) {

            this.plaintext = plaintext;
            this.error = error;

            this.frameControl = frameControl;
            this.version = version;

            this.objectIncluded = objectIncluded;
            this.capabilityIncluded = capabilityIncluded;
            this.macIncluded = macIncluded;
            this.encrypted = encrypted;

            this.productId = productId;
            this.frameCounter = frameCounter;

            this.nonceMacHex = nonceMacHex;
            this.extCounterHex = extCounterHex;
        }

        boolean success() {
            return plaintext != null && plaintext.length > 0;
        }
    }

    private final byte[] bindKey;
    private final byte[] scaleMac;

    MiBeaconDecryptor(String bindKeyHex, String scaleMacText) {
        this.bindKey = hexToBytes(bindKeyHex);
        this.scaleMac = parseMac(scaleMacText);

        if (bindKey.length != 16) {
            throw new IllegalArgumentException(
                    "bindkey must be 16 bytes");
        }

        if (scaleMac.length != 6) {
            throw new IllegalArgumentException(
                    "MAC must be 6 bytes");
        }
    }

    byte[] decrypt(byte[] frame) {
        return decryptDetailed(frame).plaintext;
    }

    Result decryptDetailed(byte[] frame) {

        int frameControl = -1;
        int version = -1;

        boolean objectIncluded = false;
        boolean capabilityIncluded = false;
        boolean macIncluded = false;
        boolean encrypted = false;

        int productId = -1;
        int frameCounter = -1;

        String nonceMacHex = "";
        String extCounterHex = "";

        try {
            if (frame == null || frame.length < 5) {
                return fail(
                        "frame too short",
                        frameControl,
                        version,
                        objectIncluded,
                        capabilityIncluded,
                        macIncluded,
                        encrypted,
                        productId,
                        frameCounter,
                        nonceMacHex,
                        extCounterHex
                );
            }

            /*
             * MiBeacon:
             *
             * byte 0-1 : frame control
             * byte 2-3 : product id
             * byte 4   : frame counter
             */
            frameControl =
                    (frame[0] & 0xFF)
                    | ((frame[1] & 0xFF) << 8);

            productId =
                    (frame[2] & 0xFF)
                    | ((frame[3] & 0xFF) << 8);

            frameCounter = frame[4] & 0xFF;

            objectIncluded =
                    ((frameControl >>> 6) & 1) != 0;

            capabilityIncluded =
                    ((frameControl >>> 5) & 1) != 0;

            macIncluded =
                    ((frameControl >>> 4) & 1) != 0;

            encrypted =
                    ((frameControl >>> 3) & 1) != 0;

            version = frameControl >>> 12;

            int idx = 5;

            if (!objectIncluded) {
                return fail(
                        "object flag is not set",
                        frameControl,
                        version,
                        objectIncluded,
                        capabilityIncluded,
                        macIncluded,
                        encrypted,
                        productId,
                        frameCounter,
                        nonceMacHex,
                        extCounterHex
                );
            }

            /*
             * MAC
             *
             * MiBeaconのnonceでは広告に入っているMACの6バイトを
             * そのまま使用する。
             *
             * 以前の実装ではここを逆順にしていた。
             */
            byte[] xiaomiMac;

            if (macIncluded) {

                if (frame.length < idx + 6) {
                    return fail(
                            "MAC field truncated",
                            frameControl,
                            version,
                            objectIncluded,
                            capabilityIncluded,
                            macIncluded,
                            encrypted,
                            productId,
                            frameCounter,
                            nonceMacHex,
                            extCounterHex
                    );
                }

                xiaomiMac = new byte[6];

                System.arraycopy(
                        frame,
                        idx,
                        xiaomiMac,
                        0,
                        6
                );

                idx += 6;

            } else {

                /*
                 * S400では測定フレーム側にMACが含まれない場合が
                 * あるため、設定されたスケールMACを使う。
                 */
                xiaomiMac = scaleMac.clone();
            }

            nonceMacHex = toHex(xiaomiMac);

            /*
             * Capability
             */
            if (capabilityIncluded) {

                if (frame.length < idx + 1) {
                    return fail(
                            "capability field truncated",
                            frameControl,
                            version,
                            objectIncluded,
                            capabilityIncluded,
                            macIncluded,
                            encrypted,
                            productId,
                            frameCounter,
                            nonceMacHex,
                            extCounterHex
                    );
                }

                int capability =
                        frame[idx] & 0xFF;

                idx++;

                /*
                 * IO capabilityが存在する場合、
                 * 追加で2バイト。
                 */
                if ((capability & 0x20) != 0) {

                    if (frame.length < idx + 2) {
                        return fail(
                                "IO capability field truncated",
                                frameControl,
                                version,
                                objectIncluded,
                                capabilityIncluded,
                                macIncluded,
                                encrypted,
                                productId,
                                frameCounter,
                                nonceMacHex,
                                extCounterHex
                        );
                    }

                    idx += 2;
                }
            }

            /*
             * 非暗号化フレーム
             */
            if (!encrypted) {

                byte[] plain =
                        copyOfRange(
                                frame,
                                idx,
                                frame.length
                        );

                return success(
                        plain,
                        frameControl,
                        version,
                        objectIncluded,
                        capabilityIncluded,
                        macIncluded,
                        encrypted,
                        productId,
                        frameCounter,
                        nonceMacHex,
                        extCounterHex
                );
            }

            /*
             * 暗号化フレーム
             *
             * 最後の7バイト:
             *   3 bytes extended frame counter
             *   4 bytes MIC
             */
            if (frame.length < idx + 7) {
                return fail(
                        "encrypted frame too short",
                        frameControl,
                        version,
                        objectIncluded,
                        capabilityIncluded,
                        macIncluded,
                        encrypted,
                        productId,
                        frameCounter,
                        nonceMacHex,
                        extCounterHex
                );
            }

            int extCounterStart =
                    frame.length - 7;

            byte[] extCounter =
                    copyOfRange(
                            frame,
                            extCounterStart,
                            extCounterStart + 3
                    );

            extCounterHex = toHex(extCounter);

            /*
             * AES-CCM nonce:
             *
             * 0..5   = MAC
             * 6..8   = PID + frame counter
             * 9..11  = extended frame counter
             */
            byte[] nonce = new byte[12];

            System.arraycopy(
                    xiaomiMac,
                    0,
                    nonce,
                    0,
                    6
            );

            System.arraycopy(
                    frame,
                    2,
                    nonce,
                    6,
                    3
            );

            System.arraycopy(
                    extCounter,
                    0,
                    nonce,
                    9,
                    3
            );

            /*
             * 暗号文
             */
            byte[] ciphertext =
                    copyOfRange(
                            frame,
                            idx,
                            extCounterStart
                    );

            /*
             * MIC
             */
            byte[] mic =
                    copyOfRange(
                            frame,
                            frame.length - 4,
                            frame.length
                    );

            /*
             * AesCcm.decrypt() は
             * ciphertext + tag
             * を受け取る。
             */
            byte[] withTag =
                    new byte[
                            ciphertext.length + mic.length
                    ];

            System.arraycopy(
                    ciphertext,
                    0,
                    withTag,
                    0,
                    ciphertext.length
            );

            System.arraycopy(
                    mic,
                    0,
                    withTag,
                    ciphertext.length,
                    mic.length
            );

            /*
             * MiBeacon AAD = 0x11
             */
            byte[] plain =
                    AesCcm.decrypt(
                            bindKey,
                            nonce,
                            new byte[]{0x11},
                            withTag,
                            4
                    );

            return success(
                    plain,
                    frameControl,
                    version,
                    objectIncluded,
                    capabilityIncluded,
                    macIncluded,
                    encrypted,
                    productId,
                    frameCounter,
                    nonceMacHex,
                    extCounterHex
            );

        } catch (Exception e) {

            String message = e.getMessage();

            if (message == null || message.isEmpty()) {
                message = e.getClass().getSimpleName();
            }

            return fail(
                    message,
                    frameControl,
                    version,
                    objectIncluded,
                    capabilityIncluded,
                    macIncluded,
                    encrypted,
                    productId,
                    frameCounter,
                    nonceMacHex,
                    extCounterHex
            );
        }
    }

    private Result success(
            byte[] plaintext,
            int frameControl,
            int version,
            boolean objectIncluded,
            boolean capabilityIncluded,
            boolean macIncluded,
            boolean encrypted,
            int productId,
            int frameCounter,
            String nonceMacHex,
            String extCounterHex) {

        return new Result(
                plaintext,
                null,
                frameControl,
                version,
                objectIncluded,
                capabilityIncluded,
                macIncluded,
                encrypted,
                productId,
                frameCounter,
                nonceMacHex,
                extCounterHex
        );
    }

    private Result fail(
            String error,
            int frameControl,
            int version,
            boolean objectIncluded,
            boolean capabilityIncluded,
            boolean macIncluded,
            boolean encrypted,
            int productId,
            int frameCounter,
            String nonceMacHex,
            String extCounterHex) {

        return new Result(
                null,
                error,
                frameControl,
                version,
                objectIncluded,
                capabilityIncluded,
                macIncluded,
                encrypted,
                productId,
                frameCounter,
                nonceMacHex,
                extCounterHex
        );
    }

    static byte[] parseMac(String text) {
        String s =
                text
                        .replace(":", "")
                        .replace("-", "")
                        .replace(" ", "");

        return hexToBytes(s);
    }

    static byte[] hexToBytes(String text) {

        String s =
                text
                        .replace(" ", "")
                        .replace(":", "")
                        .toLowerCase();

        if ((s.length() & 1) != 0) {
            throw new IllegalArgumentException(
                    "odd hex length");
        }

        byte[] out =
                new byte[s.length() / 2];

        for (int i = 0; i < out.length; i++) {

            int hi =
                    Character.digit(
                            s.charAt(i * 2),
                            16
                    );

            int lo =
                    Character.digit(
                            s.charAt(i * 2 + 1),
                            16
                    );

            if (hi < 0 || lo < 0) {
                throw new IllegalArgumentException(
                        "invalid hex");
            }

            out[i] =
                    (byte) ((hi << 4) | lo);
        }

        return out;
    }

    static String toHex(byte[] data) {

        if (data == null) {
            return "";
        }

        StringBuilder sb =
                new StringBuilder(
                        data.length * 2
                );

        for (byte b : data) {
            sb.append(
                    String.format(
                            "%02X",
                            b & 0xFF
                    )
            );
        }

        return sb.toString();
    }

    private static byte[] copyOfRange(
            byte[] src,
            int from,
            int to) {

        int len =
                Math.max(0, to - from);

        byte[] out =
                new byte[len];

        System.arraycopy(
                src,
                from,
                out,
                0,
                len
        );

        return out;
    }
}