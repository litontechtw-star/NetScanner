# NetScanner Pro — 取得 APK 安裝檔指南

## 方法一：GitHub Actions（最簡單，推薦）

### 步驟

1. **在 GitHub 建立新 Repository**
   - 前往 https://github.com/new
   - Repository name：`NetScanner`
   - 選 Public 或 Private 皆可
   - 點擊「Create repository」

2. **上傳此專案**
   - 打開電腦上的 Terminal（命令提示字元 / PowerShell）
   - 進入此 `NetScanner` 資料夾
   ```bash
   cd "D:\AI\APP\手機網路檢測工具\NetScanner"
   git init
   git add .
   git commit -m "Initial commit: NetScanner Pro"
   git branch -M main
   git remote add origin https://github.com/你的帳號/NetScanner.git
   git push -u origin main
   ```

3. **等待自動建置**
   - 推送後約 3~5 分鐘，GitHub 會自動編譯 APK
   - 前往 `https://github.com/你的帳號/NetScanner/actions`
   - 點擊最新的 workflow run

4. **下載 APK**
   - 在 workflow 頁面最下方找到 **Artifacts** 區塊
   - 點擊 `NetScanner-Debug-APK` 下載 ZIP
   - 解壓縮後得到 `app-debug.apk`

5. **安裝到手機**
   - 將 APK 傳到手機（USB / Google Drive / Line）
   - 手機設定 → 安全性 → 允許安裝未知來源
   - 點擊 APK 安裝

---

## 方法二：Android Studio（本機建置）

### 需要
- [Android Studio](https://developer.android.com/studio)（免費下載）
- 約 8GB 磁碟空間

### 步驟
1. 安裝 Android Studio
2. 開啟 Android Studio → `Open` → 選擇此 `NetScanner` 資料夾
3. 等待 Gradle sync 完成（首次約 5~10 分鐘，需要網路）
4. 點選 `Build` → `Build Bundle(s) / APK(s)` → `Build APK(s)`
5. APK 會儲存在 `app/build/outputs/apk/debug/app-debug.apk`

---

## APP 功能說明

### 📡 測速（Speed Test）
- **延遲測試**：測量到 Cloudflare DNS 的 RTT，顯示 Ping 與 Jitter
- **下載速度**：從 Cloudflare / OVH CDN 下載 25MB 檔案計算速度
- **上傳速度**：向 Cloudflare 上傳 10MB 資料計算速度
- 即時動態速度儀表盤（霓虹特效）

### 📶 WiFi 掃描（WiFi Scanner）
- 掃描並列出附近所有 WiFi 熱點
- 顯示 SSID、BSSID、訊號強度（dBm）、頻率、頻道、加密方式
- 支援 2.4GHz / 5GHz / 6GHz 顯示
- 雷達掃描動畫效果
- 依訊號強度 / 名稱 / 頻段排序

### 📊 訊號品質（Signal Quality）
- 即時顯示目前連線 WiFi 的訊號強度
- RSSI 歷史折線圖（最近 60 筆）
- 連結速度（Mbps）、TX/RX 速率
- IP 位址、頻率、頻道資訊
- 動態脈衝訊號指示器

---

## 所需權限說明

| 權限 | 用途 |
|------|------|
| INTERNET | 網路測速下載/上傳 |
| ACCESS_WIFI_STATE | 讀取 WiFi 資訊 |
| CHANGE_WIFI_STATE | 觸發 WiFi 掃描 |
| ACCESS_FINE_LOCATION | Android 9+ WiFi 掃描必需 |
| NEARBY_WIFI_DEVICES | Android 13+ WiFi 掃描必需 |

---

## 相容性

- 最低支援：Android 7.0 (API 24)
- 目標版本：Android 14 (API 34)
- 架構：arm64-v8a, armeabi-v7a, x86_64
