## MonkeySwim

Android-native game with gameplay nearly identical to Pac-Man, themed as a monkey swimming through canals and being chased by piranhas. Built in Kotlin with `SurfaceView` + a custom 60 FPS game thread (no engine).

### Splash screen

When the app launches, the player sees a splash screen overlaid on the (frozen) game. It shows the title, the copyright "© 2026 Lionstone Software", a brief instruction line — *"Help the monkey avoid piranhas and collect tasty snacks from the river!"* — a difficulty selector, a Start button, and a clickable "Privacy Policy" link to https://lionstone.dev/privacy/. Three difficulty levels:

- **Easy** — 75% of base speed
- **Medium** — 100% of base speed (default)
- **Hard** — 125% of base speed

The multiplier scales both the monkey's and the piranhas' base speeds (relative pace stays the same), so Easy is genuinely a slower / more forgiving game. The fright and "eaten/eyes-only" return speeds are constants and not scaled.

The monkey starts stationary at spawn — the game waits for the player's first swipe before the monkey begins moving.

### Maze

Each level is a water level: dark blue depth-gradient water with animated wavy bands and drifting caustic highlights, surrounded by canal-bank "walls" (dark brown rounded rectangles inset on path-facing sides so corridors look wider).

Three kinds of gateway:

- **Wrap tunnels** — a single contiguous span of three `T` tiles on the top row, with a matching three-tile span on the bottom row in the same columns; the monkey (and any piranha) walking off one edge through those columns wraps to the other. Each level has **exactly one** such 3-cell tunnel — never split into multiple groups and never wider than 3 — because the player perceives a wider/split span as multiple distinct portals. The position of the 3-cell group varies per level for visual identity (e.g. L1 cols 6-8, L2 cols 4-6, L8 cols 8-10, L10 cols 11-13, etc.). The two ends must use the same column set since `Maze.tunnelWrap` teleports by column without verifying the destination is also a `T`.
- **Level portal** — `X` tiles, on either the left or right wall (exactly one wall per level, three vertically-adjacent cells). Locks while pellets remain; unlocks (glowing animated cyan) once the last pellet is eaten. Walking into an unlocked portal transitions the player to the next level.

Every level is 15 cols × 22 rows. Each layout ships as 22 strings of 15 chars; the monkey-spawn cell is marked `M` and piranha-spawn cells are derived from the `===` pen-interior bounding box (always the four corners). The monkey spawn stays at **(col 7, row 18)** across all levels so the player's starting orientation is constant — what varies per level is the pen position, the portal wall, and the tunnel columns:

- **Level 1** — pen centre, portal right wall, tunnel cols 6-8 (baseline).
- **Levels 2-9** — each level carries a unique combination of pen position (top/middle/bottom × left/centre/right), portal wall (left or right), and tunnel column triple, so the gameplay feel changes per level: piranhas emerge from a different direction and the exit is in a different place.
- **Level 10** — densest corridor patterns. Pen centre, portal on the left wall (mirror of L1's right-wall default), tunnel cols 11-13 (far right, opposite the portal).

Levels beyond 10 cycle back to layout 1 but the difficulty knobs (speed, piranha count) keep applying — see "Challenge mode" below. Every level keeps four power-pellet positions, 1-tile-wide corridors as a guideline (a few small 2×2 walkable cells are allowed near portal-access cells or vestibules when strict avoidance would wall in piranhas or isolate pellets), all pellets reachable from spawn, and no dead-end pellets. Piranha AI is fully data-driven from the layout chars (`maze.penExitTile` and `maze.piranhaSpawnTiles`), so the per-level pen position needs no AI changes.

In debug builds (`BuildConfig.DEBUG`) a small bar appears under the HUD with two controls: a **"DEBUG · Level" Spinner** that jumps directly to any of the ten levels without having to clear the previous one, and a **"FRUIT" button** that triggers the power-pellet fright effect (piranhas frightened for 6.5s, chain bonus reset) without needing to find and eat one. Both are hidden in release builds.

### Power pellets — fruit

Each power-pellet position renders as a randomly-chosen fruit, picked once at level construction so the same fruit stays put until eaten. Possible fruits: **apple, orange, grapes, strawberry, pineapple**. Eating one frightens all active piranhas for 6.5 seconds; while frightened the player can eat them for an escalating chain bonus (200 → 400 → 800 → 1600).

### Piranha pen + staggered release

Each level has a 3×2 pen; **position varies per level** (top/middle/bottom × left/centre/right). The pen door always sits at the top of the pen (`Maze.penExitTile` is hard-coded as `doorRow - 1`), rendered as a horizontal pink bar that bridges across the door's actual entryway (extends slightly into the flanking walls' insetted edges so it visually "anchors" to them). The cell directly above the door is the **vestibule** — non-wall (often a space) and the target piranhas home to in `LEAVING_PEN` mode.

During the **first run-through (levels 1-10)** exactly four piranhas spawn at the four corners of the pen-interior bounding box. They're released through the gate on a staggered schedule from level start — first immediately, then at 4 s, 8 s, and 12 s. In **challenge mode** the count rises by one every five levels (see "Challenge mode" below); extras cycle through the same four spawn corners with their own staggered release (`i * 4f` seconds), so an L10 challenge level has six piranhas trickling out across the first 20 seconds.

After a piranha is killed (eaten frightened, hit by a powerup, etc.) it returns to its spawn corner via a pre-computed BFS shortest-path "flow field" — always optimal, never gets lost.

Piranha behaviours:

- Four personalities (Pac-Man-style): **DIRECT** (Blinky), **AHEAD2** (Pinky), **AHEAD4** (Inky), **ROAMER** (Clyde). Extra challenge-mode piranhas cycle through the same four personalities.
- Three modes: **CHASE**, **FRIGHTENED**, **EATEN** (returning to spawn as eyes only). A fourth state, **LEAVING_PEN**, drives the staggered exit.
- Pac-Man tie-break order at junctions: UP, LEFT, DOWN, RIGHT.

### Powerups

See **`specs/Powerups.md`** for the full powerup spec. Bananas appear periodically on a random walkable tile; collecting one triggers a random powerup (lightning / black hole / shark / slow piranhas + turtle visual). All powerup effects clear when the player loses a life or transitions to a new level.

### Lives + game over

The player starts with 5 lives. On collision with a non-frightened piranha, the monkey shrinks (death animation) and respawns at spawn. When all lives are used the game-over splash screen offers two buttons:

- **Restart** — start a fresh game (also clears challenge mode if it was active).
- **Watch Ad for +3 Lives** — Google AdMob rewarded interstitial; on reward, +3 lives and the player resumes mid-level with the maze, score, and challenge-mode state preserved.

AdMob is currently wired to Google's official **test** App ID and rewarded-ad unit ID, with TODOs to swap in production identifiers before publishing.

### Challenge mode

The first run-through (levels 1-10) is intentionally flat: piranha speed stays at **1.0x** every level and the piranha count stays at **4**. After clearing level 10 the game enters a new `ALL_LEVELS_COMPLETE` phase and an overlay appears with two choices:

- **Restart** — full reset; level 1, score 0, lives 5, challenge mode off.
- **Continue (Harder)** — sets `challengeMode = true`, drops the player back at level 1, **preserves current score and lives**.

In challenge mode two difficulty knobs become active, both routed through GameState helpers (`piranhaSpeedScaleForLevel`, `piranhaCountForLevel`) so the conditional logic lives in one place:

- **Speed ramp** — the existing `Levels.piranhaSpeedScale` ramp applies: `1.0 + 0.08 * (level - 1)`, capped at `1.6x`. So challenge L1 = 1.0x, L2 = 1.08x, …, L9 onward = 1.6x.
- **Piranha count** — `4 + (level / 5)` (integer division). L1-L4 = 4, L5-L9 = 5, L10-L14 = 6, L15-L19 = 7, etc.

Before each level begins, a small banner is rendered above the "Ready!" text announcing what changed vs the previous level:

- Entering challenge mode at L1: **"Challenge!"**
- Speed-only change: **"Speed Up"**
- Piranha-count only: **"+1 Piranha"**
- Both: **"Speed Up · +1 Piranha"**
- No change (e.g. challenge L11-L14, where speed is already capped and no piranha bump): no banner.

The overlay appears **only once per game** — completing 10 more levels in challenge mode does not re-trigger it; challenge mode loops indefinitely with the difficulty knobs still applied. Restarting from game over (lives = 0) clears challenge mode.

Saved games persist the challenge-mode flag (`challenge` JSON field in the snapshot; older saves without the field default to `false`).

### Sound

All SFX are procedurally synthesised in `SoundEngine.kt` (no audio assets). Each effect is generated once at app start into a 16-bit PCM `ShortArray` and uploaded into its own `AudioTrack` in `MODE_STATIC`; `play()` rewinds and re-triggers. Different effects can play simultaneously because each has its own track. See **`specs/SoundEffects.md`** for the per-effect design log (character, fire-point, synthesis recipe).

Effects in the current catalogue: PELLET, FRUIT, LIGHTNING, BLACK_HOLE, SHARK, SLOW_PIRANHAS, EXTRA_LIFE, DEATH, PIRANHA_EATEN, PORTAL, LEVEL_COMPLETE. Triggered from `GameState.updatePlaying` (pellet hook, piranha-eaten branch, gateway-hit branch), `activatePowerup` (powerup hooks), and `onLifeLost`.

`SoundEngine` is owned by `MainActivity` and handed to `GameState` via the `soundEngine` field — null-safe so headless tests don't need an audio device. The HUD top bar has a **🔊 / 🔇** button (rightmost, alongside the `?` Help button); tapping it toggles `SoundEngine.enabled`, which is persisted to `SharedPreferences` (`monkeyswim_settings` / `soundEnabled`, default `true`). When disabled, every `play()` is a no-op. Visual contract matches Brick Basher's Canvas-drawn sound button — white when on, muted gray `#546E7A` when off.

### Sprites

All sprites are procedurally drawn on Canvas (no bitmap assets):

- **Monkey** — top-down primate with a peanut-shaped face mask, forward muzzle with nostril dots, a brow ridge, temple-set ears with pale-tan inner canals, swimming arms with pale palms. 4 directions × 2 frames; suppresses wake/ripple trails when stationary.
- **Piranhas** — gray oval body, red belly stripe, animated tail flick, side fins, jagged teeth, single eye. Frightened tint + low-time blink. Eaten mode renders as eyes-only at 0.45× scale.
- **Shark, turtle, black hole** — used by the powerup system; each is a procedural Canvas draw.

### HUD + controls

Top bar shows Score, Level, Lives, and a **`?`** Help button. The Help button opens a help overlay covering the screen (and pauses an actively-playing game while open) — content includes the same one-line instruction shown on the splash, a reminder that taps pause / resume, and a short list of what each banana powerup does. Closing the overlay resumes play if Help was the thing that paused it; a manually paused game stays paused.

**Pause:** tapping anywhere on the game area (i.e., not on a button) toggles pause / resume. The existing swipe input distinguishes tap-vs-swipe by displacement threshold; small-displacement taps go to `togglePause`.
