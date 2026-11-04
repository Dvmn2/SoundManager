# SoundManager

Fabric client mod that plays and controls sounds on command from a server plugin: start offset, end point, fades, pause/resume, seek, smooth volume and pitch changes. Companion plugin: `SoundManagerPlugin`.

## Requirements

- Minecraft 1.21.11, Java 21
- Fabric Loader >= 0.19.5 and Fabric API (versions are set in `gradle.properties`)
- Companion plugin `SoundManagerPlugin` >= 2.0.0 (the mod does nothing without it)

## Build and run

```bash
./gradlew build      # build/libs/SoundManager-<version>.jar
./gradlew runClient  # Minecraft 1.21.11 test client
```

## Architecture

```text
src/client/java/net/dvmn2/soundmanager/client
├── SoundManagerClient.java      // ClientModInitializer
├── SoundManagerConfig.java      // player-side config
├── command/                     // client command /sm
├── mixin/                       // accessors
├── network/                     // payload records
└── sound/                       // managed sounds
```

| Class | Responsibility |
|---|---|
| `SoundManagerClient` | entry point: registers payloads, receivers, tick and disconnect events, `/sm` |
| `SoundManagerConfig` | `config/soundmanager.json`: `enabled`, `maxVolume`; loaded lazily and saved atomically |
| `SoundManagerCommand` | `/sm off`, `/sm on`, `/sm status` |
| `SoundController` | registry of active and paused sounds, packet handling, seek-availability flag |
| `ManagedSound` | `MovingSoundInstance` with its own timeline, fades, ramps and OpenAL seeking |
| `PlayParams` | immutable description of a sound start (`record`) |
| `Ramp` | linear transition of a value over time |
| `PlayPayload`, `ControlPayload` | packet records, format in their Javadoc |
| `SoundManagerAccessor`, `SoundSystemAccessor`, `SourceAccessor` | accessors used to reach the OpenAL source of a playing sound |

Threading: packets are handled on the client thread (`context.client().execute`). `SoundController` state is touched on the client thread only. `ManagedSound` seek flags are `volatile` because the seek runs on the sound engine thread.

Lifecycle: `onInitializeClient` loads the config, registers payloads and receivers (this also announces the mod to the server), then tick, disconnect and command hooks. On disconnect all managed sounds are stopped and paused sounds are forgotten.

## Public API

None.

## Protocol

| Channel | Direction | Protocol | Format |
|---|---|---|---|
| `dvmn2:sm_play` | S2C | 1 | `PlayPayload` Javadoc |
| `dvmn2:sm_ctl` | S2C | 1 | `ControlPayload` Javadoc |

The first field of every packet is the protocol version. A packet with a different version is ignored silently. The mod never sends packets to the server. Plugin and mod are compatible when their protocol versions match; the artifact version is independent of it.

## Data and files

| File | Content |
|---|---|
| `config/soundmanager.json` | `enabled` (boolean, default `true`), `maxVolume` (number `0.0..10.0`, default `10.0`) |

The file is created on first start. Unreadable or invalid files fall back to defaults with a log warning; `maxVolume` is clamped after loading. No cache and no downloads.

## Conventions and constraints

- The mod only plays sounds that already exist in the game or in the player's resource packs; nothing is downloaded.
- Seeking relies on internal classes of the sound system (`SoundSystem.sources`, `Source.pointer`) and on `alSourcef(AL_SEC_OFFSET)`. If this access fails once, seeking is disabled for the session, the cause is logged once, and sounds that need a start offset (including resuming a paused sound) are skipped.
- Volume is capped by the player's `maxVolume`; the server cannot bypass it. `/sm off` stops all managed sounds and ignores every packet until `/sm on`.
- A sound with zero volume is never started by Minecraft, so a tiny non-zero minimum volume is used while a seek is pending.
- Accessor members use the `soundmanager$` prefix; the mixin config lists every class of the mixin package.

## License

All Rights Reserved.
