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

## 008 — MILLET helper and GMS-Protect techniques

SettingsProvider rejects writes to non-public `Settings.System` keys from apps targeting SDK ≥ 23, so the main app cannot maintain `MILLET_NO_RESTRICT_APP` with WRITE_SETTINGS alone. A separate `:millet-helper` APK targets SDK 22 (the technique used by GMS-Protect), is bundled in the main APK, installed through Shizuku with `--bypass-low-target-sdk-block`, and controlled through a signature-protected provider. It merges (never replaces) the list, rebuilds a transient empty value from the last healthy list, re-arms a JobScheduler content-URI watcher instead of polling, and records evidence keyed to `Build.FINGERPRINT`. Shared parsing/repair lives in `:whitelist-core`.

Also ported from GMS-Protect: MiPush `REFRESH_WHITE_LIST_ACTION` and Aurogon `notifyChange` (both permission-free), an opt-in screen-off Aurogon refresh service, the FCM `local/status` probe with heartbeat/reconnect, and Greezer `mGmsLimitEnabled` via Shizuku `dumpsys` (runtime-only, resets on reboot). Not ported: the GMS binding probe, which only helps while the app is the top activity.

## 009 — Assistant guard (Gemini on the power key)

Observed on Xiaomi 15 Ultra, HyperOS OS3.0.314.0.WOACNXM: with Xiao AI removed and Google chosen as assistant, `VoiceInteractionManagerService` logs "no auto selectable voice recognition services" at boot (the user-installed Google app is not direct-boot aware) and clears `Settings.Secure.voice_interaction_service`, while `assistant` keeps the user's choice. The power-key long press (`system long_press_power_key=launch_google_search`) then fails with "Activity class GsaVoiceInteractionService does not exist". Setting `voice_recognition_service` explicitly does not prevent the wipe; reinstalling Xiao AI only changes the fallback to Xiao AI.

The guard restores `voice_interaction_service` from `assistant`, only when they differ, the device is unlocked, and the target still resolves as a `VoiceInteractionService`. It needs WRITE_SECURE_SETTINGS, granted or revoked through Shizuku `pm grant/revoke`; holding the permission is the on/off switch, so no extra preference exists. Timing: `LOCKED_BOOT_COMPLETED` arrives seconds after boot, so a direct-boot-aware job is started then and waits for `USER_UNLOCKED` (delivered to registered receivers only). A JobScheduler content-URI watcher on both keys re-repairs within ~1 s if the system rewrites them after unlock. An automation-app macro on `BOOT_COMPLETED` worked but landed ~16 s after unlock on this ROM.

The assistant picker (0.10.0) lists every app declaring a `VoiceInteractionService` (Google/Gemini, Claude, ChatGPT…, plus Xiao AI even after `pm uninstall --user 0`) and "Áp dụng" runs, through Shizuku, the community Gemini `.bat` generalised to any assistant: remove Xiao AI (first — removing it after the role change made the system clear `assistant`), grant RECORD_AUDIO, move `android.app.role.ASSISTANT`, set `voice_interaction_service`/`assistant`/`voice_recognition_service`, set HyperOS `system long_press_power_key=launch_google_search` (opens whichever assistant is set; the `.bat`'s `global power_button_long_press=5` alone is ignored on HyperOS 3) and grant the guard's permission. Choosing Xiao AI reverses it with `install-existing` and `launch_voice_assistant`. Only the role, service and power-key steps are required; the rest are best effort and reported.

Claude and ChatGPT were reached by the power key but HyperOS refused their assistant overlay ("MIUILOG- Permission Denied Activity") until Xiaomi op 10021 ("open new windows while running in the background", 10020 on the lock screen) was allowed; the Google app is pre-allowed. "Áp dụng" grants both.

Without Shizuku (0.10.3), switching between assistants after one Shizuku setup goes through the system: writing `assistant`/`voice_interaction_service` alone works immediately but the system resets `assistant` to the role holder on boot (observed), so the role must move. `ACTION_MANAGE_DEFAULT_APP` needs MANAGE_ROLE_HOLDERS; the app opens the public `ACTION_VOICE_INPUT_SETTINGS` ("Digital assistant app" row) instead, with Xiaomi's `miui.intent.action.APP_PERM_EDITOR` on top the first time a non-Google assistant is chosen. Xiao AI still needs Shizuku (it is uninstalled for the user and must be `install-existing`).

## Sources reviewed

- Official Shizuku API repository and changelog: https://github.com/RikkaApps/Shizuku-API
- Android Developers, Doze/App Standby: https://developer.android.com/training/monitoring-device-state/doze-standby
- Android Developers, background optimization: https://developer.android.com/topic/performance/background-optimization
- AOSP `DeviceIdleController` and AppOps shell implementations; runtime probing remains mandatory because OEMs may diverge.
- GMS-Protect (hoanghanhdevvn): https://github.com/hoanghanhdevvn/GMS-Protect
