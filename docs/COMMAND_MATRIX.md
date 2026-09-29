# Command Matrix

| Operation ID | Purpose | Probe/read | Apply | Rollback | Android | Observed ROMs | Risk | Source | Status |
|---|---|---|---|---|---|---|---|---|---|
| `shell_uid` | Verify execution identity | `id -u` | — | — | Shizuku v11+ | none physically tested | Low | Shizuku UserService docs | Implemented |
| `doze_whitelist` | Partial Doze exemption | `cmd deviceidle whitelist` | `cmd deviceidle whitelist +PKG` | `cmd deviceidle whitelist -PKG` only if initially absent | 6+, probed | none physically tested | Medium | Android docs/AOSP | Implemented |
| `appops_run_any_background` | Remove verified background AppOp restriction | `cmd appops get PKG RUN_ANY_IN_BACKGROUND` | `cmd appops set PKG RUN_ANY_IN_BACKGROUND allow` | set exact saved mode | command-probed | none physically tested | Medium | AOSP AppOps shell | Implemented |
| `xiaomi_cloud_lowlatency` | Candidate OEM whitelist | Future: `settings get NAMESPACE cloud_lowlatency_whitelist` | Not exposed | Not exposed | unknown | none | Experimental | community hypothesis only | Parser tested; device operation deferred |
| `xiaomi_settings_intent` | Open manual Autostart/battery UI | resolve explicit Intent | user action | user action | runtime-resolved | none | Low | PackageManager resolution | Implemented |
| `appops_10053/10008` | Xiaomi/MIUI autostart candidates | `cmd appops get PKG OP` | set `allow` | restore exact saved mode | command-probed, opt-in | unit parser only | Experimental | community implementation | Implemented opt-in |
| `network_background_whitelist` | Allow background data by UID | query package UID + netpolicy list | netpolicy add UID | exact saved membership | command-probed, opt-in | unit logic only | Medium | Android shell capability | Implemented opt-in |
| `aggressive_appops` | Wake lock, foreground service, exact alarm | `cmd appops get` | set `allow` | restore exact saved mode | command-probed, opt-in | unit parser only | Experimental | Android AppOps shell | Implemented opt-in |
| `aggressive_global_profile` | Disable standby/freezer and keep radios active | `settings get global` | `settings put global` | put exact value or delete absent key | shell + explicit opt-in | unit rollback only | Experimental | community implementation | Implemented opt-in |

All package placeholders are separate arguments produced only from the installed-package catalog. Success requires post-write verification.
