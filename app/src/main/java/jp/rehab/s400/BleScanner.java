package jp.rehab.s400;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.ParcelUuid;

import java.util.Locale;
import java.util.function.Consumer;

final class BleScanner {
    static final ParcelUuid MIBEACON_UUID =
            ParcelUuid.fromString("0000fe95-0000-1000-8000-00805f9b34fb");

    private final BluetoothLeScanner scanner;
    private final MiBeaconDecryptor decryptor;
    private final S400Parser parser = new S400Parser();
    private final Consumer<S400Measurement> onMeasurement;
    private final Consumer<String> onDebug;
    private ScanCallback callback;

    private int scanResults;
    private int serviceDataResults;
    private int decryptAttempts;
    private int decryptSuccesses;
    private int parseSuccesses;
    private int lastDebugScanCount;

    BleScanner(
            Context context,
            MiBeaconDecryptor decryptor,
            Consumer<S400Measurement> onMeasurement,
            Consumer<String> onDebug) {

        BluetoothManager manager =
                (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);

        BluetoothAdapter adapter = manager.getAdapter();
        scanner = adapter != null ? adapter.getBluetoothLeScanner() : null;

        this.decryptor = decryptor;
        this.onMeasurement = onMeasurement;
        this.onDebug = onDebug;
    }

    @SuppressLint("MissingPermission")
    void start() {
        if (scanner == null) {
            throw new IllegalStateException("BLE scanner unavailable");
        }

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setReportDelay(0)
                .build();

        /*
         * 診断用にBLEスキャンフィルタを一旦外す。
         *
         * 以前は FE95 のService Dataフィルタを使用していたが、
         * Android端末/BLEスタックによってはMiBeacon広告を
         * 正しく返さない場合があるため、まず全BLE広告を受信する。
         */
        callback = new ScanCallback() {

            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                process(result);
            }

            @Override
            public void onBatchScanResults(
                    java.util.List<ScanResult> results) {

                for (ScanResult r : results) {
                    process(r);
                }
            }

            @Override
            public void onScanFailed(int errorCode) {
                debug("BLEスキャン失敗: errorCode=" + errorCode);
            }
        };

        scanResults = 0;
        serviceDataResults = 0;
        decryptAttempts = 0;
        decryptSuccesses = 0;
        parseSuccesses = 0;
        lastDebugScanCount = 0;

        scanner.startScan(null, settings, callback);

        debug("BLEスキャン開始");
    }

    @SuppressLint("MissingPermission")
    void stop() {
        if (scanner != null && callback != null) {
            scanner.stopScan(callback);
            callback = null;
        }
    }

    String diagnostics() {
        return String.format(
                Locale.US,
                "BLE結果: %d / MiBeacon広告: %d / 復号成功: %d / S400解析成功: %d",
                scanResults,
                serviceDataResults,
                decryptSuccesses,
                parseSuccesses
        );
    }

    private void process(ScanResult result) {
        scanResults++;

        if (result.getScanRecord() == null) {
            debugThrottled();
            return;
        }

        byte[] frame =
                result.getScanRecord().getServiceData(MIBEACON_UUID);

        if (frame == null) {
            debugThrottled();
            return;
        }

        serviceDataResults++;
        decryptAttempts++;

        byte[] plain = decryptor.decrypt(frame);

        if (plain == null || plain.length == 0) {
            debugThrottled();
            return;
        }

        decryptSuccesses++;

        S400Measurement measurement = parser.parse(plain);

        if (measurement != null) {
            parseSuccesses++;
            onMeasurement.accept(measurement);
        }

        debugThrottled();
    }

    private void debugThrottled() {
        if (scanResults == 1 || scanResults >= lastDebugScanCount + 25) {
            lastDebugScanCount = scanResults;
            debug(diagnostics());
        }
    }

    private void debug(String message) {
        if (onDebug != null) {
            onDebug.accept(message);
        }
    }
}