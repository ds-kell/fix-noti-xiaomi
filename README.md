# HyperNotifyFix

HyperNotifyFix is an open-source, no-root Android diagnostic assistant for delayed notifications on Xiaomi 15 Ultra/HyperOS. It uses a user-started [Shizuku](https://shizuku.rikka.app/) instance to inspect and, after backup/preview/confirmation, apply a small set of reversible Android changes.

It cannot guarantee instant notifications. Delivery also depends on the sender's server, FCM priority, network, application design, notification settings, Android, and undocumented ROM behavior.

> Screenshot placeholders: Home · App selection · Diagnostic preview · History/restore

## Requirements and build

- Android 8.0+; primary target is Xiaomi 15 Ultra with unknown China/Global/EEA HyperOS builds.
- Shizuku v13, started through Wireless Debugging; no root or unlocked bootloader.
- Android SDK 35, JDK 17/21.

```bash
./gradlew assembleDebug testDebugUnitTest lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Use

1. Install Shizuku, enable Developer options and Wireless debugging, pair/start Shizuku, then grant HyperNotifyFix permission.
2. Select individual apps. Suggested Google packages appear only when installed and are never selected automatically.
3. Run diagnostics and review every planned change. Dry Run is on by default.
4. Turn Dry Run off only after reviewing backup/rollback and battery impact. Confirm apply, then inspect every verification result.
5. Restore any session from History. For Autostart, No restrictions, notifications and background data, use the resolved Settings shortcuts and explicitly tick the user-confirmed checklist.

## Safety model and supported operations

- Commands are fixed argument lists; package names come from `PackageManager`; no `sh -c` or arbitrary command input.
- Private, schema-versioned backup precedes writes. Apply success requires read-back verification; a transaction failure rolls back earlier verified changes.
- Implemented, runtime-probed: Doze whitelist add/exact rollback and `RUN_ANY_IN_BACKGROUND` AppOp exact-mode restore.
- Read-only/public diagnostics: device/ROM metadata, notification permission availability, battery exemption, background restriction, Shizuku status/effective UID.
- Experimental: Xiaomi `cloud_lowlatency_whitelist` remains an unverified candidate. Absent keys are never created; the current build exposes no automatic hidden-setting write.
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

## License and disclaimer

Apache-2.0. HyperNotifyFix is not affiliated with Xiaomi, Google, or Shizuku. Shizuku API is Apache-2.0 compatible; see dependency notices for upstream terms.
