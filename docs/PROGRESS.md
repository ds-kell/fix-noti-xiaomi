# Progress

## v0.1.3

- Clarified the post-diagnostic no-op state: Preview now reports that selected apps are already optimized instead of showing "No plan yet".
- Hidden Dry Run controls and the apply button when there are no actual changes to review.
- Removed duplicate inline operation feedback; transient feedback is now shown once through the snackbar.

## v0.2.0

- Expanded the safe optimization profile to include `RUN_IN_BACKGROUND`, App Standby bucket and inactive state in addition to Doze and `RUN_ANY_IN_BACKGROUND`.
- Added opt-in Xiaomi autostart AppOps (10053/10008), background-data netpolicy, aggressive background AppOps and a device-wide power profile. Every new write uses the existing snapshot, read-back verification and exact rollback transaction.
- Refined the dashboard metrics, operation labels and Advanced Settings hierarchy so risky options are visually separated, explained and reset when Advanced mode is disabled.
- Automatically includes installed Google Play Services and Google Services Framework packages whenever selected apps are diagnosed.
- Added a visible diagnostic scope summary and localized operation/value explanations so results show what is being changed and why.
- Kept unverified heartbeat global-setting hacks out of the default profile.

## v0.2.1

- Fixed App Standby semantics: bucket `5` is `EXEMPTED`, which is more privileged than `ACTIVE` (`10`), so it is now preserved and never offered as a downgrade.
- Added a defensive apply guard and UI label for the exempted bucket.

## Completed

- Phase 1 repository inspection and source-of-truth documents.
- Chosen safe architecture and verified primary Shizuku/Android sources.
- Phase 2: Compose shell, typed command model, Shizuku UserService/binder/permission state, real and fake executors, timeout/redaction foundations.
- Phase 3: device/ROM detection, installed-app catalog, public notification/battery/background checks, capability probing primitives and report exporter backend.
- Phase 4: Doze and `RUN_ANY_IN_BACKGROUND` AppOps read/plan/apply/verify/exact rollback, atomic private JSON backups, history, dry-run preview and automatic partial rollback.
- Phase 5 safe subset: resolved Xiaomi/standard Settings intents, manual user-confirmed checklist, Advanced/Experimental gates, lossless hidden-whitelist parser. Hidden-setting device writes are intentionally not exposed.
- Phase 6: README, Apache-2.0 declaration, contribution/templates, GitHub Actions, JVM tests, lint and debug APK.
- Phase 7: searched for unsafe shell/destructive primitives and audited result/rollback semantics.
- UI refinement: replaced the functional scaffold with a responsive Material 3 dashboard, real navigation icons, dedicated Shizuku/device/status cards, meaningful empty states, and consistent Preview/History/Settings hierarchy. Installed and visually smoke-tested the resulting APK on `emulator-5554` (Android 37).
- Device feedback fix (`0.1.1`): AOSP whitelist output includes an app-id column (`user,package,appId`); fixed parsing, ignored `system-excidle` as not a full exemption, removed no-op plans, and made verification failure roll back the operation that may already have changed.
- Product UI polish (`0.1.2`): introduced a branded shield/pulse launcher icon, a fixed-height single-line app search field with true placeholder behavior, visible progress states for long-running actions, Snackbar acknowledgements, and localized semantic result cards instead of raw enum/text output.
- Final `0.1.2` visual QA: converted the generated shield/pulse mark into a transparent adaptive-icon foreground over an indigo background, verified MainActivity foreground without crash, and confirmed the Apps screen renders actual package icons and a one-line ellipsized placeholder on Android 37 emulator.

## Not completed / intentionally deferred

- No physical Xiaomi 15 Ultra validation is available. A manual emulator smoke test was completed, but Compose instrumentation tests and rotation/process-recreation tests are not implemented in this iteration.
- Report exporter backend exists, but copy/share controls are not yet wired into the Compose UI.
- Notification channel detail for other packages is not available through public APIs and is reported only at the app-permission level.
- Xiaomi hidden-setting namespace probing and writes are deferred pending exact-build evidence; no compatibility claim is made.
- DataStore dependency is present but user preferences are currently saved by Compose state rather than persisted across process death.
- Notification latency test-assistant data capture is not implemented.

## Verification

- Build: `assembleDebug` passed on 2026-08-04.
- Unit tests: `testDebugUnitTest` passed, 12 tests, 0 failures/errors.
- Lint: `lintDebug` passed with 0 errors and 30 non-blocking warnings.
- Redesigned UI build: `testDebugUnitTest assembleDebug lintDebug` passed on 2026-08-04; APK installed successfully on Android 37 emulator and Home rendered without clipping/crash.
- Physical Xiaomi 15 Ultra: not available/not tested.
- APK: `app/build/outputs/apk/debug/app-debug.apk` (11,605,886 bytes).

## Important files

- `docs/PROJECT_SPEC.md`, `docs/DECISIONS.md`, `docs/COMMAND_MATRIX.md`, `docs/DEVICE_TEST_PLAN.md`

## Next step

Install on Xiaomi 15 Ultra, execute the read-only portion of `DEVICE_TEST_PLAN.md`, wire report sharing after reviewing on-device output, then add instrumentation coverage before enabling any OEM-specific device operation.
