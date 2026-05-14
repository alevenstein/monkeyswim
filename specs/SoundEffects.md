# MonkeySwim — Sound Effects

All sound effects are **procedurally synthesised** in `SoundEngine.kt` — no audio assets. Each effect is generated once at app start into a 16-bit PCM `ShortArray` and uploaded into its own `AudioTrack` in `MODE_STATIC`; `play()` rewinds and re-triggers. Different effects can play simultaneously because each has its own track.

This file is the design log for every effect: what it sounds like, why it was chosen, where it fires, and what the synthesis recipe is. Update it whenever a sound is tweaked.

## Mute toggle

The `enabled` flag on `SoundEngine` is the global mute. Persisted to `SharedPreferences` (`monkeyswim_settings` / `soundEnabled`, default `true`) so the choice survives process restarts. The HUD has a **🔊 / 🔇** button (rightmost on the top bar, white when on, muted gray `#546E7A` when off) that toggles it — same visual contract as Brick Basher's Canvas-drawn sound button, just rendered as an Android `Button` to match the rest of the MonkeySwim HUD.

When `enabled` is `false`, every `play()` call is a no-op.

## Effects

### PELLET — regular pellet eat
- **Character**: dry click, no tonal body — like a stick tap.
- **Fires at**: `GameState.updatePlaying` → `consumePelletAt` callback when the eaten tile is `Tile.PELLET`.
- **Recipe**: linear-congruential noise → first-order high-pass filter (α ≈ 0.92 cuts everything below ~2 kHz) → fast exponential decay envelope (e⁻³²⁰ᵗ). 22 ms total.

### FRUIT — power pellet eat
- **Character**: bright three-note ascending sine arpeggio.
- **Fires at**: `consumePelletAt` callback when the eaten tile is `Tile.POWER_PELLET` (same hook as PELLET, branch on `kind`).
- **Recipe**: sine tones at C5 (523.25 Hz), E5 (659.25 Hz), G5 (783.99 Hz), 85 ms each with attack-and-tail-release ADSR. 255 ms total.

### LIGHTNING — banana powerup
- **Character**: descending zap with noise.
- **Fires at**: `activatePowerup(Powerup.LIGHTNING)`.
- **Recipe**: tone descending 4 kHz → 500 Hz over 280 ms + LP-broadband noise overlay, with linear fadeout.

### BLACK_HOLE — banana powerup
- **Character**: ominous sub-bass slide.
- **Fires at**: `activatePowerup(Powerup.BLACK_HOLE)`.
- **Recipe**: exponential pitch slide 200 Hz → ~45 Hz with a sub-octave layer (0.5×freq, 30% mix), 650 ms.

### SHARK — banana powerup
- **Character**: low growl.
- **Fires at**: `activatePowerup(Powerup.SHARK)`.
- **Recipe**: sawtooth at ~110 Hz with mild 4 Hz pitch wobble (±8 Hz) + 20% noise, 500 ms.

### SLOW_PIRANHAS — banana powerup
- **Character**: wobbly 440 Hz tone — matches the turtle visual.
- **Fires at**: `activatePowerup(Powerup.SLOW_PIRANHAS)`.
- **Recipe**: 440 Hz sine with 7 Hz vibrato at ±18 Hz, slowly decaying envelope, 650 ms.

### EXTRA_LIFE — banana powerup
- **Character**: cheerful four-note ascending arpeggio.
- **Fires at**: `activatePowerup(Powerup.EXTRA_LIFE)`.
- **Recipe**: sine tones at C5, E5, G5, C6 (the octave), 90 ms each. 360 ms total.

### DEATH — monkey loses a life
- **Character**: sad "wah-wah-waaah" descending trombone — minor descent with a sustained final note.
- **Fires at**: `GameState.onLifeLost()`.
- **Recipe**: three slightly-detuned sine pairs at D5 (587.33 Hz), A♯4 (466.16 Hz), F4 (349.23 Hz). Each note uses two sines at `f` and `1.008·f` for a woozy chorus that reads as melancholy. First two notes 180 ms, last note 380 ms. 740 ms total.

### PIRANHA_EATEN — monkey eats a frightened piranha
- **Character**: quick rising bleep — classic coin/pickup chirp.
- **Fires at**: `GameState.updatePlaying` piranha-collision branch when `p.mode == Piranha.Mode.FRIGHTENED`, immediately before `p.markEaten()`.
- **Recipe**: sine pitch-sweep 600 Hz → 1200 Hz over 180 ms, fast attack + exponential tail.

### PORTAL — monkey enters the level-exit gateway
- **Character**: warp/teleport whoosh.
- **Fires at**: `GameState.updatePlaying` gateway-hit branch (same frame as `LEVEL_COMPLETE`).
- **Recipe**: ascending sine 400 Hz → 1200 Hz with HP-filtered noise overlay (25% mix), bell-shaped envelope `sin(p·π)`, 420 ms.

### LEVEL_COMPLETE — level cleared celebration
- **Character**: triumphant five-note ascending major arpeggio — more elaborate than the fruit jingle.
- **Fires at**: same frame as PORTAL (gateway-hit branch). The two timbres are different enough that the ear separates them: PORTAL = warp, LEVEL_COMPLETE = celebration.
- **Recipe**: sine tones at C5, E5, G5, C6, E6, 100 ms each. 500 ms total.

### BAIT — bait fish dropped in the water
- **Character**: wet "plop" — descending splashy tone.
- **Fires at**: `GameState.placeBait()`, when the player taps the 🐟 HUD button while charges > 0 and no bait is currently active.
- **Recipe**: sine pitch-sweep 800 Hz → 300 Hz + decaying noise overlay, 180 ms.

## Synthesis primitives

Reusable building blocks in `SoundEngine`:

- `concatSineNotes(freqs, noteMs, amp)` — back-to-back sine tones with quick attack and a tail release per note. Used by FRUIT, EXTRA_LIFE, LEVEL_COMPLETE.
- `Lcg(seed)` — tiny linear-congruential PRNG. Cheap, deterministic per generator, returns `[0,1]` floats. Use `next()*2-1` for bipolar noise.
- `Float.toClippedShort()` — coerces to `[-1, 1]` then scales to `Int16`.

## Tuning notes

Procedural sounds tend to need ear-driven tuning. If a sound feels off, the relevant generator function is short and self-contained — adjust the constants (duration, frequency, envelope rate, mix balance) in one place. The most common knobs:
- **Duration**: `ms` at top of each generator.
- **Pitch**: `freq` (often a sweep — adjust the start/end frequencies).
- **Decay rate**: the multiplier inside `exp(-t * X)` — bigger X = faster decay.
- **Volume**: the final `* 0.X` multiplier before `toClippedShort()`. Keep below ~0.6 to leave headroom for overlapping playback.
