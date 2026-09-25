package jp.rehab.s400;

final class S400Parser {
    private static final int OBJ_S400 = 0x6E16;

    S400Measurement parse(byte[] payload) {
        if (payload == null) return null;
        S400Measurement result = null;
        int idx = 0;
        while (idx + 3 <= payload.length) {
            int objId = (payload[idx] & 0xFF) | ((payload[idx + 1] & 0xFF) << 8);
            int len = payload[idx + 2] & 0xFF;
            int start = idx + 3;
            int end = start + len;
            if (end > payload.length) break;
            if (objId == OBJ_S400 && len == 9) {
                S400Measurement m = parseObject(payload, start);
                if (m != null) {
                    if (result == null) result = m;
                    else result.merge(m);
                }
            }
            idx = end;
        }
        return result;
    }

    private S400Measurement parseObject(byte[] x, int start) {
        int profileId = x[start] & 0xFF;
        long packed = ((long) x[start + 1] & 0xFF)
                | (((long) x[start + 2] & 0xFF) << 8)
                | (((long) x[start + 3] & 0xFF) << 16)
                | (((long) x[start + 4] & 0xFF) << 24);
        long timestamp = ((long) x[start + 5] & 0xFF)
                | (((long) x[start + 6] & 0xFF) << 8)
                | (((long) x[start + 7] & 0xFF) << 16)
                | (((long) x[start + 8] & 0xFF) << 24);
        if (packed == 0) return null;

        int mass = (int) (packed & 0x7FFL);
        int hrRaw = (int) ((packed >>> 11) & 0x7FL);
        int impedance = (int) ((packed >>> 18) & 0x3FFFL);

        S400Measurement m = new S400Measurement();
        m.profileId = profileId;
        m.deviceTimestamp = timestamp;
        if (mass != 0) m.weightKg = mass / 10.0;
        if (hrRaw >= 1 && hrRaw <= 126) m.heartRateBpm = hrRaw + 50;
        if (impedance != 0) {
            if (mass != 0) m.impedanceOhm = impedance / 10.0;
            else m.impedanceLowOhm = impedance / 10.0;
        }
        return m;
    }
}
