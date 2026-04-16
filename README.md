# Universal Clipboard

Compose Multiplatform app that syncs clipboard content (text, rich text, images, files) between Android and Mac — think "Apple Universal Clipboard" but cross-platform and self-hostable.

Starting platforms: **Android + macOS (Compose Desktop, menu bar)**. iOS/Windows/Linux targets are scaffolded for later.

## Architecture

| Layer | Module | Notes |
|---|---|---|
| UI | `composeApp` | Compose Multiplatform — Android + Desktop entry points, shared UI in `commonMain` |
| Wire format | `core-protocol` | Control frames (JSON) + binary `ClipChunk` |
| Crypto | `core-crypto` | X25519 + ChaCha20-Poly1305 (libsodium, placeholder impl for now) |
| Transport | `core-transport` | Ktor WebSocket client + `Transport`/`Discovery` interfaces |
| Pairing | `core-pairing` | QR payload, state machine, stored pairings |
| Relay | `relay-server` | Optional zero-knowledge Ktor server for cloud fallback |

Devices pair via a QR code. All clipboard payloads are end-to-end encrypted with a per-pair key — the relay only sees ciphertext.

## Build

Android:
```
./gradlew :composeApp:assembleDebug
```

Desktop (menu bar app):
```
./gradlew :composeApp:run
```

Relay server:
```
./gradlew :relay-server:run
```

Tests:
```
./gradlew allTests
```

## Status

This is Phase 1 — scaffolding. Modules compile and the app shows placeholder UI. Subsequent phases (protocol wiring, LAN discovery, pairing, clipboard watchers, content types, relay fallback, hardening) are tracked in `/root/.claude/plans/i-want-to-build-sunny-torvalds.md`.
