# Building WebServer-Android from Source

Requirements:
- Android SDK – you need android.jar for API level 36 (or lower)
- R8/D8 – download the latest r8.jar from the official R8 releases
- Java Development Kit – JDK 8 or later (OpenJDK 17 works)
- aapt2 – Android Asset Packaging Tool
- Git – to clone the repository

Step-by-Step:

1. Clone the repository
   git clone https://github.com/toptstudio/WebServer-Android.git
   cd WebServer-Android

2. Add required JARs
   Place android.jar and r8.jar in the project root (same folder as build.sh).

3. Generate a signing keystore
   keytool -genkey -v -keystore webserver_signing.keystore -alias server -keyalg RSA -keysize 2048 -validity 10000 -storepass android -keypass android

4. Run the build script
   chmod +x build.sh
   ./build.sh

5. Output
   The signed APK will be at bin/app-debug.apk.
   If you are on Termux with storage permission granted, it will also be copied to /sdcard/app-debug.apk.

Troubleshooting:

- aapt2: command not found -> pkg install aapt2 (Termux) or add Android build-tools to PATH.
- android.jar not found -> Copy the correct android.jar to the project root.
- Missing webserver_signing.keystore -> Run the keytool command above.
- Cannot write to internal storage -> Run termux-setup-storage and grant permission.

Building without Termux:
The same build.sh works on any Linux/macOS system with Java, aapt2, and the required JARs. For Windows, use WSL or Git Bash.
