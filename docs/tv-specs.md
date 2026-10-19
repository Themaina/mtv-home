# Target TV specs

Surveyed over adb on 2026-10-20.

## Hardware

| Item | Value |
|---|---|
| Brand / board | Toptech TV ("Ateko" vendor build), Realtek RTD2851A SoC |
| CPU | 4× ARM Cortex-A55 @ 1.3 GHz, running **32-bit** (armeabi-v7a only, zygote32) |
| GPU | ARM Mali-G31, OpenGL ES 3.2 |
| RAM | 1 GB, `ro.config.low_ram=true`, app heap 128 MB (256 MB large) |
| Storage | 4 GB `/data`, ~334 MB free at survey time |
| UI resolution | 1280×720 @ 60 Hz, density 240 (hdpi). `ro.hdmi.resolution=2K` |
| HDR | HDR10 and HLG, 500 nits max |

## Video and DRM

| Codec | Hardware decoder (`OMX.realtek.video.decoder`) | Secure decoder |
|---|---|---|
| H.264 / AVC | yes, 1080p @ 120 fps measured | yes |
| HEVC / H.265 | yes | yes |
| VP9 | yes | yes |
| AV1 | yes | yes |
| MPEG-2 / MPEG-4 | yes | MPEG-4 only |

DRM HALs: Widevine, PlayReady and ClearKey (drm@1.3). Dolby audio (AC-3, E-AC-3, AC-4) decoders present.

## Software

| Item | Value |
|---|---|
| Android | 11 (API 30), `userdebug` build, release-keys |
| Build date | 2022-04-28 (`eng.hexing.20220428.193422`) |
| Security patch | 2020-09-05 |
| Kernel | 4.14.187 |
| Treble / VNDK | enabled / 30, no A/B slots |
| Bootloader | locked, verified boot green; `ro.oem_unlock_supported=1` |
| Home launcher | Titan OS 2 (`com.zeasn.toptech.titanos2.launcher`), fallback `com.android.tv.settings/.system.FallbackHome` |
| Google | Play services 26.40.32 and Play Store 52.8 (both current); leanback_only |
| WebView | `com.android.webview` 83 (2020) is the only allowed provider |
| System updater | `com.toptech.systemupdate`, never updated since factory |

## Developer notes

- Build for **armeabi-v7a**; arm64-only native libraries will not install.
- `minSdk` up to 30 works; we use 21. `targetSdk` 35 is fine.
- Design for 1280×720 at 240 dpi (≈ 853×480 dp), D-pad only, no touch.
- Keep memory low: 1 GB device with ~300 MB available. Test release builds.
- adb: no host authorization (`ro.adb.secure` unset), TCP 5555 persists across reboots, `su` gives root to the shell user.
