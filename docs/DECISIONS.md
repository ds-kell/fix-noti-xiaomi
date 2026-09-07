# Technical Decisions

## 001 — Single Android application module

Use one `app` module with package-level boundaries (`core`, `data`, `domain`, `ui`). This keeps builds practical while preserving testable interfaces.

## 002 — Shizuku UserService

Use official Shizuku API 13.1.5 and `bindUserService`. `Shizuku.newProcess` is not used because the official changelog says it is being prepared for removal. The bound AIDL service runs as ADB shell UID (normally 2000) and starts commands via `ProcessBuilder(List<String>)`.

## 003 — Command safety

Commands are typed immutable argument lists. Package names must match a package returned by `PackageManager`; no `sh -c` is used. Both the client and privileged service enforce bounded timeouts. Exit zero is never sufficient for apply success; operations re-read and compare.

## 004 — Doze operation

Probe `cmd deviceidle whitelist`; read the returned allowlist; apply `cmd deviceidle whitelist +PACKAGE`; rollback with `-PACKAGE` only when the snapshot proves the package was absent beforehand. Android 6+ introduced Doze, but runtime command capability is authoritative.

## 005 — AppOps operation

Only `RUN_ANY_IN_BACKGROUND` is implemented initially. Probe via `cmd appops get PACKAGE OP`, preserve modes (`allow`, `ignore`, `deny`, `default`, `foreground`), apply `cmd appops set PACKAGE OP allow`, verify, and restore the exact previous mode. Unknown output disables writes.

## 006 — Xiaomi integration

Generic Android behavior is independent of Xiaomi support. `cloud_lowlatency_whitelist` is an unverified candidate. This version implements and tests only its lossless parser/merge policy; device reads/writes are deliberately deferred until a namespace and exact-build evidence can be established. A future implementation must probe read-only across namespaces, never create an absent key, and require Advanced + Experimental flags, an additional warning, backup, and exact raw rollback. No Xiaomi 15 Ultra support claim exists without exact-build device evidence.

## 007 — Backups

Versioned JSON sessions are atomically written to private `filesDir/sessions`. Snapshot values explicitly encode Present(raw), Absent, Unreadable, and Unsupported. Diagnostic exports exclude raw restore payloads.

## Sources reviewed

- Official Shizuku API repository and changelog: https://github.com/RikkaApps/Shizuku-API
- Android Developers, Doze/App Standby: https://developer.android.com/training/monitoring-device-state/doze-standby
- Android Developers, background optimization: https://developer.android.com/topic/performance/background-optimization
- AOSP `DeviceIdleController` and AppOps shell implementations; runtime probing remains mandatory because OEMs may diverge.
