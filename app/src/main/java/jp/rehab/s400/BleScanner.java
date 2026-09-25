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
            ParcelUuid.fromString(
                    "0000fe95-0000-1000-8000-00805f9b34fb"
            );

    private final BluetoothLeScanner scanner;
    private final MiBeaconDecryptor decryptor;
    private final S400Parser parser =
            new S400Parser();

    private final Consumer<S400Measurement> onMeasurement;
    private final Consumer<String> onDebug;

    private ScanCallback callback;

    private int scanResults;
    private int serviceDataResults;
    private int decryptSuccesses;
    private int parseSuccesses;

    private int lastDebugScanCount;

    private boolean encryptedFrameShown;

    private String lastDecryptError = "";
    private String encryptedFrameInfo = "";

    BleScanner(
            Context context,
            MiBeaconDecryptor decryptor,
            Consumer<S400Measurement> onMeasurement,
            Consumer<String> onDebug) {

        BluetoothManager manager =
                (BluetoothManager)
                        context.getSystemService(
                                Context.BLUETOOTH_SERVICE
                        );

        BluetoothAdapter adapter =
                manager.getAdapter();

        scanner =
                adapter != null
                        ? adapter.getBluetoothLeScanner()
                        : null;

        this.decryptor = decryptor;
        this.onMeasurement = onMeasurement;
        this.onDebug = onDebug;
    }

    @SuppressLint("MissingPermission")
    void start() {

        if (scanner == null) {
            throw new IllegalStateException(
                    "BLE scanner unavailable"
            );
        }

        ScanSettings settings =
                new ScanSettings.Builder()
                        .setScanMode(
                                ScanSettings.SCAN_MODE_LOW_LATENCY
                        )
                        .setReportDelay(0)
                        .build();

        callback =
                new ScanCallback() {

                    @Override
                    public void onScanResult(
                            int callbackType,
                            ScanResult result) {

                        process(result);
                    }

                    @Override
                    public void onBatchScanResults(
                            java.util.List<ScanResult> results) {

                        for (ScanResult result : results) {
                            process(result);
                        }
                    }

                    @Override
                    public void onScanFailed(
                            int errorCode) {

                        debug(
                                "BLEスキャン失敗: errorCode="
                                        + errorCode
                        );
                    }
                };

        scanResults = 0;
        serviceDataResults = 0;
        decryptSuccesses = 0;
        parseSuccesses = 0;

        lastDebugScanCount = 0;

        encryptedFrameShown = false;

        lastDecryptError = "";
        encryptedFrameInfo = "";

        scanner.startScan(
                null,
                settings,
                callback
        );

        debug("BLEスキャン開始");
    }

    @SuppressLint("MissingPermission")
    void stop() {

        if (scanner != null
                && callback != null) {

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

    private void process(
            ScanResult result) {

        scanResults++;

        if (result.getScanRecord() == null) {
            debugThrottled();
            return;
        }

        byte[] frame =
                result
                        .getScanRecord()
                        .getServiceData(
                                MIBEACON_UUID
                        );

        if (frame == null) {
            debugThrottled();
            return;
        }

        serviceDataResults++;

        MiBeaconDecryptor.Result decoded =
                decryptor.decryptDetailed(frame);

        /*
         * 非暗号化フレームは測定フレームではない場合がある。
         * 今回はEncrypted=1のフレームを優先して診断する。
         */
        if (decoded.encrypted
                && !encryptedFrameShown) {

            encryptedFrameShown = true;

            String deviceMac =
                    result.getDevice() != null
                            ? result
                                    .getDevice()
                                    .getAddress()
                            : "?";

            encryptedFrameInfo =
                    String.format(
                            Locale.US,

                            "暗号化MiBeacon:" +
                            "\n端末MAC=%s" +
                            "\nFE95長=%d" +
                            "\nFC=0x%04X" +
                            "\nv=%d" +
                            "\nPID=0x%04X" +
                            "\ncnt=0x%02X" +
                            "\nobj=%d" +
                            "\ncap=%d" +
                            "\nmac=%d" +
                            "\nenc=%d" +
                            "\nnonceMAC=%s" +
                            "\next=%s" +
                            "\nraw=%s" +
                            "\nerr=%s",

                            deviceMac,

                            frame.length,

                            decoded.frameControl,

                            decoded.version,

                            decoded.productId,

                            decoded.frameCounter,

                            decoded.objectIncluded
                                    ? 1
                                    : 0,

                            decoded.capabilityIncluded
                                    ? 1
                                    : 0,

                            decoded.macIncluded
                                    ? 1
                                    : 0,

                            decoded.encrypted
                                    ? 1
                                    : 0,

                            decoded.nonceMacHex.isEmpty()
                                    ? "-"
                                    : decoded.nonceMacHex,

                            decoded.extCounterHex.isEmpty()
                                    ? "-"
                                    : decoded.extCounterHex,

                            MiBeaconDecryptor.toHex(
                                    frame
                            ),

                            decoded.error == null
                                    ? "OK"
                                    : decoded.error
                    );

            debug(encryptedFrameInfo);
        }

        /*
         * 復号失敗
         */
        if (!decoded.success()) {

            if (decoded.encrypted) {
                lastDecryptError =
                        decoded.error == null
                                ? "unknown"
                                : decoded.error;
            }

            debugThrottled();
            return;
        }

        /*
         * 復号成功
         */
        decryptSuccesses++;

        S400Measurement measurement =
                parser.parse(
                        decoded.plaintext
                );

        if (measurement != null) {

            parseSuccesses++;

            onMeasurement.accept(
                    measurement
            );
        }

        debugThrottled();
    }

    private void debugThrottled() {

        if (scanResults == 1
                || scanResults
                >= lastDebugScanCount + 25) {

            lastDebugScanCount =
                    scanResults;

            String detail =
                    encryptedFrameInfo.isEmpty()
                            ? ""
                            : "\n"
                              + encryptedFrameInfo;

            String error =
                    lastDecryptError.isEmpty()
                            ? ""
                            : "\n復号エラー: "
                              + lastDecryptError;

            debug(
                    diagnostics()
                    + detail
                    + error
            );
        }
    }

    private void debug(
            String message) {

        if (onDebug != null) {
            onDebug.accept(message);
        }
    }
}