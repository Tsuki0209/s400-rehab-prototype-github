package jp.rehab.s400;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.ParcelUuid;

import java.util.function.Consumer;

final class BleScanner {
    static final ParcelUuid MIBEACON_UUID = ParcelUuid.fromString("0000fe95-0000-1000-8000-00805f9b34fb");

    private final BluetoothLeScanner scanner;
    private final MiBeaconDecryptor decryptor;
    private final S400Parser parser = new S400Parser();
    private final Consumer<S400Measurement> onMeasurement;
    private ScanCallback callback;

    BleScanner(Context context, MiBeaconDecryptor decryptor, Consumer<S400Measurement> onMeasurement) {
        BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = manager.getAdapter();
        scanner = adapter != null ? adapter.getBluetoothLeScanner() : null;
        this.decryptor = decryptor;
        this.onMeasurement = onMeasurement;
    }

    @SuppressLint("MissingPermission")
    void start() {
        if (scanner == null) throw new IllegalStateException("BLE scanner unavailable");
        ScanFilter filter = new ScanFilter.Builder().setServiceData(MIBEACON_UUID, null).build();
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setReportDelay(0)
                .build();
        callback = new ScanCallback() {
            @Override public void onScanResult(int callbackType, ScanResult result) { process(result); }
            @Override public void onBatchScanResults(java.util.List<ScanResult> results) { for (ScanResult r : results) process(r); }
        };
        scanner.startScan(java.util.Collections.singletonList(filter), settings, callback);
    }

    @SuppressLint("MissingPermission")
    void stop() {
        if (scanner != null && callback != null) {
            scanner.stopScan(callback);
            callback = null;
        }
    }

    private void process(ScanResult result) {
        byte[] frame = result.getScanRecord() == null ? null : result.getScanRecord().getServiceData(MIBEACON_UUID);
        if (frame == null) return;
        byte[] plain = decryptor.decrypt(frame);
        if (plain == null || plain.length == 0) return;
        S400Measurement measurement = parser.parse(plain);
        if (measurement != null) onMeasurement.accept(measurement);
    }
}
