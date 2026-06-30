# WebServer-Android

**The #1 offline web server APK by Topt Studio – under 0.5 MB.**

Turn any Android phone into a **full HTTP/HTTPS server**.  
Offline file sharing, 4K streaming, static hosting.  
Zero‑config, no internet, no cloud, no sign‑ups.

[![Download APK](https://img.shields.io/badge/Download%20APK-0.5MB-blue)](https://github.com/toptstudio/WebServer-Android/releases/latest)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-5.0%2B-brightgreen)](https://www.android.com/)

---

## 📱 Android Version Support

Runs on Android 5.0 Lollipop up to the latest Android 16+ (2026).

- **Min SDK 21** – backward compatible, works on 2014+ phones, tablets, TV boxes.
- **Target SDK 36** – optimized for Android 16 and future releases. Pure Java sockets.
- **Low‑RAM Ready** – adaptive buffers, runs smoothly on 512 MB RAM (Android Go).
- **No Google Services** – zero analytics, zero tracking, zero cloud dependencies.

---

## ⚙️ Intelligent Core Engine

Raw Java sockets, zero external libraries. Built for speed, resilience, and security.

- **Adaptive Performance** – dynamic buffer sizing (64 KB – 4 MB) based on available RAM.
- **Resumable Uploads** – 10 MB chunks, persistent progress manifests. Survives crashes.
- **Enterprise Security** – HTTP Basic Auth with thread‑safe password handling.
- **mDNS Discovery** – Bonjour/Zeroconf auto‑advertising, foreground service.
- **Smart Deduplication** – SHA‑256 hashing + slice sampling for large files.
- **Built‑in HTTPS** – one‑tap toggle, pre‑embedded self‑signed certificate.

---

## ✅ Complete Feature List

| Category     | Feature                        | Description                                       |
|--------------|--------------------------------|---------------------------------------------------|
| Size         | APK < 0.5 MB                   | Installs in seconds, perfect for limited storage. |
| Upload       | Resumable Chunked Uploads      | 10 MB chunks, only missing parts retransmitted.   |
| Upload       | 100 GB+ File Support           | No artificial size limits.                        |
| Download     | Folder as ZIP                  | Streaming ZIP archive of entire folder.           |
| Media        | 4K Video Streaming             | MP4, MKV, AVI, MOV, WebM, etc.                    |
| Media        | High‑Resolution Audio          | MP3, FLAC, OGG, WAV, AAC, Opus streaming.        |
| Media        | Image Preview                  | JPEG, PNG, GIF, SVG, WebP, BMP, TIFF.             |
| Web Hosting  | Static HTML/CSS/JS             | Full website hosting with relative paths.         |
| File Mgmt    | Delete, Rename, Create Folder, Upload | Authenticated remote management via web UI. |
| Security     | Basic HTTP Auth                | Secure mode unlocks management actions.           |
| Discovery    | mDNS / Find Servers            | One‑tap browser open, system notifications.       |
| Network      | Wi‑Fi, Hotspot, Cellular       | Works completely offline, zero data usage.        |
| Performance  | High‑Speed Mode                | Disables Nagle, increases socket buffers.         |
| UI           | Dark/Light Theme               | Follows system preference.                        |
| Extras       | Share APK, Wallpapers, Documents | APK games, 4K wallpapers, PDFs, Office files.   |

---

## 📊 WebServer-Android vs. Others

| Feature                      | Topt Studio       | KSWEB (Paid)      | AWebServer        | Localhost Lite    |
|------------------------------|-------------------|-------------------|-------------------|-------------------|
| APK Size                     | < 0.5 MB          | ~15 MB            | ~10 MB            | ~2 MB             |
| Price                        | Free (MIT)        | Paid/Freemium     | Free              | Free              |
| Resumable Chunked Upload     | ✅ Yes             | ❌ No              | ❌ No              | ❌ No              |
| mDNS Discovery               | ✅ Yes             | ✅ Yes             | ❌ No              | ❌ No              |
| Hotspot Mode                 | ✅ Yes             | ⚠️ Limited        | ❌ No              | ❌ No              |
| HTTPS Built‑in               | ✅ Yes             | ✅ Yes             | ❌ No              | ❌ No              |
| File Mgmt (Del/Rename)       | ✅ Yes             | ✅ Yes             | ❌ No              | ❌ No              |
| Stream ZIP Folders           | ✅ Yes             | ❌ No              | ❌ No              | ❌ No              |
| Android 16+ Support          | ✅ Yes             | ⚠️ Unknown        | ⚠️ Unknown        | ⚠️ Unknown        |
| PHP/MySQL                    | ❌ No (Static)     | ✅ Yes             | ✅ Yes             | ❌ No              |

---

## 🔓🔒 Web UI – Public vs. Secure Mode

- **Public Mode (Default)** – browse, download, stream, view images/PDFs. No modification rights.
- **Secure Mode** – after setting credentials, additional actions appear: Delete, Rename, Upload, Create Folder.

---

## 🌐 Network Support

- **Wi‑Fi client mode** – same router (AP isolation off)
- **Hotspot (Access Point)** – completely offline, no router needed
- **Cellular fallback** – USB tethering, 5G sharing
- **No internet required** – local traffic, zero data usage

---

## ⚡ Performance & Efficiency

- **Ultra‑Lightweight** – pure Java sockets, no external frameworks.
- **Chunked Upload Resilience** – temp storage on SD card, resumes after crash.
- **High‑Speed Mode** – disables Nagle, larger socket buffers.
- **Adaptive Buffer** – 64 KB to 512 KB auto‑tuning.
- **Connection Limit** – capped at 20 to prevent exhaustion.
- **No Database / No Caching** – direct file serving, minimal overhead.

---

## 💡 Real‑World Use Cases

1. **Digital Nomads** – share offline, zero data charges
2. **Developers & QA** – test responsive sites on real devices
3. **Gamers** – host ROMs, APKs for friends
4. **Students & Teachers** – distribute lectures offline
5. **Content Creators** – share 4K footage on set
6. **IoT & Smart Home** – old phone as local dashboard
7. **Offline Documentation** – workshops, labs, remote sites

---

## 🖼️ Screenshot Gallery

### Main screen

<div align="center">
  <img src="docs/screenshots/11_DW.webp" alt="Mixed theme main screen" width="280">
  <p><em>Mixed theme main screen</em></p>
</div>

### Dark Mode vs Light Mode

| Dark Mode                                | Light Mode                               |
|------------------------------------------|------------------------------------------|
| ![Dark file list](docs/screenshots/12_DD.webp) | ![Light root dir](docs/screenshots/13_WW.webp) |
| ![Dark video streaming](docs/screenshots/14_DD.webp) | ![Light video streaming](docs/screenshots/15_WW.webp) |
| ![Dark settings](docs/screenshots/16_DD.webp) | ![Light settings](docs/screenshots/17_WW.webp) |
| ![Dark upload progress](docs/screenshots/18_DD.webp) | ![Light upload progress](docs/screenshots/19_WW.webp) |
| ![Dark auth dialog](docs/screenshots/20_DD.webp) | ![Light auth dialog](docs/screenshots/21_WW.webp) |
| ![Dark mDNS](docs/screenshots/22_DD.webp) | ![Light mDNS](docs/screenshots/23_WW.webp) |
| ![Dark ZIP download](docs/screenshots/24_DD.webp) | ![Light ZIP download](docs/screenshots/25_WW.webp) |
| ![Dark hotspot guide](docs/screenshots/26_DD.webp) | ![Light hotspot guide](docs/screenshots/27_WW.webp) |
| ![Dark file actions](docs/screenshots/28_DD.webp) | ![Light file actions](docs/screenshots/29_WW.webp) |
| ![Dark notification](docs/screenshots/30_DD.webp) | ![Light notification](docs/screenshots/31_WW.webp) |

---

## 🚀 Quick Start

1. Download the APK from [Releases](https://github.com/toptstudio/WebServer-Android/releases)
2. Install & grant storage/notification permissions
3. Pick a folder to share (SAF or legacy)
4. Set port (default `9999`) – root needed for `80`/`443`
5. Tap **Start Server**; note the IP
6. On another device, open `http://<phone-ip>:9999` or use **Find Servers**

---

## ❓ Frequently Asked Questions

**How do I install this Web Server APK on Android 13, 14, or 15?**  
Download the APK from the Releases page. If Google Play Protect warns, tap “More details” → “Install anyway” (the app is 100% safe and open source). On Android 11+, the app will guide you to grant “All files access” using SAF if needed.

**Can I use this Android Web Server without Wi‑Fi?**  
Absolutely! Start a **Hotspot (Access Point)**. Others connect to your Wi‑Fi hotspot and access the server via the gateway IP. No router required.

**Does this WebServer support HTTPS (SSL)?**  
Yes. A self‑signed certificate is built in. Toggle HTTPS on/off from the main screen. When enabled, use `https://` in the browser.

**Is this Web Server App completely free?**  
Yes, 100% free and open source under the MIT License. No fees, no in‑app purchases, no premium versions.

**How do I change the port?**  
Use the “Port” input on the main screen (default 9999). Restart the server after changing. Rooted devices can use port 80 or 443.

**What is the maximum file size I can share?**  
No artificial limits – tested with files up to 100 GB. The chunked engine handles any size that fits your storage, bypassing FAT32's 4 GB limit.

**How do I find the IP address of my Web Server?**  
The app displays both the Wi‑Fi IP and the Hotspot IP (if active) prominently on the main screen.

**Can I stream 4K video?**  
Yes. As long as your network bandwidth supports it (e.g., 5 GHz Wi‑Fi), 4K videos play smoothly in modern browsers.

**What happens if the app crashes during a large upload?**  
The chunked upload engine saves temporary chunks to SD card. When you restart the server and retry, it resumes from the last completed chunk – no data loss.

**How do I uninstall the WebServer APK?**  
Uninstall like any app from Android Settings. Your files remain untouched.

**Does this app drain my battery?**  
Minimal. It uses CPU only when actively serving requests; idle consumption is virtually zero.

**Can I share the APK with my friends directly?**  
Yes, use the “Share” button or host the APK file itself using this server, then send it via Bluetooth, Nearby Share, etc.

**What is the Phone-as-Web-Server concept?**  
It means using a mobile device as a fully functional web server for file serving, website hosting, and network services – without cloud infrastructure. WebServer-Android is a prime example.

**Can I use this for mobile‑first homelab projects?**  
Definitely! Perfect for running lightweight services, dashboards, or APIs on an old Android phone, reducing power and hardware costs.

**How does the resumable upload engine work?**  
Files are split into 10 MB chunks; a progress manifest tracks what's uploaded. Only missing chunks are retransmitted, and all chunks are verified before assembly.

**How does the app handle duplicate files?**  
The deduplication system uses SHA‑256 hashing. For large files, smart three‑slice sampling provides near‑instant identification without hashing the entire file.

---

## 🔧 Troubleshooting Common Issues

- **“Cannot access server from other device”** – verify both devices are on the same network or hotspot. Disable “AP Isolation” on the router. Temporarily turn off firewalls or VPNs.
- **“mDNS Discovery not finding servers”** – some devices block multicast on hotspot. Use “Find Servers” (subnet probe) or manually enter the IP.
- **“Upload fails on large files”** – enable “Chunks Mode” in the web UI and ensure enough free space on SD card for temporary chunks.
- **“HTTPS shows ‘Not Secure’”** – expected with a self‑signed certificate. Click “Advanced” → “Proceed to site” in your browser.
- **“App closes when minimized”** – ensure “Foreground Service” permission is granted. The persistent notification prevents Android from killing the process.

---

## 🔐 Permissions – Why They Are Needed

| Permission                                 | Reason |
|--------------------------------------------|--------|
| INTERNET                                   | Required to run the web server and accept incoming connections. |
| FOREGROUND_SERVICE                         | Allows the server to run in the background with an ongoing notification. |
| POST_NOTIFICATIONS                         | For server status and discovery alerts – no other notifications are sent (Android 13+). |
| MANAGE_EXTERNAL_STORAGE                    | Full file access; not required if SAF is used (Android 11+). |
| ACCESS_WIFI_STATE, CHANGE_WIFI_MULTICAST_STATE | Needed for mDNS discovery. |
| RECEIVE_BOOT_COMPLETED                     | Optional – enables auto‑start on device reboot (must be enabled in settings). |
| WRITE_EXTERNAL_STORAGE (max SDK 29)        | Legacy permission; not used on Android 10+. |
| ACCESS_COARSE_LOCATION (max SDK 30)        | Required for Wi‑Fi scanning (SSID) for mDNS; GPS is never used. |
| NEARBY_WIFI_DEVICES (min SDK 31)           | Modern replacement for location permission to access Wi‑Fi info (Android 12+). |

*No data leaves your device. No analytics, no tracking, no cloud services. All traffic stays on your local network.*

---

## 🗺️ Roadmap – Upcoming Features

- **WebDAV Support** – mount the server as a network drive on Windows/macOS.
- **Upload Speed Limiter** – control bandwidth usage.
- **Theme Customization** – additional color schemes for the web UI.
- **Cloudflare Tunnel Integration** – optionally expose the server to the public internet via Cloudflare tunnels.

---

## 🤝 Contributing

This project is 100% open source (MIT license). Contributions are welcome! Open issues, suggest features, submit pull requests, or improve documentation.

**Building from source:** see [BUILD.md](BUILD.md) for detailed instructions.

---

## 📄 License

MIT License – see [LICENSE](LICENSE) for full text.

---

*Built for the open source community. ❤️*