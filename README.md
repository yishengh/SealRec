# SealRec

Offline **tamper-proof sealed audio recorder** for Android.

SealRec captures PCM with `AudioRecord`, streams a SHA-256 digest while writing, signs the digest with an Android Keystore ECDSA P-256 key (StrongBox preferred, TEE fallback), and embeds the signature in a custom WAV `seal` chunk. Verification is fully local — no network permission.

## Features

- Hardware-backed sealing (SHA-256 + ECDSA)
- Custom WAV `seal` chunk (most players still play the file)
- Offline integrity report: Intact / Tampered / Bad signature / Not SealRec
- Foreground service recording with pause / resume / stop
- Library: play with seekable progress, rename, export, verify, delete
- Crash recovery for incomplete `.raw` captures
- Zero `INTERNET` / `ACCESS_NETWORK_STATE` permissions

## Security notes

| Proves | Does not prove |
|--------|----------------|
| PCM bytes were not modified | Recording content is “true” |
| Signed by this device key | Absolute trusted time (timestamps are device local time) |

## Stack

- Kotlin, Jetpack Compose, Coroutines
- `AudioRecord`, Android Keystore, Room
- minSdk 26, targetSdk 36

## Build

Open in Android Studio, or:

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Instrumented tests (device/emulator required):

```bash
./gradlew :app:connectedDebugAndroidTest
```

## License

Private project — all rights reserved unless otherwise stated.
