# Build and verify

## Requirements

Verified configuration: JDK 21, Gradle 8.11.1, Android Gradle Plugin 8.9.3, Android SDK platform 35 and build-tools 35.0.0. Java source/target is 17; runtime minimum is API 26. The Gradle distribution checksum is pinned in both wrappers.

Use an existing Android SDK, with its license requirements already satisfied, and set `ANDROID_HOME` normally. The repository contains source and the Gradle wrapper, not an SDK, dependency cache, APK, AAR or signing key. Standard debug signing is for local testing, not a production release. Keep at least 2 GiB free disk and use one worker on constrained machines.

## Source checks (no Android toolchain)

```sh
python3 tools/verify-publication.py
python3 tools/verify-source-closure.py
```

The first command verifies both copies, per-file provenance, authored-file hashes, allowed file types, exclusions and documentation links. The second verifies the reviewed engine closure and exact radial asset. Neither downloads dependencies or runs an emulator.

## Feedback example and shared module

```sh
./gradlew --no-daemon --max-workers=1 \
  -Dorg.gradle.jvmargs='-Xmx1g -Dfile.encoding=UTF-8' \
  :engine:assembleDebug :engine:lintDebug :smoke-host:assembleDebug
bash tools/test-host-core.sh
```

The wrapper may download Gradle, AGP and normal build-time dependencies from their configured official repositories. Add `--offline` only when these dependencies already exist in your own cache. The recorded build used an existing offline cache; a fresh online setup was not independently tested.

Outputs:
- `engine/build/outputs/aar/engine-debug.aar`
- `smoke-host/build/outputs/apk/debug/smoke-host-debug.apk`

Feedback launcher: `dev.oritwig.markup.proof/.MainActivity`.

## Independently buildable attachment example

```sh
cd standalone-consumer
./gradlew --no-daemon --max-workers=1 \
  -Dorg.gradle.jvmargs='-Xmx1g -Dfile.encoding=UTF-8' :app:assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`.
Launcher: `dev.oritwig.markup.consumer/.MainActivity`.

This directory contains its own settings, wrapper, app and exact engine source copy, pinned in `PINNED-MODULE-SOURCE.json`. It can be copied out and built without the feedback host or a prebuilt AAR. The root `tools/build-independent-consumer.sh` refreshes that engine/build copy from the root before building; it is a development helper, unnecessary for the frozen snapshot.

## Runtime fixture

The explicit `dev.oritwig.markup.proof/.WorkflowFixtureActivity` is separate from ordinary launchers. It accepts an optional safe alphanumeric/dash/underscore `run_id` and writes PNGs plus `result.json` under the app's external-files `workflow-proof/<run_id>/` directory. [CHECKS.md](CHECKS.md) records the tested device and limitations. Synthetic fixture results do not establish physical-input behavior.

## CI scope

[Source verification](.github/workflows/source-verification.yml) uses Ubuntu 24.04's existing Python and Bash, with checkout pinned to an exact commit and repository read permission only. It performs source/hash/closure and shell-syntax checks. The automatic source job does not build Android or rerun runtime tests. A green source check is not a new runtime validation.

An optional manual run with `compile` enabled adds a separate compile/lint job for both Gradle roots and the 20 deterministic host-core checks. It selects the preinstalled JDK 21, requires platform 35 and build-tools 35.0.0, and disables automatic SDK downloads. The frozen wrapper/AGP may fetch normal build dependencies. The job invokes no assemble, signing, release, upload or emulator task, and does not install SDK packages or accept licenses. It fails if the required SDK/JDK is missing. Neither CI job has run as part of publication preparation.

The runner's installed tools are documented by [GitHub](https://github.com/actions/runner-images/blob/main/images/ubuntu/Ubuntu2404-Readme.md). No user-supplied credentials are required.
