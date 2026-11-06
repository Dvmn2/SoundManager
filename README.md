# SoundManager

Fabric client mod that plays and controls sounds on command from a server plugin: start offset, end point, fades, pause/resume, seek, smooth volume and pitch changes. Companion plugin: `SoundManagerPlugin`.

## Requirements

- Minecraft 1.21.11, Java 21
- Fabric Loader >= 0.19.5 and Fabric API (versions are set in `gradle.properties`)
- Companion plugin `SoundManagerPlugin` >= 2.0.0 (the mod does nothing without it); >= 2.1.0 for the Playlists tab

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
├── AtomicFiles.java             // atomic file writes
├── catalog/                     // sound list and sound lengths for the GUI
├── command/                     // client command /sm
├── gui/                         // the SoundManager window
├── mixin/                       // accessors
├── network/                     // payload records
├── preset/                      // saved GUI presets
├── sound/                       // managed sounds and preview
└── state/                       // client state received from the server
```

| Class | Responsibility |
|---|---|
| `SoundManagerClient` | entry point: registers payloads, receivers, tick and disconnect events, `/sm` |
| `SoundManagerConfig` | `config/soundmanager.json`: `enabled`, `maxVolume`; loaded lazily and saved atomically |
| `AtomicFiles` | writes a file through a temporary file and a move; used by the config and the presets |
| `SoundManagerCommand` | `/sm off`, `/sm on`, `/sm status`, `/sm gui` |
| `SoundController` | registry of active and paused sounds, packet handling, seek-availability flag |
| `ManagedSound` | `MovingSoundInstance` with its own timeline, fades, ramps and OpenAL seeking |
| `PlayParams` | immutable description of a sound start (`record`) |
| `PreviewController` | local preview of one `ManagedSound`, separate from `SoundController`; pause keeps the position |
| `SoundManagerScreen` | the GUI: tabs Sound, Parameters, Target, Presets, Playlists; timeline, preview and send buttons |
| `PlaylistTab`, `PlaylistCommand` | the Playlists tab (lists from `PlaylistState`, buttons send `/soundmanager playlist ...`) and the command text builder |
| `PlaylistState` | the playlists of the server as last reported; immutable snapshot replaced as a whole, cleared on disconnect |
| `PreviewMute` | lowers the volume of every other sound while a preview is active and restores it afterwards |
| `SoundSort` | order of the sound list: name or namespace, ascending or descending |
| `PlayForm`, `PlayCommand` | editable values of every `play`/`playat`/`playon` argument; builds the command text |
| `ValueRow`, `PagedButtonList`, `Timeline` | slider plus field, a scrollable list made of buttons, the timeline drawing |
| `GuiState` | the form values kept while the game runs; the entity is dropped on disconnect |
| `OpenGuiKey` | key binding without a default key that opens the GUI |
| `SoundCatalog` | immutable index of sound events built from every `sounds.json` of the loaded resource packs |
| `OggDuration` | reads the length of an Ogg Vorbis file from its last page, without decoding |
| `CatalogLoader` | one background thread for catalog and length reads; caches lengths |
| `PresetStore`, `PresetDto` | `config/soundmanager_presets.json`: named presets of the GUI |
| `Ramp` | linear transition of a value over time |
| `PlayPayload`, `ControlPayload`, `PlaylistPayload` | packet records, format in their Javadoc; `PlaylistInfo` holds a decoded playlist |
| `SoundManagerAccessor`, `SoundSystemAccessor`, `SourceAccessor` | accessors used to reach the OpenAL source of a playing sound |

Threading: the GUI, the preview and the presets run on the client thread. The catalog and the sound lengths are read on the `soundmanager-io` daemon thread by `CatalogLoader`; the screen polls the futures in `tick()`. Packets are handled on the client thread (`context.client().execute`). `SoundController` state is touched on the client thread only. `ManagedSound` seek flags are `volatile` because the seek runs on the sound engine thread.

Lifecycle: `onInitializeClient` loads the config, registers payloads and receivers (this also announces the mod to the server), then tick, disconnect and command hooks. On disconnect all managed sounds are stopped and paused sounds are forgotten.

## Public API

None.

## Protocol

| Channel | Direction | Protocol | Format |
|---|---|---|---|
| `dvmn2:sm_play` | S2C | 1 | `PlayPayload` Javadoc |
| `dvmn2:sm_ctl` | S2C | 1 | `ControlPayload` Javadoc |
| `dvmn2:sm_plist` | S2C | 1 | `PlaylistPayload` Javadoc |

The first field of every packet is the protocol version. A packet with a different version is ignored silently. The mod never sends its own packets to the server. The only thing it sends is a chat command (`/soundmanager play|playat|playon ...`) when the player presses Send in the GUI, or a `/soundmanager playlist ...` command from the Playlists tab; the server's normal permission checks apply. Plugin and mod are compatible when their protocol versions match; the artifact version is independent of it.

## Data and files

| File | Content |
|---|---|
| `config/soundmanager.json` | `enabled` (boolean, default `true`), `maxVolume` (number `0.0..10.0`, default `10.0`), `previewMuteOthers` (boolean, default `true`) |
| `config/soundmanager_presets.json` | `version` (`1`) and `presets`: up to 200 entries, see `PresetDto` (`name` up to 40 characters, `mode`, `targets`, `sound`, `category`, `volume`, `pitch`, `startSec`, `endSec` (`-1` = to the end), `fadeInSec`, `fadeOutSec`, `x`, `y`, `z`) |

The config is created on first start. Unreadable or invalid files fall back to defaults with a log warning; `maxVolume` is clamped after loading. The presets file is created on the first save; a damaged file is treated as empty, invalid or duplicate entries are skipped, and values are clamped when a preset is loaded. No cache and no downloads: sound lengths are kept in memory only.

## Conventions and constraints

- The mod only plays sounds that already exist in the game or in the player's resource packs; nothing is downloaded.
- Seeking relies on internal classes of the sound system (`SoundSystem.sources`, `Source.pointer`) and on `alSourcef(AL_SEC_OFFSET)`. If this access fails once, seeking is disabled for the session, the cause is logged once, and sounds that need a start offset (including resuming a paused sound) are skipped.
- Volume is capped by the player's `maxVolume`; the server cannot bypass it. `/sm off` stops all managed sounds and ignores every packet until `/sm on`.
- A sound with zero volume is never started by Minecraft, so a tiny non-zero minimum volume is used while a seek is pending.
- The preview runs through `PreviewController`, not `SoundController`, so server packets (including "stop everything") never affect it and it is not counted by `/sm status`. It respects `enabled` and `maxVolume`. The window stops the preview when it closes.
- The GUI sends a chat command instead of a packet, so no protocol change was needed. The Send button is enabled only when the command tree sent by the server contains `soundmanager <mode>`: the server sends a player only the commands they may use.
- The command options are positional (`start end fadein fadeout volume pitch category`); `PlayCommand` writes everything up to the last non-default option.
- The sound list and lengths come from `sounds.json` and the `.ogg` files in the resource manager, not from the sound system. For an event with several files the timeline uses the first one. The length is the last Ogg page granule divided by the sample rate; a file that is not Vorbis shows an unknown length.
- Entities for `playon` are chosen from the client world and written as a UUID selector; the preview follows the same entity. They are not stored in presets.
- Lists in the GUI are made of ordinary buttons (`PagedButtonList`) to avoid version-specific list widgets; the timeline is display only.
- Playlists live on the server. The Playlists tab shows what `PlaylistState` holds and changes it only by sending commands; the server answers with new `sm_plist` packets. A track is a copy of a preset's options. For a preset that plays to the end the tab reads the length of the sound file and passes it as the `length` argument; a sound event with several files, or an unknown length, needs an explicit end in the preset. `playon` presets cannot be added. The tab is enabled when the command tree contains `soundmanager playlist`.
- `PlaylistPayload` decodes a packet with another protocol version, an unknown action or too many tracks as an inert packet and skips the rest of the bytes, so a mismatch never disconnects the player.
- Muting during preview: every client tick, while a preview plays or is paused, `PreviewMute` sets a tiny non-zero volume (zero would stop the source) on the source of every other sound except interface sounds (category `ui` or `minecraft:ui.*`). Sounds that tick themselves are muted again each tick; others once. When the preview ends the volume of the others is set back from the sound's own volume and the category slider (tickable sounds set their own). A sound that starts between two ticks may be heard for up to one tick. No mixin is needed beyond the existing accessors; on any error the feature disables itself for the session and logs once.
- The sort and the "Exclude Minecraft" switch of the Sound tab are kept in `GuiState` (until the game closes) and are not written to the config.
- Accessor members use the `soundmanager$` prefix; the mixin config lists every class of the mixin package.

## License

All Rights Reserved.
