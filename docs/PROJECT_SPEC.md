# HyperNotifyFix Product Specification

HyperNotifyFix is an open-source, no-root Android application for diagnosing and reducing delayed notifications on Xiaomi 15 Ultra/HyperOS using a user-started Shizuku service. It never promises instant delivery: server behavior, FCM priority, network, app configuration, Android, and OEM power policies remain outside its control.

## Safety invariants

- Dry Run is enabled by default. No mutation occurs before read, durable private-storage backup, preview, and explicit confirmation.
- Only packages selected from the installed-package catalog are accepted. User text is never concatenated into a shell command.
- Every command has an identifier, argument list, timeout, exit code, stdout, stderr, and duration. Reports redact sensitive-looking values.
- Every supported write is reversible and verified by reading state again. Unreadable is distinct from absent and empty.
- No root, bootloader, SELinux, verified-boot, package disabling/removal, app-data deletion, accessibility automation, unrelated permission grants, or global battery-management disabling.
- Xiaomi hidden settings are experimental, off by default, must already exist, must parse losslessly, and restore the exact raw value.

## Scope

The main flows are Shizuku onboarding, device/ROM compatibility, installed-app selection, public-API and privileged diagnostics, operation preview, apply/verify/automatic rollback, session history/restore, manual Settings shortcuts, and redacted JSON/text export. Suggested packages are not selected automatically. Backups stay in private app storage.

## Completion truth

Implementation, emulator/fake tests, and physical Xiaomi validation are reported separately. The application is not considered validated on Xiaomi 15 Ultra until the device checklist in `DEVICE_TEST_PLAN.md` is completed on the exact build.
