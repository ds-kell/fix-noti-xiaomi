# Xiaomi 15 Ultra Device Test Plan

Record model/device, Android SDK, fingerprint hash, security patch, HyperOS property values, and CNXM/MIXM/EUXM suffix. Never record serial, Android ID, accounts, phone number, tokens, or notification content.

1. Install debug APK and current official Shizuku; start Shizuku with Wireless Debugging.
2. Run only read-only diagnostics. Confirm binder lifecycle, permission denial/grant, effective UID 2000, and capability matrix. Export and inspect redaction.
3. Confirm all explicit Xiaomi intents are resolved before launch and standard App Info fallback works.
4. Select one non-critical user app. Preview one Doze operation, verify backup exists, apply, read back, then restore and compare exact initial state.
5. Repeat for `RUN_ANY_IN_BACKGROUND` only if probe returns a recognized mode. Induce a fake/controlled failure and verify partial rollback reporting.
6. Reboot only by user action. Confirm Shizuku-not-running guidance and record which system changes persisted.
7. Keep Xiaomi hidden-setting writes disabled initially. If exact-build evidence is established, enable Advanced/Experimental, confirm the key already exists and parses losslessly, test one package, read back, restore exact raw bytes, and record evidence.
8. Notification effectiveness: use a second device to send a test; manually record app, send/receive timestamps, screen state, network, and before/after. Do not capture notification content.

Pass requires exact restore, no unrelated package/state change, truthful per-operation results, and no sensitive data in log/export.
