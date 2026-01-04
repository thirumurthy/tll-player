# Release Guide

## Automated Release

### Prerequisites
1. **GitHub CLI**: [Install GitHub CLI](https://cli.github.com/) and run `gh auth login`.
2. **Signing Setup**:
   - Create a file named `keystore.properties` in the root folder (this file is ignored by git).
   - Add your keystore details (ask developer for secrets or fill with your own):
     ```properties
     storePassword=your_password
     keyPassword=your_password
     keyAlias=your_alias
     storeFile=../tllplayer.jks
     ```
   - **Note**: `storeFile` path is relative to the `app` module, so `../tllplayer.jks` points to the root.

### Window (PowerShell)
Run the PowerShell script:
```powershell
.\release.ps1 -Version v1.0.X
```
*Example: `.\release.ps1 -Version v1.0.9`*

### Linux / Mac / Git Bash
Run the Shell script:
```bash
./release.sh v1.0.X
```

---

## Manual Process

Follow these steps if you prefer to do it manually or if the script fails.

## 1. Update Version
Before building the APK, you need to update the version information.

### Option A: Using Make (if available)
Run the following command in your terminal (Git Bash or similar):
```bash
make gen v=v1.0.9
```
*Replace `v1.0.9` with your new version number.*

### Option B: Manual Update
1. Open `version.json`.
2. Update `version_name` to your new version (e.g., `"v1.0.9"`).
3. Update `version_code`. This must be higher than the previous one. You can calculate it or simply increment the integer value found in `version.json`.

## 2. Commit and Tag
1. Commit your changes:
   ```bash
   git add .
   git commit -m "Release v1.0.9"
   ```
2. Create a git tag (files are fetched from GitHub, so tags help):
   ```bash
   git tag v1.0.9
   git push origin main --tags
   ```

## 3. Build Signed APK
1. Open Android Studio.
2. Go to **Build** > **Generate Signed Bundle / APK**.
3. Select **APK**.
4. Choose your keystore and enter passwords.
5. Select **release** build variant.
6. Click **Create**.

## 4. Publish to GitHub
The `UpdateManager` looks for the release on GitHub.

1. Go to your GitHub repository: [https://github.com/thirumurthy/tll-player/releases](https://github.com/thirumurthy/tll-player/releases)
2. Click **Draft a new release**.
3. **Choose a tag**: Select the tag you created (e.g., `v1.0.9`).
4. **Release title**: `v1.0.9` (or similar).
5. **Attach binaries**:
   - Locate your built APK (usually in `app/release/app-release.apk`).
   - **RENAME** the file to match the format: `tll-player-v1.0.9.apk`.
   - **IMPORTANT**: The filename MUST be `tll-player-[version_name].apk`.
6. Click **Publish release**.

## 5. Verify Update
1. Open the app on a device with an older version.
2. The app should automatically check for updates on startup (or go to Settings > Check Version).
3. It should detect the new version and prompt to update.
