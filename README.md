# 📱 WebServer for Android – The Ultimate Lightweight Local File Sharing & Web Hosting Solution

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Android API](https://img.shields.io/badge/API-21%2B-brightgreen)](https://developer.android.com/reference/android/os/Build.VERSION_CODES#LOLLIPOP)
[![Size](https://img.shields.io/badge/size-%3C0.5%20MB-blue)](https://github.com/toptstudio/WebServer-Android)
[![Download APK](https://img.shields.io/badge/Download-APK-brightgreen)](https://github.com/toptstudio/WebServer-Android/releases/download/v1.0.0/WebServer-v1.0.0.apk)
[![Platform](https://img.shields.io/badge/platform-Android-blue)](https://developer.android.com)

**The smallest, fastest, and most feature‑rich web server for Android.**  
Serve files, stream video and music, host static websites, share games, wallpapers, and any documents – all over Wi‑Fi or hotspot. **No internet required. No cloud. No limits.**

## 📱 Android Version Support – From Android 5.0 to Android 16+

- **Minimum SDK:** API 21 (Android 5.0 Lollipop) – works on devices released from 2014 onward, including old phones, tablets, and Android TV boxes.
- **Target SDK:** API 36 (Android 16) – fully compatible with the latest Android versions, including betas and future releases.
- **Why such a wide range?** The app uses only standard Android APIs and pure Java sockets. No deprecated libraries, no forced updates, no compatibility wrappers. It will keep working on new Android versions without modification.
- **Optimised for low‑RAM devices** – automatically adjusts buffer sizes based on available memory. Works flawlessly on devices with 512 MB RAM (Android Go), 1 GB, 2 GB, and up.
- **No Google Play Services required** – pure open source, no tracking, no analytics.
toptstudio
## 🔍 What Makes This App Unique? (Complete Feature List)

| Category | Feature | Detailed Description |
|----------|---------|----------------------|
| **Size** | APK < 0.5 MB | The entire app is under half a megabyte. Installs in seconds, uses almost no storage. Perfect for devices with limited space. |
| **Upload** | Chunked resumable uploads | Large files are split into 10 MB chunks. If the connection drops or the app is closed, only missing chunks are retransmitted. No restart from zero. |
| **Upload** | 100 GB+ file support | Tested with files up to 100 gigabytes. No artificial limits. The chunked engine handles any size that fits your storage. |
| **Download** | Single file or full folder as ZIP | Click the download button next to any file to get it. Click the folder download button to instantly pack the entire folder into a ZIP archive and stream it to your browser. |
| **Streaming** | Video streaming | Supports MP4, MKV, AVI, MOV, WebM, M4V, 3GP, and more. Browser plays natively. |
| **Streaming** | Audio streaming | Supports MP3, FLAC, OGG, WAV, M4A, AAC, Opus, and many others. |
| **Streaming** | Image preview | JPEG, PNG, GIF, SVG, WebP, BMP, TIFF – view directly in browser. |
| **Web hosting** | Static HTML/CSS/JS | Upload an entire website (e.g., portfolio, documentation, single‑page app). Click any `.html` file – it renders as a real webpage. |
| **File management** | Delete (authenticated) | When authentication is enabled, users can delete files or folders via the web UI. |
| **File management** | Rename (authenticated) | Change file or folder names directly from the browser. |
| **File management** | Create folder (authenticated) | Create new subfolders remotely. |
| **File management** | Upload (authenticated) | Drag & drop files from your computer or phone browser. Supports chunked mode for large uploads. |
| **Authentication** | Basic HTTP auth | Set a username and password via the “SECURE” button in the app. When enabled, the web UI shows action buttons (delete, rename, upload, create folder). |
| **Discovery** | mDNS (Bonjour) | Automatically advertises the server on the local network. Other devices running the app can find it without typing IP/port. |
| **Discovery** | One‑tap browser open | Tap a discovered server in the list – the app opens your browser directly to that server’s address. |
| **Discovery** | System notifications | When a new server is discovered, you receive a notification. Tap it to open the web UI instantly. |
| **Discovery** | Background service | Discovery runs as a foreground service. It can continue even when the app is closed (you can stop it anytime). |
| **Network** | Wi‑Fi client mode | All devices connected to the same router can see each other (if AP isolation is disabled). |
| **Network** | Hotspot (access point) mode | Start a hotspot on your Android. Other devices connect and access the server via the gateway IP (e.g., `192.168.43.1`). No internet needed. |
| **Network** | Cellular fallback | If Wi‑Fi is off, the server binds to the cellular IP. Useful for USB tethering or direct 5G sharing. |
| **Network** | No internet required | All traffic stays local. Your files never leave the room. No data usage. |
| **Performance** | High‑speed mode | Disables Nagle’s algorithm, increases socket buffers, and reduces latency for large transfers. |
| **Performance** | Adaptive buffer size | The app measures available RAM and sets optimal read/write buffers (64 KB – 4 MB). |
| **Security** | HTTPS | Built‑in self‑signed certificate (generated at build time). Toggle HTTPS on/off from the main screen. |
| **Security** | Root port forwarding | If your device is rooted, the app can forward ports 80 and 443 to your chosen port, allowing standard web ports. |
| **Storage** | Storage Access Framework (SAF) | Pick any folder on internal or external storage (SD card, USB drive) without legacy permissions. |
| **Storage** | Legacy storage fallback | For older Android versions or full file access, uses traditional file paths. |
| **Resilience** | Resumable after crash | The chunked upload engine stores temporary files on SD card. If the app or device crashes, uploads resume from the last complete chunk. |
| **UI** | Dark / Light theme | Automatically follows system theme. All screenshots show both modes. |
| **UI** | Responsive web interface | Works on phones, tablets, laptops, desktops. Uses CSS grid and flexbox. |
| **Extras** | Share games | Upload APK files or game ROMs. Others download and install directly. |
| **Extras** | Share wallpapers | Browse high‑resolution images and save them to your device. |
| **Extras** | Share documents | PDF, Office files, e‑books, etc. – the browser’s built‑in viewer handles many formats. |
| **Extras** | mDNS service type | Registers `_webserver._tcp` so other instances can discover it. |

## 🌐 Web UI Features – Public vs. Secure Mode

### 🔓 Public Mode (Authentication Disabled – Default)
- **Browse** any folder.
- **Download** any file with one click.
- **Download entire folder as ZIP** – the server compresses on the fly.
- **Inline preview** for images, videos, audio, text, HTML, PDF.
- **No delete, rename, upload, or create folder** – visitors cannot modify your files.

### 🔒 Secure Mode (Authentication Enabled via “SECURE” Button)
Once you set a username and password in the app, the web UI shows **additional action buttons** for authenticated users:

| Action | Button | Description |
|--------|--------|-------------|
| Delete | 🗑️ | Remove a file or folder. |
| Rename | 📝 | Change the name of a file or folder. |
| Upload | 📤 | Upload files (drag & drop or file picker). Supports chunked resumable upload for large files. |
| Create folder | 📁 | Create a new subfolder. |

All public features (browse, download, ZIP, preview) remain available.

> **Why this design?** Without authentication, the web UI is read‑only – safe for public sharing. With authentication, you get full remote file management from any browser.

## 🔍 Automatic Discovery & Notifications – No More Typing IP Addresses

- **Find Servers button** – Tap it on any device running WebServer. The app scans the local network using mDNS and HTTP probes.
- **One‑click browser open** – Discovered servers appear in a list. Tap any entry – the app opens your browser directly to that server’s IP and port. **No manual address entry.**
- **System notifications** – When a new server is found, you receive a notification. Tap the notification to instantly open the web UI of that remote server.
- **Background discovery service** – The discovery runs as a foreground service. Even when the app is closed, discovery can continue (you can stop it via the “Stop Discovery” button).
- **Manual fallback** – If automatic discovery fails (e.g., router isolates multicast), you can still enter the IP and port manually in your browser.

> 💡 **Perfect for quick file sharing between friends:** both install the app, one starts the server, the other taps “Find Servers” – and connects in seconds.

## 🌐 Network Support – Wi‑Fi, Hotspot, Cellular

- **Wi‑Fi client mode** – All devices on the same router can see each other if AP isolation is disabled. Most home networks allow this.
- **Hotspot (access point) mode** – Start a hotspot on your Android. Other devices connect to your hotspot and access the server via the gateway IP (usually `192.168.43.1` or `192.168.0.1`). Works without any internet connection. Perfect for outdoor sharing.
- **Cellular fallback** – If Wi‑Fi is off, the server binds to the cellular IP. This is useful for USB tethering or direct 5G sharing between two phones (using a cable or Wi‑Fi Direct).
- **No internet required** – All traffic stays local. Your files never leave the room. No data usage. No privacy concerns.

**Discovery across hotspot:** mDNS may be limited on some Android hotspot implementations. However, the app’s manual discovery (Find Servers) probes the entire subnet, so it will find other instances even if mDNS is blocked. You can also enter the IP address directly.

## ⚡ Performance & Efficiency – Why It’s So Fast

- **Ultra‑lightweight** – Entire APK is **under 0.5 MB**. Compare to other web servers that are 5–20 MB. No bloated frameworks (no Retrofit, OkHttp, Volley, etc.). Pure Java sockets.
- **Chunked upload engine** – Files are split into 10 MB chunks. The server keeps a manifest of received chunks. If connection cuts, only missing chunks are re‑sent.
- **Optimised for unstable networks** – The temporary folder for chunks is stored on SD card (or internal storage). Even if the app crashes or the device reboots, uploads can resume.
- **High‑speed mode** – Disables Nagle’s algorithm (TCP_NODELAY), increases send/receive buffers (up to 256 KB), and reduces latency for many small packets.
- **Adaptive buffer size** – The app checks total RAM and available RAM on start. On low‑RAM devices (≤1 GB), buffer size is 64 KB; on high‑RAM devices (≥6 GB), buffer size can be up to 512 KB. This prevents out‑of‑memory errors.
- **Concurrent connection limit** – The server limits active connections to 20 to avoid exhausting file descriptors.
- **No database, no caching** – Direct file serving. Minimal overhead.

## 🎬 Media Streaming & Web Hosting – Real HTTP Server

Because this is a **real HTTP/HTTPS server** implementing RFC 2616 (HTTP/1.0 and 1.1), any browser can:

- **Play videos** – MP4, MKV, AVI, MOV, WebM, M4V, 3GP, etc. (browser codec support varies). The server sends proper `Content-Type` headers based on file extension (MimeMap.java has over 500 mime types).
- **Play music** – MP3, FLAC, OGG, WAV, M4A, AAC, Opus, WebM audio – stream directly without downloading.
- **View images** – JPEG, PNG, GIF, SVG, WebP, BMP, TIFF, even RAW (browser dependent).
- **Render HTML websites** – Upload an entire static site (HTML, CSS, JS, images, fonts) into a folder. Navigate to that folder and click any `.html` file – it will render as a webpage. All relative links work because the server resolves paths correctly.
- **Download games & apps** – Share APK files, game ROMs (e.g., NES, SNES, PlayStation), Windows installers, Linux packages. Users just tap to download.
- **Set wallpapers** – Browse high‑resolution images (e.g., 4K wallpapers) and save them to your device using the browser’s “Save image as”.
- **View PDFs and Office documents** – The browser’s built‑in PDF viewer and Office Online (if available) can handle these files. The server sends the correct mime types (`application/pdf`, `application/msword`, etc.).

The web UI is fully responsive and works on phones, tablets, laptops, and desktops – **no client app required**. The UI includes:
- A file browser with sorting (folders first, then files alphabetically).
- Download buttons for each file and folder (ZIP).
- Action buttons (delete, rename, upload, create folder) when authentication is enabled.
- A modern CSS grid layout with dark/light mode support.
- JavaScript for chunked uploads, progress indicators, and duplicate handling.

## 📸 Screenshot Gallery – Dark Mode, Light Mode, and Mixed

### 🌗 Mixed Mode (both dark and light) – #11
This image shows the app’s main screen in the default mixed theme (some elements light, some dark).

<div align="center">
  <img src="docs/screenshots/11_DW.jpg" width="320">
  <br><em>The app’s main screen in default mixed theme.</em>
</div>

<br>

### 🌙 Dark Mode (DD) vs ☀️ Light Mode (WW)
The following table compares pure Dark Mode (DD series) on the left and pure Light Mode (WW series) on the right.

| Dark Mode | Light Mode |
|:---------:|:----------:|
| **#12**<br><img src="docs/screenshots/12_DD.jpg" width="240"> | **#13**<br><img src="docs/screenshots/13_WW.jpg" width="240"> |
| **#14**<br><img src="docs/screenshots/14_DD.jpg" width="240"> | **#15**<br><img src="docs/screenshots/15_WW.jpg" width="240"> |
| **#16**<br><img src="docs/screenshots/16_DD.jpg" width="240"> | **#17**<br><img src="docs/screenshots/17_WW.jpg" width="240"> |
| **#18**<br><img src="docs/screenshots/18_DD.jpg" width="240"> | **#19**<br><img src="docs/screenshots/19_WW.jpg" width="240"> |
| **#20**<br><img src="docs/screenshots/20_DD.jpg" width="240"> | **#21**<br><img src="docs/screenshots/21_WW.jpg" width="240"> |
| **#22**<br><img src="docs/screenshots/22_DD.jpg" width="240"> | **#23**<br><img src="docs/screenshots/23_WW.jpg" width="240"> |
| **#24**<br><img src="docs/screenshots/24_DD.jpg" width="240"> | **#25**<br><img src="docs/screenshots/25_WW.jpg" width="240"> |
| **#26**<br><img src="docs/screenshots/26_DD.jpg" width="240"> | **#27**<br><img src="docs/screenshots/27_WW.jpg" width="240"> |
| **#28**<br><img src="docs/screenshots/28_DD.jpg" width="240"> | **#29**<br><img src="docs/screenshots/29_WW.jpg" width="240"> |
| **#30**<br><img src="docs/screenshots/30_DD.jpg" width="240"> | **#31**<br><img src="docs/screenshots/31_WW.jpg" width="240"> |

*All screenshots are original and show the app’s web interface, Android settings, and discovery dialogs.*

## 🚀 Quick Start – Install and Run in 60 Seconds

1. **Download the APK** from the [Releases page](https://github.com/toptstudio/WebServer-Android/releases). The file is named `Web Server.apk`.
2. **Install** it on your Android device (enable “Install from unknown sources” if needed).
3. **Grant permissions** when prompted: storage (to access your files) and notifications (for discovery alerts).
4. **Pick a folder** to share – tap “Change” and select a directory using the built‑in folder picker (SAF or legacy).
5. **Set a port** (default 9999). If you want to use port 80 or 443, your device must be rooted – the app will automatically forward those ports if root is available.
6. **Tap “Start Server”**. The server will start, and the IP address will appear (e.g., `192.168.1.100`).
7. **On another device** (phone, tablet, laptop, desktop), open a web browser and go to `http://<your-phone-ip>:9999`.
   - Alternatively, if the other device has the same app installed, tap “Find Servers” – it will discover your server automatically. Tap the discovered entry to open the browser.
8. **Share files** – browse, download, upload (if authentication enabled), stream media, or host websites.

## 📱 Usage Tips – Get the Most Out of the App

- **Finding your IP address** – The app displays the server IP on the main screen. If you’re using a hotspot, the IP is usually the hotspot gateway (e.g., `192.168.43.1` or `192.168.0.1`). You can also find it in Android settings under “Hotspot & tethering”.
- **Uploading very large files ( >2 GB )** – Enable **Chunks Mode** in the web UI (toggle switch). The app will then upload the file in 10 MB chunks. Even if the connection drops, the upload will resume from the last completed chunk. The temporary chunk directory is stored on your SD card or internal storage.
- **Streaming media** – Just tap any video or music file in the web interface. Your browser will play it directly (provided the codec is supported). For best results, use modern browsers like Chrome, Firefox, Edge, or Safari.
- **Hosting a website** – Create a folder on your device, put your HTML/CSS/JS files inside (including an `index.html`). In the app, share that folder. On the client browser, navigate to the folder and click `index.html`. The browser will render the website. All relative paths (images, CSS, JS) will work.
- **Sharing a game** – Upload an APK file. On the client, click the APK to download it. Then install it normally. Works for any file type.
- **Setting a wallpaper** – Browse the images folder, tap any high‑resolution image. On most browsers, you can long‑press the image and select “Save image”. Then set as wallpaper from your gallery.
- **Downloading a whole folder as ZIP** – Click the download icon (📥) next to any folder. The server will pack the folder’s entire contents into a ZIP archive and stream it to your browser. No temporary file is created on the server.
- **Enabling authentication** – Tap the “SECURE” button in the app. Enter a username and password. From now on, the web UI will require these credentials. Once logged in, you will see delete, rename, upload, and create folder buttons.
- **Disabling authentication** – Go back to the “SECURE” dialog and clear both username and password fields. Save. The web UI will revert to read‑only mode.
- **Stopping the server** – Tap “Stop Server” in the app. You can also stop it from the notification (if running as foreground).
- **Discovery** – Tap “Find Servers”. The app will scan the local network for other WebServer instances. Discovered servers appear in a list. Tap any to open its web UI directly. You will also receive notifications for new servers.

## 🔐 Permissions – Why They Are Needed and How They Are Used

| Permission | Requested on | Reason |
|------------|--------------|--------|
| `INTERNET` | Always | Required to run the web server and accept incoming connections. |
| `FOREGROUND_SERVICE` | Android 8+ | Allows the server to run in the background without being killed. The app shows an ongoing notification. |
| `POST_NOTIFICATIONS` | Android 13+ | Used to show server status notifications and discovery alerts. The app does not send any other notifications. |
| `MANAGE_EXTERNAL_STORAGE` | Android 11+ (optional) | Grants full file access. This permission is **not required** if you use SAF (Storage Access Framework). The app works perfectly without it by using SAF. |
| `ACCESS_WIFI_STATE`, `CHANGE_WIFI_MULTICAST_STATE` | Android 5+ | Needed for mDNS discovery (to find other servers on the same Wi‑Fi). |
| `RECEIVE_BOOT_COMPLETED` | Android 5+ | Optional – if you want the server to auto‑start after device reboot. You must enable it in the app’s settings. |
| `WRITE_EXTERNAL_STORAGE` (max SDK 29) | Android 5–9 | Legacy permission for devices before Android 10. Not used on newer Android versions. |
| `ACCESS_COARSE_LOCATION` (max SDK 30) | Android 8–12 | Required for Wi‑Fi scanning to get SSID (used by mDNS). The app does not use GPS. |
| `NEARBY_WIFI_DEVICES` (min SDK 31) | Android 12+ | Modern replacement for location permission to access Wi‑Fi information. |

**No data leaves your device. No analytics, no tracking, no cloud services. All network traffic is local to your Wi‑Fi or hotspot.**

## 🤝 Contributing – Join the Open Source Community

This project is 100% open source (MIT license). Contributions are welcome!

- **Report bugs** – Open an issue on GitHub with steps to reproduce.
- **Suggest features** – Open an issue with the “enhancement” label.
- **Submit pull requests** – Fork the repo, create a branch, commit your changes, and open a PR.
- **Improve documentation** – The README, BUILD.md, or inline code comments.

### Building from Source

See [`BUILD.md`](BUILD.md) for detailed instructions. In short:

1. Clone the repo: `git clone https://github.com/toptstudio/WebServer-Android.git`
2. Place `android.jar` (from Android SDK) and `r8.jar` (R8 dexer) in the project root.
3. Generate a keystore: `keytool -genkey -v -keystore webserver_signing.keystore -alias server -keyalg RSA -keysize 2048 -validity 10000 -storepass android -keypass android`
4. Run `./build.sh`
5. The APK will be at `bin/app-debug.apk`.

## 📜 License – MIT

Copyright (c) 2026 ToptStudio

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the “Software”), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

## 📬 Contact & Support

- **GitHub Issues:** [https://github.com/toptstudio/WebServer-Android/issues](https://github.com/toptstudio/WebServer-Android/issues)
- **Email:** toptstudio@gmail.com
- **Project Homepage:** [https://github.com/toptstudio/WebServer-Android](https://github.com/toptstudio/WebServer-Android)

**If you find this app useful, please ⭐ star the repository and share it with others!**

---

*Built with ❤️ for the open source community. No cloud, no tracking, just pure local file sharing.*
