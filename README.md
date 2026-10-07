# SealRec

Offline **sealed audio recorder with integrity verification** for Android.

SealRec captures PCM with `AudioRecord`, streams a SHA-256 digest while writing, signs the digest with an Android Keystore ECDSA P-256 key (StrongBox preferred, platform Keystore fallback), and embeds the signature in a custom WAV `seal` chunk. Hardware protection depends on the device. Verification is fully local — no network permission.

## Features

- Device-key sealing (SHA-256 + ECDSA)
- Custom WAV `seal` chunk (most players still play the file)
- Offline integrity report: Intact / Tampered / Bad signature / Not SealRec
- Foreground service recording with pause / resume / stop
- Library: seekable playback, rename, export, share WAV + JSON, verify, recycle bin
- Streaming save / verification / recovery; incomplete `.raw` captures retain their recording format
- Zero `INTERNET` / `ACCESS_NETWORK_STATE` permissions

## Security notes

| Proves | Does not prove |
|--------|----------------|
| PCM hash matches the embedded key signature | Recording content is “true”, or speaker / device identity |
| Possession of the signing key when sealed | Trusted time or an authenticated WAV format; v1 signs only the PCM digest |

WAV files are **not encrypted**. Recovered PCM is signed at recovery time. Keep a trusted fingerprint separately if you need to compare a signing key. Changing WAV format metadata does not change the PCM digest. Legacy raw files without format metadata use the old 44.1 kHz default; their original rate cannot be recovered reliably.

Capture pauses on interruption, low space or the 2,000,000,000-byte PCM limit; it never resumes automatically. Save failures retain raw audio for recovery. The limit is about 17.4 hours at 16 kHz, 6.3 hours at 44.1 kHz, or 5.8 hours at 48 kHz (mono 16-bit). Saving temporarily needs space for raw and WAV together. Playback stops when leaving Library or moving the app to the background.

## Stack

- Kotlin, Jetpack Compose, Coroutines
- `AudioRecord`, Android Keystore, Room
- minSdk 26, targetSdk 36

## Build

Open in Android Studio, or:

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Debug installs as `com.yishenghuang.sealrec.debug`, separate from the release package and recordings. Release signing configuration is unchanged. For repeatable local checks use `:app:lintDebug` as well.

Instrumented tests (explicit test emulator required):

```bash
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb -s TEST_SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
adb -s TEST_SERIAL install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s TEST_SERIAL shell am instrument -w com.yishenghuang.sealrec.debug.test/androidx.test.runner.AndroidJUnitRunner
```

Only pass `-e silentEmulator true` when using an emulator launched with `-no-audio`; this enables the microphone/service flow test without recording host audio. The process-death tests require separate `crashPhase=prepare` and `crashPhase=recover` instrumentation invocations with an explicit `adb -s TEST_SERIAL shell am force-stop com.yishenghuang.sealrec.debug` between them.

See [local completion checklist](docs/LOCAL_COMPLETION.md) and [privacy / release audit](docs/PRIVACY_AND_RELEASE_AUDIT.md) for evidence and outstanding device checks. No publication or remote push is part of local validation.

## License

Private project — all rights reserved unless otherwise stated.
