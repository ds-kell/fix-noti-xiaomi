# HyperNotifyFix — fix delayed notifications, remove bloatware & ads, pick your AI assistant on Xiaomi HyperOS (no root)

**Zalo, Messenger, Gmail, Telegram… notifications arriving late or only when you open the app?** HyperNotifyFix finds and fixes the HyperOS battery/background restrictions behind it, without root, using Shizuku.

**Want Gemini, ChatGPT or Claude on the power button instead of Xiao AI?** Pick any installed assistant; HyperNotifyFix sets it up (microphone, background-window permission, power-key action) and keeps it after every reboot, even on HyperOS China ROMs that reset it.

**HyperOS toolkit:** remove bloatware and the MSA ad service (reversible, per user), set the default browser or launcher HyperOS keeps locked, one-tap keep-alive presets for messaging, smartwatch and music apps, and a **bank-safe mode** that turns developer options and wireless debugging back off when you are done, so banking apps open normally while every fix stays in place.

> 🇻🇳 Thông báo Zalo, Messenger, Gmail… đến trễ hoặc chỉ hiện khi mở app trên Xiaomi/Redmi/POCO (HyperOS, MIUI)? App này kiểm tra và sửa các giới hạn chạy nền của HyperOS, không cần root. Ngoài ra còn đổi trợ lý AI khi giữ nút nguồn từ Xiao AI sang Gemini, ChatGPT, Claude… và tự giữ sau mỗi lần khởi động lại. Tab **Công cụ**: gỡ app rác, tắt quảng cáo hệ thống (MSA), đổi trình duyệt/launcher mặc định, chọn nhanh app cần chạy nền, và tắt Tùy chọn nhà phát triển khi xong để app ngân hàng vẫn dùng được.

[![Download APK](https://img.shields.io/github/v/release/ds-kell/fix-noti-xiaomi?label=Download%20APK&logo=android)](../../releases/latest)

HyperNotifyFix is an open-source, no-root Android diagnostic assistant for delayed notifications on Xiaomi 15 Ultra/HyperOS. It uses a user-started [Shizuku](https://shizuku.rikka.app/) instance to inspect and, after backup/preview/confirmation, apply a small set of reversible Android changes.

It cannot guarantee instant notifications. Delivery also depends on the sender's server, FCM priority, network, application design, notification settings, Android, and undocumented ROM behavior.

> Screenshot placeholders: Home · App selection · Diagnostic preview · History/restore

## Install

Download `HyperNotifyFix-<version>.apk` from [Releases](../../releases/latest), install it, then follow **Use** below.

## Requirements and build

- Android 8.0+; primary target is Xiaomi 15 Ultra with unknown China/Global/EEA HyperOS builds.
- Shizuku v13, started through Wireless Debugging; no root or unlocked bootloader.
- Android SDK 35, JDK 17/21.

```bash
./gradlew assembleDebug testDebugUnitTest lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Signing: every build (debug and release, app and MILLET helper), local or CI, uses one fixed key, so a new APK always installs over the previous one. The key is **not** in git: locally it lives in `signing/hypernotifyfix-release.jks` with `keystore.properties` (both git-ignored); CI recreates them from the `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` repository secrets (`scripts/export-signing-secrets.sh` prints them). Never regenerate it: a different key forces uninstalling the app and helper. Any field can be overridden with `HYPERNOTIFY_SIGNING_<FIELD>` env vars (e.g. `HYPERNOTIFY_SIGNING_STOREFILE`).

## Use

1. Install Shizuku, enable Developer options and Wireless debugging, pair/start Shizuku, then grant HyperNotifyFix permission.
2. Select individual apps. Suggested Google packages appear only when installed and are never selected automatically.
3. Run diagnostics and review every planned change. Dry Run is on by default.
4. Turn Dry Run off only after reviewing backup/rollback and battery impact. Confirm apply, then inspect every verification result.
5. Restore any session from History. For Autostart, No restrictions, notifications and background data, use the resolved Settings shortcuts and explicitly tick the user-confirmed checklist.

## Safety model and supported operations

- Commands are fixed argument lists; package names come from `PackageManager` or a built-in catalog; no arbitrary command input. The only `sh -c` is a constant script that turns developer options off (bank-safe mode).
- Private, schema-versioned backup precedes writes. Apply success requires read-back verification; a transaction failure rolls back earlier verified changes.
- Implemented, runtime-probed: Doze whitelist add/exact rollback and `RUN_ANY_IN_BACKGROUND` AppOp exact-mode restore.
- Read-only/public diagnostics: device/ROM metadata, notification permission availability, battery exemption, background restriction, Shizuku status/effective UID.
- MILLET helper: an optional bundled `targetSdk 22` companion, installed once through Shizuku, keeps `MILLET_NO_RESTRICT_APP` (and `millet_white`/`cloud_lowlatency_whitelist` when present) repaired after reboots and PowerKeeper rewrites without Shizuku. See [decision 008](docs/DECISIONS.md).
- Assistant picker (Shizuku): puts Gemini, Claude, ChatGPT or any installed assistant on the power-key long press instead of Xiao AI, and keeps it there after reboots on HyperOS CN, which clears `voice_interaction_service` on every boot. Choosing Xiao AI restores the original. See [decision 009](docs/DECISIONS.md).
- HyperOS GMS nudges: MiPush whitelist refresh, Aurogon allowlist reload (optional screen-off keep-alive), FCM connection check/reconnect, and an experimental Greezer GMS-limiter switch (Shizuku, resets on reboot).
- Experimental: Xiaomi `cloud_lowlatency_whitelist` remains an unverified candidate. Absent keys are never created; the current build exposes no automatic hidden-setting write.
- Toolkit tab (Shizuku): per-user removal/restore of a curated bloat and ad catalog (`pm uninstall -k --user 0` / `cmd package install-existing`), default browser/launcher through `cmd role`, keep-alive presets, and bank-safe mode. Everything it applies persists without Shizuku. See [decision 010](docs/DECISIONS.md).
- Manual Settings intents are resolved before launch and fall back to Android App Info.

Changing background policy can increase battery consumption. Google Play Services changes deserve particular caution.

## Compatibility

| Device/ROM | Status |
|---|---|
| Fake executor / JVM parsers | Covered by unit tests |
| Android emulator | Not yet recorded |
| Xiaomi 15 Ultra CNXM/MIXM/EUXM | Implemented for runtime probing; **not device-tested** |

See [command evidence](docs/COMMAND_MATRIX.md), [decisions](docs/DECISIONS.md), and the [physical-device checklist](docs/DEVICE_TEST_PLAN.md).

## Reports and bugs

Reports contain only selected packages, shortened fingerprint hash, compatibility results, effective UID, and redacted errors. They exclude accounts, serial, Android ID, tokens, notification content, and the raw restore backup. Open a bug with the templates under `.github/ISSUE_TEMPLATE` and attach the report after reviewing it.

## ☕ Support / Ủng hộ

Free and open source. If it saved you time, you can buy me a coffee — thank you!

[![PayPal](https://img.shields.io/badge/PayPal-Donate-00457C?logo=paypal&logoColor=white)](https://paypal.me/truyennt)
[![VietQR](https://img.shields.io/badge/VietQR-Chuy%E1%BB%83n%20kho%E1%BA%A3n-E31837)](https://github.com/ds-kell/ds-kell/blob/main/DONATE.md)

<img src="https://raw.githubusercontent.com/ds-kell/ds-kell/main/assets/donate-vietqr.jpg" alt="VietQR VietinBank" width="180">

## License and disclaimer

Apache-2.0. HyperNotifyFix is not affiliated with Xiaomi, Google, or Shizuku. Shizuku API is Apache-2.0 compatible; see dependency notices for upstream terms.
