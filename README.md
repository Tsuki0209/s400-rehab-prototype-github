# S400 Rehab Prototype

Xiaomi Body Composition Scale S400 (MJTZC01YM) を直接BLE受信するAndroid試作です。

## 試作範囲

- Xiaomi MiBeacon 0xFE95 をBLEスキャン
- S400の暗号化広告をローカルでAES-CCM復号
- S400 object `0x6E16` を解析
- 重量 / インピーダンス / 心拍 / device timestamp を表示
- 計測完了時に端末内CSVへ保存
- Androidのファイル保存画面からCSVを書き出し
- S400 MAC / BLE bindkey を設定画面から入力
- ネットワーク通信なし。サーバー同期・動画/姿勢同期はまだ未実装

## ビルド

GitHub Actions に `build-apk.yml` を入れているので、リポジトリへpushすれば debug APKをビルドできます。

ローカルでAndroid SDK/Gradleを用意している場合は、Gradle 8.9系で `assembleDebug` を実行してください。

## S400接続

1. S400をXiaomi Homeに追加し、最低1回は完全な計測を行う。
2. Xiaomi CloudからスケールのMACアドレスとBLE bindkeyを取得する。
3. Xiaomi Homeを完全終了する。
4. このアプリの「S400設定 / 接続情報」にMACと32桁hex bindkeyを入力する。
5. Bluetoothを有効にし、「測定開始」を押す。
6. スケールに乗り、計測を完了する。
7. 重量等が表示され、重量+インピーダンスが揃った時点でCSVに保存する。

bindkeyやトークンをチャット、Git、スクリーンショット、ログへ貼り付けないでください。

## 注意

この試作はMiBeacon広告方式を使います。Xiaomi Homeが接続中だと広告取得がうまくいかない場合があります。

体脂肪率などの体組成値はこの版ではまだ生成していません。まず生データ取得とCSV保存を安定させ、その後、プロフィール依存の推定値と姿勢/動画タイムラインを追加する想定です。
