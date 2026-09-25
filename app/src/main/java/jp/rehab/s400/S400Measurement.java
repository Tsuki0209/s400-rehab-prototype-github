package jp.rehab.s400;

final class S400Measurement {
    int profileId;
    Double weightKg;
    Double impedanceOhm;
    Double impedanceLowOhm;
    Integer heartRateBpm;
    Long deviceTimestamp;
    long receivedAt = System.currentTimeMillis();

    boolean isComplete() {
        return weightKg != null && (impedanceOhm != null || impedanceLowOhm != null);
    }

    void merge(S400Measurement other) {
        if (other == null) return;
        profileId = other.profileId;
        if (other.weightKg != null) weightKg = other.weightKg;
        if (other.impedanceOhm != null) impedanceOhm = other.impedanceOhm;
        if (other.impedanceLowOhm != null) impedanceLowOhm = other.impedanceLowOhm;
        if (other.heartRateBpm != null) heartRateBpm = other.heartRateBpm;
        if (other.deviceTimestamp != null) deviceTimestamp = other.deviceTimestamp;
    }
}
