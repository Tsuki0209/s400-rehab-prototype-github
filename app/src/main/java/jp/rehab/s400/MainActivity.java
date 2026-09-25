package jp.rehab.s400;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int REQ_PERMS = 1001;
    private static final int REQ_EXPORT = 1002;

    private SettingsStore settings;
    private CsvStore csvStore;
    private BleScanner scanner;
    private boolean scanning;
    private S400Measurement current;
    private boolean savedThisScan;

    private TextView status;
    private TextView weight;
    private TextView impedance;
    private TextView lowImpedance;
    private TextView heartRate;
    private TextView timestamp;
    private TextView records;
    private TextView diagnostics;
    private Button scanButton;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        settings = new SettingsStore(this);
        csvStore = new CsvStore(this);

        buildUi();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 22, 24, 22);

        TextView title = text("S400 リハビリ計測 試作", 24);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(
                title,
                lp(-1, -2, 0, 0, 0, 12)
        );

        status = text("待機中", 16);
        status.setGravity(Gravity.CENTER);
        root.addView(
                status,
                lp(-1, -2, 0, 0, 0, 8)
        );

        weight = text("--.- kg", 58);
        weight.setGravity(Gravity.CENTER);
        root.addView(
                weight,
                lp(-1, -2, 0, 6, 0, 14)
        );

        LinearLayout data = new LinearLayout(this);
        data.setOrientation(LinearLayout.VERTICAL);
        data.setPadding(18, 12, 18, 12);
        data.setBackgroundColor(Color.rgb(245, 245, 245));

        data.addView(
                row(
                        "高周波インピーダンス",
                        impedance = text("-", 19)
                )
        );

        data.addView(
                row(
                        "低周波インピーダンス",
                        lowImpedance = text("-", 19)
                )
        );

        data.addView(
                row(
                        "心拍数",
                        heartRate = text("-", 19)
                )
        );

        data.addView(
                row(
                        "デバイス時刻",
                        timestamp = text("-", 17)
                )
        );

        root.addView(
                data,
                lp(-1, -2, 0, 0, 0, 12)
        );

        scanButton = new Button(this);
        scanButton.setText("測定開始");
        scanButton.setTextSize(19);
        scanButton.setOnClickListener(v -> toggleScan());

        root.addView(
                scanButton,
                lp(-1, -2, 0, 4, 0, 6)
        );

        Button csv = new Button(this);
        csv.setText("CSVを書き出す");
        csv.setOnClickListener(v -> exportCsv());

        root.addView(
                csv,
                lp(-1, -2, 0, 0, 0, 6)
        );

        Button settingsButton = new Button(this);
        settingsButton.setText("S400設定 / 接続情報");
        settingsButton.setOnClickListener(
                v -> startActivity(
                        new Intent(this, SettingsActivity.class)
                )
        );

        root.addView(
                settingsButton,
                lp(-1, -2, 0, 0, 0, 6)
        );

        records = text("CSV記録: 0 bytes", 13);
        records.setGravity(Gravity.CENTER_HORIZONTAL);

        root.addView(
                records,
                lp(-1, -2, 0, 8, 0, 0)
        );

        diagnostics = text("診断: 待機中", 13);
        diagnostics.setTextColor(Color.DKGRAY);
        diagnostics.setGravity(Gravity.CENTER_HORIZONTAL);

        root.addView(
                diagnostics,
                lp(-1, -2, 0, 8, 0, 0)
        );

        updateRecordInfo();

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);

        setContentView(scroll);
    }

    private LinearLayout row(String name, TextView value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        TextView label = text(name, 15);
        label.setTextColor(Color.DKGRAY);

        row.addView(
                label,
                new LinearLayout.LayoutParams(0, -2, 1f)
        );

        row.addView(
                value,
                new LinearLayout.LayoutParams(-2, -2)
        );

        return row;
    }

    private void toggleScan() {
        if (scanning) {
            stopScan();
        } else {
            beginScan();
        }
    }

    private void beginScan() {
        if (!checkBluetooth()) {
            return;
        }

        if (!permissionsGranted()) {
            requestBlePermissions();
            return;
        }

        String mac = settings.getMac();
        String bind = settings.getBindKey();

        if (mac.isEmpty() || bind.isEmpty()) {
            toast("先にS400設定でMACとbindkeyを入力してください");

            startActivity(
                    new Intent(this, SettingsActivity.class)
            );

            return;
        }

        try {
            current = null;
            savedThisScan = false;

            MiBeaconDecryptor decryptor =
                    new MiBeaconDecryptor(bind, mac);

            scanner = new BleScanner(
                    this,
                    decryptor,
                    this::onMeasurement,
                    this::onDebug
            );

            scanner.start();

            scanning = true;

            scanButton.setText("測定停止");

            status.setText(
                    "S400を検索中… スケールに乗ってください"
            );

            diagnostics.setText(
                    "診断: BLEスキャン開始"
            );

        } catch (Exception e) {
            toast("BLE開始失敗: " + e.getMessage());
            status.setText("開始失敗");

            if (diagnostics != null) {
                diagnostics.setText(
                        "診断: BLE開始失敗 - " + e.getMessage()
                );
            }
        }
    }

    private void onDebug(String message) {
        runOnUiThread(() -> {
            if (diagnostics != null) {
                diagnostics.setText("診断: " + message);
            }
        });
    }

    private void stopScan() {
        if (scanner != null) {
            scanner.stop();
        }

        scanner = null;
        scanning = false;

        scanButton.setText("測定開始");

        if (current != null && current.isComplete()) {
            status.setText("計測データ取得完了");
        } else {
            status.setText("停止しました");
        }

        if (scanner == null) {
            // diagnostics は最後に受信した状態をそのまま残す
        }
    }

    private void onMeasurement(S400Measurement m) {
        runOnUiThread(() -> {
            if (current == null) {
                current = new S400Measurement();
            }

            current.merge(m);

            if (current.weightKg != null) {
                weight.setText(
                        String.format(
                                Locale.JAPAN,
                                "%.1f kg",
                                current.weightKg
                        )
                );
            }

            impedance.setText(
                    current.impedanceOhm == null
                            ? "-"
                            : String.format(
                                    Locale.JAPAN,
                                    "%.1f Ω",
                                    current.impedanceOhm
                            )
            );

            lowImpedance.setText(
                    current.impedanceLowOhm == null
                            ? "-"
                            : String.format(
                                    Locale.JAPAN,
                                    "%.1f Ω",
                                    current.impedanceLowOhm
                            )
            );

            heartRate.setText(
                    current.heartRateBpm == null
                            ? "-"
                            : String.format(
                                    Locale.JAPAN,
                                    "%d bpm",
                                    current.heartRateBpm
                            )
            );

            timestamp.setText(
                    current.deviceTimestamp == null
                            ? "-"
                            : String.valueOf(
                                    current.deviceTimestamp
                            )
            );

            status.setText(
                    current.isComplete()
                            ? "計測データ取得完了"
                            : "計測値を受信中…"
            );

            if (current.isComplete() && !savedThisScan) {
                savedThisScan = true;

                saveMeasurement();
                stopScan();
            }
        });
    }

    private void saveMeasurement() {
        if (current == null || current.weightKg == null) {
            return;
        }

        try {
            long now = System.currentTimeMillis() / 1000L;

            csvStore.append(current, now);
            updateRecordInfo();

            toast("計測結果を保存しました");

        } catch (Exception e) {
            toast("CSV保存失敗: " + e.getMessage());
        }
    }

    private void exportCsv() {
        if (csvStore.size() == 0) {
            toast("まだ記録がありません");
            return;
        }

        Intent intent = new Intent(
                Intent.ACTION_CREATE_DOCUMENT
        );

        intent.setType("text/csv");
        intent.putExtra(
                Intent.EXTRA_TITLE,
                "s400_measurements.csv"
        );

        startActivityForResult(intent, REQ_EXPORT);
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (requestCode == REQ_EXPORT
                && resultCode == RESULT_OK
                && data != null
                && data.getData() != null) {

            try {
                csvStore.export(
                        this,
                        data.getData()
                );

                toast("CSVを書き出しました");

            } catch (Exception e) {
                toast(
                        "CSV書き出し失敗: "
                                + e.getMessage()
                );
            }
        }
    }

    private boolean checkBluetooth() {
        BluetoothManager manager =
                (BluetoothManager)
                        getSystemService(
                                BLUETOOTH_SERVICE
                        );

        BluetoothAdapter adapter =
                manager.getAdapter();

        if (adapter == null || !adapter.isEnabled()) {
            try {
                startActivity(
                        new Intent(
                                BluetoothAdapter.ACTION_REQUEST_ENABLE
                        )
                );
            } catch (Exception ignored) {
            }

            return false;
        }

        return true;
    }

    private boolean permissionsGranted() {
        if (Build.VERSION.SDK_INT >= 31) {
            return checkSelfPermission(
                    Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
                    &&
                    checkSelfPermission(
                            Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED;
        }

        return checkSelfPermission(
                Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestBlePermissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            requestPermissions(
                    new String[]{
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_CONNECT
                    },
                    REQ_PERMS
            );

        } else {
            requestPermissions(
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION
                    },
                    REQ_PERMS
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] results) {

        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                results
        );

        if (requestCode == REQ_PERMS) {
            boolean ok = results.length > 0;

            for (int r : results) {
                if (r != PackageManager.PERMISSION_GRANTED) {
                    ok = false;
                }
            }

            if (ok) {
                beginScan();
            } else {
                toast("Bluetooth権限が必要です");
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (records != null) {
            updateRecordInfo();
        }
    }

    @Override
    protected void onDestroy() {
        if (scanner != null) {
            scanner.stop();
        }

        super.onDestroy();
    }

    private void updateRecordInfo() {
        if (records != null) {
            records.setText(
                    "CSV記録ファイル: "
                            + csvStore.size()
                            + " bytes"
            );
        }
    }

    private TextView text(String s, float size) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        return t;
    }

    private LinearLayout.LayoutParams lp(
            int w,
            int h,
            int l,
            int top,
            int r,
            int b) {

        LinearLayout.LayoutParams p =
                new LinearLayout.LayoutParams(w, h);

        p.setMargins(l, top, r, b);

        return p;
    }

    private void toast(String s) {
        Toast.makeText(
                this,
                s,
                Toast.LENGTH_SHORT
        ).show();
    }
}