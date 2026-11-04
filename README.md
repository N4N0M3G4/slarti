# Slarti

The Geo Recon phone app: a cheerful Android field app for **rockhounds and fossil hunters**. It shows the rocks and fossils around you, takes geotagged photos, and guides you through a photogrammetry scan of a find. It sends everything to the [Magrathea hub](https://github.com/dyodji/magrathea) on your laptop.

## What it does

- **Field map.** MapLibre map with STREETS, DARK and SAT basemaps (SAT needs an Esri key). Works offline in one saved area (3 km around your pin).
- **Geology and fossils.** Macrostrat geologic map with an opacity slider. Tap a unit for its name, age and description. PBDB fossil occurrences; tap one for details. iDigBio specimens and Wikipedia summaries add context.
- **Pin, zoom lock, recenter.** Search an address or use GPS.
- **Geotagged photos.** CAPTURE saves a photo with GPS, heading, pitch and roll in a sidecar JSON.
- **Guided object scan.** SCAN OBJECT uses ARCore to track the phone around a fossil or rock. A coverage dome shows which angles you still need. It takes sharp frames by itself, with coaching and a haptic tick. Each frame keeps its camera pose and intrinsics for photogrammetry. The scan logic is in `ScanLogic.kt` and `ArScanEngine.kt`.
- **Upload to the hub.** Pair once by scanning the hub's QR code. Photos and scans upload on unmetered Wi-Fi, only to the paired LAN hub, and resume after a break.
- **Fog of war.** The map starts dark and reveals as you explore.

**Screenshots.** None yet. This is a native Android app with no web or desktop mode, so we could not capture it on the laptop. Run it on a phone or an Android emulator to see it.

## Quick start

You need JDK 17 and the Android SDK (API 34/35).

```bash
git clone https://github.com/dyodji/slarti.git
cd slarti
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Optional: put `arcgis.apiKey=...` in `local.properties` for the Esri satellite basemap. Never commit `local.properties`.

To pair: start the hub's ingest service (`npm run ingest` in the hub), open http://127.0.0.1:4180/pair on the laptop, and scan the QR code with the phone.

Known gaps: scan frames are ARCore CPU images (1920x1080 on a Pixel 7), not full-resolution stills; focus and exposure are not locked; no scale-target (ChArUco) detection yet.

## How we built it

The app is our own Kotlin and Jetpack Compose code. It does not fork any app. It pairs with the Magrathea hub, which is a fork of [God's Eye View](https://github.com/bilawalsidhu/gods-eye-view) by Bilawal Sidhu. The phone does sensing in the field (GPS, compass, camera, ARCore). The hub does the heavy work: the 3D globe, analysis and photogrammetry.

Main parts: MapLibre Native (map), ARCore (scan tracking), CameraX and the system camera (photos), WorkManager (uploads).

## Maintenance plan

- `main` is the only branch. Small commits, pushed after a local build.
- The hub's upload API (`/api/captures`, `/api/scans`) is the contract. Change it in both repos in the same session.
- We own all code here. Track MapLibre, ARCore and AndroidX versions in `gradle/` and update them a few times a year.

## The name

Slarti is short for Slartibartfast, the coastline designer in Douglas Adams' *The Hitchhiker's Guide to the Galaxy*. The hub is Magrathea. This is a fan nod for a personal project, not affiliated with the Douglas Adams estate.

## Credits and license

The hub this app talks to is built on [God's Eye View](https://github.com/bilawalsidhu/gods-eye-view) by **Bilawal Sidhu** (MIT License).

Data shown in the app keeps its own license. Macrostrat and PBDB are CC BY 4.0. OpenStreetMap data via OpenFreeMap is ODbL. Esri imagery follows Esri's terms. The code license does not cover data. Some optional hub data bundles are non-commercial (CC BY-NC); see the hub's [DATA_SOURCES.md](https://github.com/dyodji/magrathea/blob/geo-recon/DATA_SOURCES.md).
