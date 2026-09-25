package jp.rehab.s400;

import android.content.Context;
import android.net.Uri;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class CsvStore {
    private static final String FILE = "measurements.csv";
    private final Context context;

    CsvStore(Context context) { this.context = context.getApplicationContext(); }

    void append(S400Measurement m, long timestampSeconds) throws Exception {
        File f = new File(context.getFilesDir(), FILE);
        boolean newFile = !f.exists() || f.length() == 0;
        try (Writer out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f, true), StandardCharsets.UTF_8))) {
            if (newFile) {
                out.write("timestamp,datetime,profile_id,weight_kg,impedance_ohm,impedance_low_ohm,heart_rate_bpm,device_timestamp,bmi\n");
            }
            String dt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.JAPAN).format(new Date(timestampSeconds * 1000L));
            double bmi = Double.NaN;
            out.write(csv(timestampSeconds) + "," + csv(dt) + "," + m.profileId + "," +
                    csv(m.weightKg) + "," + csv(m.impedanceOhm) + "," + csv(m.impedanceLowOhm) + "," +
                    csv(m.heartRateBpm) + "," + csv(m.deviceTimestamp) + "," + csv(bmi) + "\n");
        }
    }

    void export(Context activityContext, Uri uri) throws Exception {
        File f = new File(context.getFilesDir(), FILE);
        if (!f.exists()) throw new IllegalStateException("CSVがまだありません");
        try (java.io.InputStream in = new java.io.FileInputStream(f);
             java.io.OutputStream out = activityContext.getContentResolver().openOutputStream(uri)) {
            if (out == null) throw new IllegalStateException("保存先を開けません");
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        }
    }

    long size() {
        File f = new File(context.getFilesDir(), FILE);
        return f.exists() ? f.length() : 0L;
    }

    private static String csv(Object value) {
        if (value == null || (value instanceof Double && ((Double) value).isNaN())) return "";
        String s = String.valueOf(value);
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }
}
