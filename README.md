# MonkeySwim

An Android-native, Pac-Man-inspired arcade game where a monkey swims through canals collecting bananas and dodging piranhas.

Built per `specs/MonkeySwim.md`.

## Building

This is a standard Kotlin Android project (Gradle).

1. **Open in Android Studio** (Hedgehog 2023.1.1 or newer recommended). Android Studio will offer to install the matching Gradle wrapper / Android SDK components on first sync.
2. Wait for the initial Gradle sync to finish.
3. Connect a device or start an emulator (API 24+, i.e. Android 7.0+).
4. Press **Run ▶** (or `Shift+F10`).

If you prefer the command line and already have a JDK 17 + Android SDK + a populated `local.properties`:

```powershell
# from repo root
.\gradlew assembleDebug                  # build APK
.\gradlew installDebug                   # install on connected device
```

The gradle-wrapper jar/scripts are not committed; Android Studio adds them on first sync. If you want a CLI-only setup, run `gradle wrapper --gradle-version 8.4` once.

## Project layout

```
app/src/main/
├── AndroidManifest.xml
├── java/com/monkeyswim/game/
│   ├── MainActivity.kt        — hosts GameView + HUD + game-over overlay
│   ├── GameView.kt            — SurfaceView, owns the game thread, swipe input
│   ├── GameThread.kt          — fixed-step game loop
│   ├── GameState.kt           — phase machine (READY → PLAYING → LIFE_LOST/LEVEL_COMPLETE/GAME_OVER)
│   ├── Maze.kt                — tile grid, pellet count, gateway lock, water/bank rendering
│   ├── Levels.kt              — level-1 maze layout (28×29 grid) + spawns + difficulty scaling
│   ├── Tile.kt, Direction.kt  — small enums
│   ├── Monkey.kt              — player, tile-aligned movement with queued turns
│   ├── Piranha.kt             — chase/scatter/frightened AI with 4 personalities
│   ├── SpriteRenderer.kt      — procedural top-down monkey + piranha sprites (4 directions × 2 frames)
│   └── AdMobController.kt     — Google Mobile Ads rewarded-ad scaffold
└── res/                        — strings, colors, themes, layouts, icons
```

## Gameplay

| Element | Behaviour |
|--|--|
| Monkey | Tile-aligned movement at ~9 tiles/sec. Swipe to set direction; the next legal turn commits at the upcoming tile boundary. Reverse turns commit immediately. |
| Pellet (banana coin) | +10 points each. |
| Power pellet (golden banana) | +50 points. Triggers a 6.5 s frighten phase: piranhas turn blue, slow down, and become edible (200/400/800/1600 chain). |
| Piranhas | Four start positions in the corridors next to the central pen. Personalities: DIRECT (chase), AHEAD2, AHEAD4, ROAMER (scatters when close). |
| Tunnels | Row 13 wraps left ↔ right. |
| Bottom gateway | 4-wide gate in the bottom wall. Locked (drawn dark) until the last pellet is eaten, then it pulses cyan. Walking into it advances to the next level. |
| Level progression | Each level reuses Level 1's layout for now; piranha speed scales by 8 % per level (capped at 1.6×). |
| Lives | Three to start. On the third loss the game-over splash appears. |
| Game over | Two buttons: **Restart** wipes the game; **Watch Ad for +3 Lives** triggers an AdMob rewarded ad and grants +3 lives + resumes the current level. |

## AdMob

Wired to Google's official **test** IDs so the build runs out-of-the-box:

- App ID (in `AndroidManifest.xml`): `ca-app-pub-3940256099942544~3347511713`
- Rewarded ad unit (in `AdMobController.kt`): `ca-app-pub-3940256099942544/5224354917`

Before publishing, replace **both** IDs with your own from the AdMob console. Search for `TODO` in `AndroidManifest.xml` and `AdMobController.kt`.

## Adding a new level

1. Add a `LEVEL_N` layout (a 22-row × 15-char `List<String>`) inside `Levels.kt`.
2. Append it to the `LEVELS` list — `LEVEL_COUNT` and the debug-mode level-selector spinner pick it up automatically.
3. Each layout *must* contain exactly one `M` (monkey spawn) and at least one `=` (pen interior — drives both pen visuals and piranha spawn cells). The init validator catches missing `M`s, mismatched row widths, and wrong row counts.

Tile chars: `W` wall, `.` pellet, `o` power pellet, `(space)` empty water, `M` monkey spawn (PATH; no pellet), `-` pen door, `=` pen interior + piranha spawn, `T` tunnel mouth, `X` level portal.

In debug builds (`./gradlew :app:assembleDebug`), a "DEBUG · Level" spinner appears under the HUD and lets you jump to any level instantly via `GameState.jumpToLevel`. It's hidden in release builds via `BuildConfig.DEBUG`.

## Known limitations / future work

- Sprites are procedurally drawn for v1. To replace with hand-drawn art, swap calls in `SpriteRenderer` with `BitmapFactory.decodeResource` + a sprite-sheet atlas keyed on (direction, frame).
- Piranhas don't currently emerge from the pen; they spawn outside it. Implementing the classic pen-exit timing is straightforward (move spawns inside `=` tiles and add an exit timer per piranha).
- Ten maze layouts ship with the game (`LEVEL_1` … `LEVEL_10` in `Levels.kt`); levels beyond ten cycle back to layout 1 while difficulty keeps climbing via piranha speed.
- AdMob uses test IDs only — flip both in the manifest and `AdMobController` before shipping.
- No sound yet.
