## MonkeySwim

Android-native game with gameplay nearly identical to Pac-Man, themed as a monkey swimming through canals and being chased by piranhas. Built in Kotlin with `SurfaceView` + a custom 60 FPS game thread (no engine).

### Splash screen

When the app launches, the player sees a splash screen overlaid on the (frozen) game. It shows the title, the copyright "© 2026 Lionstone Software", a difficulty selector, a Start button, and a clickable "Privacy Policy" link to https://lionstone.dev/privacy/. Three difficulty levels:

- **Easy** — 75% of base speed
- **Medium** — 100% of base speed (default)
- **Hard** — 125% of base speed

The multiplier scales both the monkey's and the piranhas' base speeds (relative pace stays the same), so Easy is genuinely a slower / more forgiving game. The fright and "eaten/eyes-only" return speeds are constants and not scaled.

The monkey starts stationary at spawn — the game waits for the player's first swipe before the monkey begins moving.

### Maze

Each level is a water level: dark blue depth-gradient water with animated wavy bands and drifting caustic highlights, surrounded by canal-bank "walls" (dark brown rounded rectangles inset on path-facing sides so corridors look wider).

Three gateways:

- **Left tunnel** at the tunnel row — the monkey (and any piranha) walking off the left edge wraps to the right edge.
- **Right tunnel** — mirror; wraps to the left.
- **Bottom gateway** — three locked tiles (rendered dark) centred at the bottom edge. Locks while pellets remain; unlocks (glowing animated cyan) once the last pellet is eaten. Walking into an unlocked gateway transitions the player to the next level.

The first level layout (15 cols × 22 rows) is a hand-laid level with two stacked 1-tile-tall passages at the bottom separated by a wall row, four power-pellet positions near the four corners of the playable area, and 1-tile-wide corridors throughout (no big open zones for the monkey to get lost in mid-air). All pellets reachable from spawn, no dead-end pellets.

### Power pellets — fruit

Each power-pellet position renders as a randomly-chosen fruit, picked once at level construction so the same fruit stays put until eaten. Possible fruits: **apple, orange, grapes, strawberry, pineapple**. Eating one frightens all active piranhas for 6.5 seconds; while frightened the player can eat them for an escalating chain bonus (200 → 400 → 800 → 1600).

### Piranha pen + staggered release

Four piranhas spawn inside a 3×2 pen near the centre of the maze. The pen has a single 1-tile gate at the top, rendered as a horizontal pink bar that bridges across the door's actual entryway (extends slightly into the flanking walls' insetted edges so it visually "anchors" to them). Piranhas are released through the gate on a staggered schedule from level start — first immediately, then at 4 s, 8 s, and 12 s. After a piranha is killed (eaten frightened, hit by a powerup, etc.) it returns to its spawn corner via a pre-computed BFS shortest-path "flow field" — always optimal, never gets lost.

Piranha behaviours:

- Four personalities (Pac-Man-style): **DIRECT** (Blinky), **AHEAD2** (Pinky), **AHEAD4** (Inky), **ROAMER** (Clyde).
- Three modes: **CHASE**, **FRIGHTENED**, **EATEN** (returning to spawn as eyes only). A fourth state, **LEAVING_PEN**, drives the staggered exit.
- Pac-Man tie-break order at junctions: UP, LEFT, DOWN, RIGHT.

### Powerups

See **`specs/Powerups.md`** for the full powerup spec. Bananas appear periodically on a random walkable tile; collecting one triggers a random powerup (lightning / black hole / shark / slow piranhas + turtle visual). All powerup effects clear when the player loses a life or transitions to a new level.

### Lives + game over

The player starts with 3 lives. On collision with a non-frightened piranha, the monkey shrinks (death animation) and respawns at spawn. When all lives are used the game-over splash screen offers two buttons:

- **Restart** — start a fresh game.
- **Watch Ad for +3 Lives** — Google AdMob rewarded interstitial; on reward, +3 lives and the player resumes mid-level with the maze and score preserved.

AdMob is currently wired to Google's official **test** App ID and rewarded-ad unit ID, with TODOs to swap in production identifiers before publishing.

### Sprites

All sprites are procedurally drawn on Canvas (no bitmap assets):

- **Monkey** — top-down primate with a peanut-shaped face mask, forward muzzle with nostril dots, a brow ridge, temple-set ears with pale-tan inner canals, swimming arms with pale palms. 4 directions × 2 frames; suppresses wake/ripple trails when stationary.
- **Piranhas** — gray oval body, red belly stripe, animated tail flick, side fins, jagged teeth, single eye. Frightened tint + low-time blink. Eaten mode renders as eyes-only at 0.45× scale.
- **Shark, turtle, black hole** — used by the powerup system; each is a procedural Canvas draw.

### HUD

Top bar shows Score, Level, Lives, and a Pause / Resume button.
