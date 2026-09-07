# Command Matrix

| Operation ID | Purpose | Probe/read | Apply | Rollback | Android | Observed ROMs | Risk | Source | Status |
|---|---|---|---|---|---|---|---|---|---|
| `shell_uid` | Verify execution identity | `id -u` | — | — | Shizuku v11+ | none physically tested | Low | Shizuku UserService docs | Implemented |
| `doze_whitelist` | Partial Doze exemption | `cmd deviceidle whitelist` | `cmd deviceidle whitelist +PKG` | `cmd deviceidle whitelist -PKG` only if initially absent | 6+, probed | none physically tested | Medium | Android docs/AOSP | Implemented |
| `appops_run_any_background` | Remove verified background AppOp restriction | `cmd appops get PKG RUN_ANY_IN_BACKGROUND` | `cmd appops set PKG RUN_ANY_IN_BACKGROUND allow` | set exact saved mode | command-probed | none physically tested | Medium | AOSP AppOps shell | Implemented |
| `xiaomi_cloud_lowlatency` | Candidate OEM whitelist | Future: `settings get NAMESPACE cloud_lowlatency_whitelist` | Not exposed | Not exposed | unknown | none | Experimental | community hypothesis only | Parser tested; device operation deferred |
| `xiaomi_settings_intent` | Open manual Autostart/battery UI | resolve explicit Intent | user action | user action | runtime-resolved | none | Low | PackageManager resolution | Implemented |

All package placeholders are separate arguments produced only from the installed-package catalog. Success requires post-write verification.
