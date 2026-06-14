package com.lionstone.monkeyrapids

/**
 * Level catalog. Each level is a 22×15 layout that *must* contain:
 *   • exactly one 'M' (monkey spawn)
 *   • one '-' (pen door) and a contiguous '===' pen interior block
 *   • exactly one 3-cell 'T' tunnel group on the top row and a matching
 *     group on the bottom row (same columns — `tunnelWrap` in Maze.kt
 *     teleports by column without verifying the destination is also a T)
 *   • exactly one wall of 'X' level-portal cells
 *   • four 'o' power pellets
 *   • optional mechanic tiles (see below); at most one 'K' (crocodile spawn)
 *   • only 1-tile-wide corridors (a soft guideline — small exceptions are
 *     permitted near vestibules / portal-access cells when strict adherence
 *     would wall in the piranhas)
 *
 * Tile chars (see Tile.fromChar):
 *   W = wall (canal bank)
 *   . = pellet (banana coin)
 *   o = power pellet (golden banana)
 *   (space) = empty water (no pellet)
 *   M = monkey spawn (PATH; no pellet)
 *   - = piranha pen door
 *   = = piranha pen interior (also defines piranha spawn cells via bounding-box corners)
 *   T = tunnel mouth (wraps to opposite side along the same column)
 *   X = level portal (locked until last pellet eaten)
 *   ^v<> = current tile flowing UP / DOWN / LEFT / RIGHT (no pellet; speed
 *          modifier ±50% applied to entities moving with / against the flow)
 *   ~ = tide cell (walkable during the high-tide phase of the global
 *       ~6s cycle, wall during the low phase; no pellet)
 *   L = lily pad (slippery — entity entering keeps its direction until it
 *       slides off; no pellet)
 *   K = crocodile spawn (no pellet; GameState spawns a slow patrolling
 *       crocodile entity at this tile)
 *
 * 30-level progression:
 *   • L1-L4 classic gameplay (pen positions / portal walls / tunnel cols vary).
 *   • L5 introduces **CURRENTS**.
 *   • L6-L7 review currents (classic + currents).
 *   • L8 introduces **CROCODILE**.
 *   • L9-L11 mix croc with classic / currents.
 *   • L12 introduces **TIDE**.
 *   • L13-L15 mix tide with classic / currents / croc.
 *   • L16 introduces **LILY PADS**.
 *   • L17-L20 mix lily pads with each prior mechanic in turn.
 *   • L21-L24 three-mechanic combinations (each leaving one mechanic out).
 *   • L25 first level with ALL FOUR mechanics together (light density).
 *   • L26-L29 all four mechanics with escalating density.
 *   • L30 BOSS — densest layout of everything.
 *
 * Monkey spawn stays at (col 7, row 18) across every level so the player's
 * starting orientation is constant. Piranha AI + pen-exit logic are fully
 * driven from the layout chars (`maze.penExitTile`, `maze.piranhaSpawnTiles`),
 * so adding mechanics or moving the pen needs no AI changes. Difficulty also
 * ramps via piranha speed (Levels.piranhaSpeedScale) — though challenge mode
 * is what actually applies the ramp during the first run-through.
 */
object Levels {

    // Level 1 — the hand-laid baseline: pen center, portal on right wall,
    // tunnels centered, corner power pellets.
    private val LEVEL_1: List<String> = listOf(
        "WWWWWWTTTWWWWWW",
        "W.............W",
        "W.WWW.WWW.WWW.W",
        "WoW.........WoW",
        "W.W.WWW.WWW.W.W",
        "W.....W.W.....W",
        "W.WWW.....WWW.W",
        "W.....W W.....W",
        "W.WW.WW-WW.WW.W",
        "W.WW.W===W.WW.W",
        "W.WW.W===W.WW.X",
        "W.WW.WWWWW.WW.X",
        "W.............X",
        "W.WWWW.W.WWWW.W",
        "W.............W",
        "W.WWW.WWW.WWW.W",
        "WoW.........WoW",
        "W.W.WWW.WWW.W.W",
        "W......M......W",
        "W.WWW.WWW.WWW.W",
        "W.............W",
        "WWWWWWTTTWWWWWW",
    )

    // Level 2 — Pen TOP-CENTER (rows 4-7), portal LEFT WALL, tunnels at cols
    // 4-6. Classic gameplay.
    private val LEVEL_2: List<String> = listOf(
        "WWWWTTTWWWWWWWW",
        "W.............W",
        "W.WWW.W.W.WWW.W",
        "W.....W W.....W",
        "W.WW.WW-WW.WW.W",
        "W.WW.W===W.WW.W",
        "W.WW.W===W.WW.W",
        "W.WW.WWWWW.WW.W",
        "W.............W",
        "W.WWW.W.W.WWW.W",
        "XoW.........WoW",
        "X.W.WWWWWWW.W.W",
        "X.............W",
        "W.WWW.WWW.WWW.W",
        "W.............W",
        "WoW.WWW.WWW.WoW",
        "W.W.........W.W",
        "W.W.WWW.WWW.W.W",
        "W......M......W",
        "W.WWW.WWW.WWW.W",
        "W.............W",
        "WWWWTTTWWWWWWWW",
    )

    // Level 3 — Pen BOTTOM-CENTER (rows 13-16), portal RIGHT WALL, tunnels at
    // cols 7-9. Classic gameplay.
    private val LEVEL_3: List<String> = listOf(
        "WWWWWWWTTTWWWWW",
        "W.............W",
        "W.WWW.W.W.WWW.W",
        "W.W.........W.W",
        "W.W.WWW.WWW.W.W",
        "WoW.........WoW",
        "W.WWW.W.W.WWW.W",
        "W.....W.W.....W",
        "W.WWW.W.W.WWW.W",
        "W.............W",
        "W.WWW.W.W.WWW.X",
        "WoW.WWW WWW.WoX",
        "W.....W W.....X",
        "W.WW.WW-WW.WW.W",
        "W.WW.W===W.WW.W",
        "W.WW.W===W.WW.W",
        "W.WW.WWWWW.WW.W",
        "W.WW.WWWWW.WW.W",
        "W......M......W",
        "W.WWW.WWW.WWW.W",
        "W.............W",
        "WWWWWWWTTTWWWWW",
    )

    // Level 4 — Pen LEFT-CENTER (rows 8-11, cols 1-5), portal RIGHT WALL,
    // tunnels at cols 10-12. Classic gameplay.
    private val LEVEL_4: List<String> = listOf(
        "WWWWWWWWWWTTTWW",
        "W.............W",
        "W.WWW.WWW.WWW.W",
        "WoW.........WoW",
        "W.W.WWW.WWW.W.W",
        "W.W...W.W...W.W",
        "W.W.W.WWW.WWW.W",
        "W.W W.........W",
        "WWW-WW.WW.WW.WW",
        "WW===W.WW.WW.WW",
        "WW===W.WW.WW.WX",
        "WWWWWW.WW.WW.WX",
        "W.............X",
        "W.WWW.WWW.WWW.W",
        "W.............W",
        "WoW.WWW.WWW.WoW",
        "W.W.........W.W",
        "W.W.WWW.WWW.W.W",
        "W......M......W",
        "W.WWW.WWW.WWW.W",
        "W.............W",
        "WWWWWWWWWWTTTWW",
    )

    // Level 5 — CURRENTS introduced. Pen centre, portal LEFT WALL, tunnels at
    // cols 11-13. Row 12 is a leftward current toward the portal — gentle
    // introduction to the mechanic.
    private val LEVEL_5: List<String> = listOf(
        "WWWWWWWWWWWTTTW",
        "W.............W",
        "W.W.W.W.W.W.W.W",
        "WoW.........WoW",
        "W.W.WWW.WWW.W.W",
        "W.W...W.W...W.W",
        "W.WWWWW.WWWWW.W",
        "W.....W W.....W",
        "W.WW.WW-WW.WW.W",
        "W.WW.W===W.WW.W",
        "X.WW.W===W.WW.W",
        "X.WW.WWWWW.WW.W",
        "X.<<<<<<<<<<<.W",
        "W.WWWWW.WWWWW.W",
        "W.W...W.W...W.W",
        "WoW.WWW.WWW.WoW",
        "W.W.........W.W",
        "W.W.W.W.W.W.W.W",
        "W......M......W",
        "W.WWW.WWW.WWW.W",
        "W.............W",
        "WWWWWWWWWWWTTTW",
    )

    // Level 6 — Pen TOP-LEFT, portal RIGHT WALL, tunnels at cols 10-12.
    // Classic review level after the currents intro.
    private val LEVEL_6: List<String> = listOf(
        "WWWWWWWWWWTTTWW",
        "W.............W",
        "W.W W.WWW.WWW.W",
        "W.W W.........W",
        "WWW-WW.WW.WW.WW",
        "WW===W.WW.WW.WW",
        "WW===W.WW.WW.WW",
        "WWWWWW.WW.WW.WW",
        "W.............W",
        "W.WWW.W.W.WWW.W",
        "WoW.........WoX",
        "W.W.WWWWWWW.W.X",
        "W.............X",
        "W.WWW.WWW.WWW.W",
        "W.............W",
        "WoW.WWW.WWW.WoW",
        "W.W.........W.W",
        "W.W.WWW.WWW.W.W",
        "W......M......W",
        "W.WWW.WWW.WWW.W",
        "W.............W",
        "WWWWWWWWWWTTTWW",
    )

    // Level 7 — Pen TOP-RIGHT, portal LEFT WALL, tunnels at cols 2-4. A
    // leftward current on row 12 toward the portal — second currents level.
    private val LEVEL_7: List<String> = listOf(
        "WWTTTWWWWWWWWWW",
        "W.............W",
        "W.WWW.WWW.W W.W",
        "W.........W W.W",
        "WW.WW.WW.WW-WWW",
        "WW.WW.WW.W===WW",
        "WW.WW.WW.W===WW",
        "WW.WW.WW.WWWWWW",
        "W.............W",
        "W.WWW.W.W.WWW.W",
        "XoW.........WoW",
        "X.W.WWWWWWW.W.W",
        "X.<<<<<<<<<<<.W",
        "W.WWW.WWW.WWW.W",
        "W.............W",
        "WoW.WWW.WWW.WoW",
        "W.W.........W.W",
        "W.W.WWW.WWW.W.W",
        "W......M......W",
        "W.WWW.WWW.WWW.W",
        "W.............W",
        "WWTTTWWWWWWWWWW",
    )

    // Level 8 — CROCODILE introduced. Pen BOTTOM-LEFT, portal RIGHT WALL,
    // tunnels at cols 8-10. Crocodile patrols row 9 horizontally.
    private val LEVEL_8: List<String> = listOf(
        "WWWWWWWWTTTWWWW",
        "W.............W",
        "W.WWW.WWW.WWW.W",
        "WoW.........WoW",
        "W.W.WWW.WWW.W.W",
        "W.W...W.W...W.W",
        "W.WWW.W.W.WWW.W",
        "W.....W.W.....W",
        "W.WWW.W.W.WWW.W",
        "W......K......W",
        "W.WWW.W.W.WWW.W",
        "Wo..WWW.WWW.WoW",
        "W.W W.........W",
        "WWW-WW.WW.WW.WW",
        "WW===W.WW.WW.WW",
        "WW===W.WW.WW.WX",
        "WWWWWW.WW.WW.WX",
        "W.WWWW.WW.WW. X",
        "W......M......W",
        "W.WWW.WWW.WWW.W",
        "W.............W",
        "WWWWWWWWTTTWWWW",
    )

    // Level 9 — Pen BOTTOM-RIGHT, portal LEFT WALL, tunnels at cols 1-3.
    // Classic + crocodile on row 9.
    private val LEVEL_9: List<String> = listOf(
        "WTTTWWWWWWWWWWW",
        "W.............W",
        "W.WWW.WWW.WWW.W",
        "WoW.........WoW",
        "W.W.WWW.WWW.W.W",
        "W.W...W.W...W.W",
        "W.WWW.WWW.WWW.W",
        "W.....W.W.....W",
        "W.WWW.W.W.WWW.W",
        "W......K......W",
        "W.WWW.W.W.WWW.W",
        "WoW.WWW.WWW..oW",
        "W.........W W.W",
        "WW.WW.WW.WW-WWW",
        "WW.WW.WW.W===WW",
        "XW.WW.WW.W===WW",
        "XW.WW.WW.WWWWWW",
        "X..WW.WW.WWWW.W",
        "W......M......W",
        "W.WWW.WWW.WWW.W",
        "W.............W",
        "WTTTWWWWWWWWWWW",
    )

    // Level 10 — Pen centre, portal RIGHT WALL, tunnels at cols 6-8. Currents
    // on row 12 toward portal + crocodile patrolling row 14.
    private val LEVEL_10: List<String> = listOf(
        "WWWWWWTTTWWWWWW",
        "W.............W",
        "W.WWW.WWW.WWW.W",
        "WoW.........WoW",
        "W.W.WWW.WWW.W.W",
        "W.....W.W.....W",
        "W.WWW.....WWW.W",
        "W.....W W.....W",
        "W.WW.WW-WW.WW.W",
        "W.WW.W===W.WW.W",
        "W.WW.W===W.WW.X",
        "W.WW.WWWWW.WW.X",
        "W.>>>>>>>>>>>.X",
        "W.WWWW.W.WWWW.W",
        "W......K......W",
        "W.WWW.WWW.WWW.W",
        "WoW.........WoW",
        "W.W.WWW.WWW.W.W",
        "W......M......W",
        "W.WWW.WWW.WWW.W",
        "W.............W",
        "WWWWWWTTTWWWWWW",
    )

    // Level 11 — Pen TOP-CENTER, portal LEFT WALL, tunnels at cols 4-6.
    // Classic respite before the tide intro.
    private val LEVEL_11: List<String> = listOf(
        "WWWWTTTWWWWWWWW",
        "W.............W",
        "W.WWW.W.W.WWW.W",
        "W.....W W.....W",
        "W.WW.WW-WW.WW.W",
        "W.WW.W===W.WW.W",
        "W.WW.W===W.WW.W",
        "W.WW.WWWWW.WW.W",
        "W.............W",
        "W.WWW.W.W.WWW.W",
        "XoW.........WoW",
        "X.W.WWWWWWW.W.W",
        "X.............W",
        "W.WWW.WWW.WWW.W",
        "W.............W",
        "WoW.WWW.WWW.WoW",
        "W.W.........W.W",
        "W.W.WWW.WWW.W.W",
        "W......M......W",
        "W.WWW.WWW.WWW.W",
        "W.............W",
        "WWWWTTTWWWWWWWW",
    )

    // Level 12 — TIDE introduced. Pen BOTTOM-CENTER, portal RIGHT WALL,
    // tunnels at cols 7-9. Tide row 9 flips between walkable and wall on the
    // ~6s cycle, opening and closing a passage between upper and lower halves.
    private val LEVEL_12: List<String> = listOf(
        "WWWWWWWTTTWWWWW",
        "W.............W",
        "W.WWW.W.W.WWW.W",
        "W.W.........W.W",
        "W.W.WWW.WWW.W.W",
        "WoW.........WoW",
        "W.WWW.W.W.WWW.W",
        "W.....W.W.....W",
        "W.WWW.W.W.WWW.W",
        "W.~~~~~~~~~~~.W",
        "W.WWW.W W.WWW.X",
        "WoW.WWW WWW.WoX",
        "W.....W W.....X",
        "W.WW.WW-WW.WW.W",
        "W.WW.W===W.WW.W",
        "W.WW.W===W.WW.W",
        "W.WW.WWWWW.WW.W",
        "W.WW.WWWWW.WW.W",
        "W......M......W",
        "W.WWW.WWW.WWW.W",
        "W.............W",
        "WWWWWWWTTTWWWWW",
    )

    // Level 13 — Pen LEFT-CENTER, portal RIGHT WALL, tunnels at cols 10-12.
    // Classic + tide on row 14.
    private val LEVEL_13: List<String> = listOf(
        "WWWWWWWWWWTTTWW",
        "W.............W",
        "W.WWW.WWW.WWW.W",
        "WoW.........WoW",
        "W.W.WWW.WWW.W.W",
        "W.W...W.W...W.W",
        "W.W.W.WWW.WWW.W",
        "W.W W.........W",
        "WWW-WW.WW.WW.WW",
        "WW===W.WW.WW.WW",
        "WW===W.WW.WW.WX",
        "WWWWWW.WW.WW.WX",
        "W.............X",
        "W.WWW.WWW.WWW.W",
        "W.~~~~~~~~~~~.W",
        "WoW.WWW.WWW.WoW",
        "W.W.........W.W",
        "W.W.WWW.WWW.W.W",
        "W......M......W",
        "W.WWW.WWW.WWW.W",
        "W.............W",
        "WWWWWWWWWWTTTWW",
    )

    // Level 14 — Pen RIGHT-CENTER, portal LEFT WALL, tunnels at cols 2-4.
    // Currents row 12 + tide row 14.
    private val LEVEL_14: List<String> = listOf(
        "WWTTTWWWWWWWWWW",
        "W.............W",
        "W.WWW.WWW.WWW.W",
        "WoW.........WoW",
        "W.W.WWW.WWW.W.W",
        "W.W...W.W...W.W",
        "W.WWW.WWW.W.W.W",
        "W.........W W.W",
        "WW.WW.WW.WW-WWW",
        "WW.WW.WW.W===WW",
        "XW.WW.WW.W===WW",
        "XW.WW.WW.WWWWWW",
        "X.<<<<<<<<<<<.W",
        "W.WWW.WWW.WWW.W",
        "W.~~~~~~~~~~~.W",
        "WoW.WWW.WWW.WoW",
        "W.W.........W.W",
        "W.W.WWW.WWW.W.W",
        "W......M......W",
        "W.WWW.WWW.WWW.W",
        "W.............W",
        "WWTTTWWWWWWWWWW",
    )

    // Level 15 — Pen TOP-LEFT, portal RIGHT WALL, tunnels at cols 10-12.
    // Crocodile on row 8 + tide on row 14.
    private val LEVEL_15: List<String> = listOf(
        "WWWWWWWWWWTTTWW",
        "W.............W",
        "W.W W.WWW.WWW.W",
        "W.W W.........W",
        "WWW-WW.WW.WW.WW",
        "WW===W.WW.WW.WW",
        "WW===W.WW.WW.WW",
        "WWWWWW.WW.WW.WW",
        "W......K......W",
        "W.WWW.W.W.WWW.W",
        "WoW.........WoX",
        "W.W.WWWWWWW.W.X",
        "W.............X",
        "W.WWW.WWW.WWW.W",
        "W.~~~~~~~~~~~.W",
        "WoW.WWW.WWW.WoW",
        "W.W.........W.W",
        "W.W.WWW.WWW.W.W",
        "W......M......W",
        "W.WWW.WWW.WWW.W",
        "W.............W",
        "WWWWWWWWWWTTTWW",
    )

    // Level 16 — LILY PADS introduced. Pen TOP-RIGHT, portal LEFT WALL,
    // tunnels at cols 2-4. A 7-cell lily-pad chain on row 1 — step on and
    // slide until you exit.
    private val LEVEL_16: List<String> = listOf(
        "WWTTTWWWWWWWWWW",
        "W...LLLLLLL...W",
        "W.WWW.WWW.W W.W",
        "W.........W W.W",
        "WW.WW.WW.WW-WWW",
        "WW.WW.WW.W===WW",
        "WW.WW.WW.W===WW",
        "WW.WW.WW.WWWWWW",
        "W.............W",
        "W.WWW.W.W.WWW.W",
        "XoW.........WoW",
        "X.W.WWWWWWW.W.W",
        "X.............W",
        "W.WWW.WWW.WWW.W",
        "W.............W",
        "WoW.WWW.WWW.WoW",
        "W.W.........W.W",
        "W.W.WWW.WWW.W.W",
        "W......M......W",
        "W.WWW.WWW.WWW.W",
        "W.............W",
        "WWTTTWWWWWWWWWW",
    )

    // Level 17 — Pen BOTTOM-LEFT, portal RIGHT WALL, tunnels at cols 8-10.
    // Classic + lily pads on row 9.
    private val LEVEL_17: List<String> = listOf(
        "WWWWWWWWTTTWWWW",
        "W.............W",
        "W.WWW.WWW.WWW.W",
        "WoW.........WoW",
        "W.W.WWW.WWW.W.W",
        "W.W...W.W...W.W",
        "W.WWW.W.W.WWW.W",
        "W.....W.W.....W",
        "W.WWW.W.W.WWW.W",
        "W...LLLLLLL...W",
        "W.WWW.W.W.WWW.W",
        "Wo..WWW.WWW.WoW",
        "W.W W.........W",
        "WWW-WW.WW.WW.WW",
        "WW===W.WW.WW.WW",
        "WW===W.WW.WW.WX",
        "WWWWWW.WW.WW.WX",
        "W.WWWW.WW.WW. X",
        "W......M......W",
        "W.WWW.WWW.WWW.W",
        "W.............W",
        "WWWWWWWWTTTWWWW",
    )

    // Level 18 — Pen BOTTOM-RIGHT, portal LEFT WALL, tunnels at cols 1-3.
    // Lily pads row 1 + currents row 9 (left flow).
    private val LEVEL_18: List<String> = listOf(
        "WTTTWWWWWWWWWWW",
        "W...LLLLLLL...W",
        "W.WWW.WWW.WWW.W",
        "WoW.........WoW",
        "W.W.WWW.WWW.W.W",
        "W.W...W.W...W.W",
        "W.WWW.WWW.WWW.W",
        "W.....W.W.....W",
        "W.WWW.W.W.WWW.W",
        "W.<<<<<<<<<<<.W",
        "W.WWW.W.W.WWW.W",
        "WoW.WWW.WWW..oW",
        "W.........W W.W",
        "WW.WW.WW.WW-WWW",
        "WW.WW.WW.W===WW",
        "XW.WW.WW.W===WW",
        "XW.WW.WW.WWWWWW",
        "X .WW.WW.WWWW.W",
        "W......M......W",
        "W.WWW.WWW.WWW.W",
        "W.............W",
        "WTTTWWWWWWWWWWW",
    )

    // Level 19 — Pen centre, portal RIGHT WALL, tunnels at cols 6-8. Lily
    // pads row 1 + crocodile on row 14.
    private val LEVEL_19: List<String> = listOf(
        "WWWWWWTTTWWWWWW",
        "W...LLLLLLL...W",
        "W.WWW.WWW.WWW.W",
        "WoW.........WoW",
        "W.W.WWW.WWW.W.W",
        "W.....W.W.....W",
        "W.WWW.....WWW.W",
        "W.....W W.....W",
        "W.WW.WW-WW.WW.W",
        "W.WW.W===W.WW.W",
        "W.WW.W===W.WW.X",
        "W.WW.WWWWW.WW.X",
        "W.............X",
        "W.WWWW.W.WWWW.W",
        "W......K......W",
        "W.WWW.WWW.WWW.W",
        "WoW.........WoW",
        "W.W.WWW.WWW.W.W",
        "W......M......W",
        "W.WWW.WWW.WWW.W",
        "W.............W",
        "WWWWWWTTTWWWWWW",
    )

    // Level 20 — Pen TOP-CENTER, portal LEFT WALL, tunnels at cols 4-6.
    // Lily pads row 1 + tide row 14.
    private val LEVEL_20: List<String> = listOf(
        "WWWWTTTWWWWWWWW",
        "W...LLLLLLL...W",
        "W.WWW.W.W.WWW.W",
        "W.....W W.....W",
        "W.WW.WW-WW.WW.W",
        "W.WW.W===W.WW.W",
        "W.WW.W===W.WW.W",
        "W.WW.WWWWW.WW.W",
        "W.............W",
        "W.WWW.W.W.WWW.W",
        "XoW.........WoW",
        "X.W.WWWWWWW.W.W",
        "X.............W",
        "W.WWW.WWW.WWW.W",
        "W.~~~~~~~~~~~.W",
        "WoW.WWW.WWW.WoW",
        "W.W.........W.W",
        "W.W.WWW.WWW.W.W",
        "W......M......W",
        "W.WWW.WWW.WWW.W",
        "W.............W",
        "WWWWTTTWWWWWWWW",
    )

    // Level 21 — Pen BOTTOM-CENTER, portal RIGHT WALL, tunnels at cols 7-9.
    // Three mechanics: crocodile row 1 + currents row 9 (right toward portal)
    // + tide row 20.
    private val LEVEL_21: List<String> = listOf(
        "WWWWWWWTTTWWWWW",
        "W......K......W",
        "W.WWW.W.W.WWW.W",
        "W.W.........W.W",
        "W.W.WWW.WWW.W.W",
        "WoW.........WoW",
        "W.WWW.W.W.WWW.W",
        "W.....W.W.....W",
        "W.WWW.W.W.WWW.W",
        "W.>>>>>>>>>>>.W",
        "W.WWW.W W.WWW.X",
        "WoW.WWW WWW.WoX",
        "W.....W W.....X",
        "W.WW.WW-WW.WW.W",
        "W.WW.W===W.WW.W",
        "W.WW.W===W.WW.W",
        "W.WW.WWWWW.WW.W",
        "W.WW.WWWWW.WW.W",
        "W......M......W",
        "W.WWW.WWW.WWW.W",
        "W.~~~~~~~~~~~.W",
        "WWWWWWWTTTWWWWW",
    )

    // Level 22 — Pen LEFT-CENTER, portal RIGHT WALL, tunnels at cols 10-12.
    // Three mechanics: lily pads row 1 + currents row 12 (right to portal) +
    // crocodile row 14.
    private val LEVEL_22: List<String> = listOf(
        "WWWWWWWWWWTTTWW",
        "W...LLLLLLL...W",
        "W.WWW.WWW.WWW.W",
        "WoW.........WoW",
        "W.W.WWW.WWW.W.W",
        "W.W...W.W...W.W",
        "W.W.W.WWW.WWW.W",
        "W.W W.........W",
        "WWW-WW.WW.WW.WW",
        "WW===W.WW.WW.WW",
        "WW===W.WW.WW.WX",
        "WWWWWW.WW.WW.WX",
        "W.>>>>>>>>>>>.X",
        "W.WWW.WWW.WWW.W",
        "W......K......W",
        "WoW.WWW.WWW.WoW",
        "W.W.........W.W",
        "W.W.WWW.WWW.W.W",
        "W......M......W",
        "W.WWW.WWW.WWW.W",
        "W.............W",
        "WWWWWWWWWWTTTWW",
    )

    // Level 23 — Pen RIGHT-CENTER, portal LEFT WALL, tunnels at cols 2-4.
    // Three mechanics: lily pads row 1 + currents row 12 (left to portal) +
    // tide row 14.
    private val LEVEL_23: List<String> = listOf(
        "WWTTTWWWWWWWWWW",
        "W...LLLLLLL...W",
        "W.WWW.WWW.WWW.W",
        "WoW.........WoW",
        "W.W.WWW.WWW.W.W",
        "W.W...W.W...W.W",
        "W.WWW.WWW.W W.W",
        "W.........W W.W",
        "WW.WW.WW.WW-WWW",
        "WW.WW.WW.W===WW",
        "XW.WW.WW.W===WW",
        "XW.WW.WW.WWWWWW",
        "X.<<<<<<<<<<<.W",
        "W.WWW.WWW.WWW.W",
        "W.~~~~~~~~~~~.W",
        "WoW.WWW.WWW.WoW",
        "W.W.........W.W",
        "W.W.WWW.WWW.W.W",
        "W......M......W",
        "W.WWW.WWW.WWW.W",
        "W.............W",
        "WWTTTWWWWWWWWWW",
    )

    // Level 24 — Pen TOP-LEFT, portal RIGHT WALL, tunnels at cols 10-12.
    // Three mechanics: lily pads row 1 + crocodile row 8 + tide row 14.
    private val LEVEL_24: List<String> = listOf(
        "WWWWWWWWWWTTTWW",
        "W...LLLLLLL...W",
        "W.W W.WWW.WWW.W",
        "W.W W.........W",
        "WWW-WW.WW.WW.WW",
        "WW===W.WW.WW.WW",
        "WW===W.WW.WW.WW",
        "WWWWWW.WW.WW.WW",
        "W......K......W",
        "W.WWW.W.W.WWW.W",
        "WoW.........WoX",
        "W.W.WWWWWWW.W.X",
        "W.............X",
        "W.WWW.WWW.WWW.W",
        "W.~~~~~~~~~~~.W",
        "WoW.WWW.WWW.WoW",
        "W.W.........W.W",
        "W.W.WWW.WWW.W.W",
        "W......M......W",
        "W.WWW.WWW.WWW.W",
        "W.............W",
        "WWWWWWWWWWTTTWW",
    )

    // Level 25 — FIRST 4-MECHANIC LEVEL. Pen TOP-RIGHT, portal LEFT WALL,
    // tunnels at cols 2-4. Lily row 1 + crocodile row 8 + currents row 12
    // (left to portal) + tide row 14 — light density of each.
    private val LEVEL_25: List<String> = listOf(
        "WWTTTWWWWWWWWWW",
        "W...LLLLLLL...W",
        "W.WWW.WWW.W W.W",
        "W.........W W.W",
        "WW.WW.WW.WW-WWW",
        "WW.WW.WW.W===WW",
        "WW.WW.WW.W===WW",
        "WW.WW.WW.WWWWWW",
        "W......K......W",
        "W.WWW.W.W.WWW.W",
        "XoW.........WoW",
        "X.W.WWWWWWW.W.W",
        "X.<<<<<<<<<<<.W",
        "W.WWW.WWW.WWW.W",
        "W.~~~~~~~~~~~.W",
        "WoW.WWW.WWW.WoW",
        "W.W.........W.W",
        "W.W.WWW.WWW.W.W",
        "W......M......W",
        "W.WWW.WWW.WWW.W",
        "W.............W",
        "WWTTTWWWWWWWWWW",
    )

    // Level 26 — Pen BOTTOM-LEFT, portal RIGHT WALL, tunnels at cols 8-10.
    // All four mechanics with heavier density: tide row 1 + lily pads inserted
    // into row 7's open corridor + currents row 9 + crocodile row 20.
    private val LEVEL_26: List<String> = listOf(
        "WWWWWWWWTTTWWWW",
        "W.~~~~~~~~~~~.W",
        "W.WWW.WWW.WWW.W",
        "WoW.........WoW",
        "W.W.WWW.WWW.W.W",
        "W.W...W.W...W.W",
        "W.WWW.W.W.WWW.W",
        "W.LLL.W.W.LLL.W",
        "W.WWW.W.W.WWW.W",
        "W.>>>>>>>>>>>.W",
        "W.WWW.W.W.WWW.W",
        "Wo..WWW.WWW.WoW",
        "W.W W.........W",
        "WWW-WW.WW.WW.WW",
        "WW===W.WW.WW.WW",
        "WW===W.WW.WW.WX",
        "WWWWWW.WW.WW.WX",
        "W.WWW.WWW.WWW.X",
        "W......M......W",
        "W.WWW.WWW.WWW.W",
        "W......K......W",
        "WWWWWWWWTTTWWWW",
    )

    // Level 27 — Pen BOTTOM-RIGHT, portal LEFT WALL, tunnels at cols 1-3.
    // All four mechanics: tide row 1 + lily pads in row 7 corridor +
    // currents row 9 (left to portal side) + crocodile row 20.
    private val LEVEL_27: List<String> = listOf(
        "WTTTWWWWWWWWWWW",
        "W.~~~~~~~~~~~.W",
        "W.WWW.WWW.WWW.W",
        "WoW.........WoW",
        "W.W.WWW.WWW.W.W",
        "W.W...W.W...W.W",
        "W.WWW.WWW.WWW.W",
        "W.LLL.W.W.LLL.W",
        "W.WWW.W.W.WWW.W",
        "W.<<<<<<<<<<<.W",
        "W.WWW.W.W.WWW.W",
        "WoW.WWW.WWW  oW",
        "W.........W W.W",
        "WW.WW.WW.WW-WWW",
        "WW.WW.WW.W===WW",
        "XW.WW.WW.W===WW",
        "XW.WW.WW.WWWWWW",
        "X .WW.WW.WWWW.W",
        "W......M......W",
        "W.WWW.WWW.WWW.W",
        "W......K......W",
        "WTTTWWWWWWWWWWW",
    )

    // Level 28 — Pen centre, portal RIGHT WALL, tunnels at cols 6-8. All four
    // mechanics: tide row 1 + currents row 12 (right to portal) + lily pads
    // row 14 + crocodile row 20.
    private val LEVEL_28: List<String> = listOf(
        "WWWWWWTTTWWWWWW",
        "W.~~~~~~~~~~~.W",
        "W.WWW.WWW.WWW.W",
        "WoW.........WoW",
        "W.W.WWW.WWW.W.W",
        "W.....W.W.....W",
        "W.WWW.....WWW.W",
        "W.....W W.....W",
        "W.WW.WW-WW.WW.W",
        "W.WW.W===W.WW.W",
        "W.WW.W===W.WW.X",
        "W.WW.WWWWW.WW.X",
        "W.>>>>>>>>>>>.X",
        "W.WWWW.W.WWWW.W",
        "W...LLLLLLL...W",
        "W.WWW.WWW.WWW.W",
        "WoW.........WoW",
        "W.W.WWW.WWW.W.W",
        "W......M......W",
        "W.WWW.WWW.WWW.W",
        "W......K......W",
        "WWWWWWTTTWWWWWW",
    )

    // Level 29 — Pen TOP-CENTER, portal LEFT WALL, tunnels at cols 4-6. All
    // four mechanics, denser: lily pads row 1 + currents row 12 (left to
    // portal) + tide row 14 + crocodile row 20.
    private val LEVEL_29: List<String> = listOf(
        "WWWWTTTWWWWWWWW",
        "W...LLLLLLL...W",
        "W.WWW.W.W.WWW.W",
        "W.....W W.....W",
        "W.WW.WW-WW.WW.W",
        "W.WW.W===W.WW.W",
        "W.WW.W===W.WW.W",
        "W.WW.WWWWW.WW.W",
        "W.............W",
        "W.WWW.W.W.WWW.W",
        "XoW.........WoW",
        "X.W.WWWWWWW.W.W",
        "X.<<<<<<<<<<<.W",
        "W.WWW.WWW.WWW.W",
        "W.~~~~~~~~~~~.W",
        "WoW.WWW.WWW.WoW",
        "W.W.........W.W",
        "W.W.WWW.WWW.W.W",
        "W......M......W",
        "W.WWW.WWW.WWW.W",
        "W......K......W",
        "WWWWTTTWWWWWWWW",
    )

    // Level 30 — BOSS. Pen centre, portal LEFT WALL, tunnels at cols 11-13
    // (mirror of L5 baseline). Densest layout: tide row 1 + lily pads inserted
    // into row 5's open corridor + currents row 12 + tide row 14 + crocodile
    // row 20. Two tide rows + lily-pad branches make the final challenge.
    private val LEVEL_30: List<String> = listOf(
        "WWWWWWWWWWWTTTW",
        "W.~~~~~~~~~~~.W",
        "W.W.W.W.W.W.W.W",
        "WoW.........WoW",
        "W.W.WWW.WWW.W.W",
        "WLLLLLWLWLLLLLW",
        "W.WWWWW.WWWWW.W",
        "W.....W W.....W",
        "W.WW.WW-WW.WW.W",
        "W.WW.W===W.WW.W",
        "X.WW.W===W.WW.W",
        "X.WW.WWWWW.WW.W",
        "X.<<<<<<<<<<<.W",
        "W.WWWWW.WWWWW.W",
        "W.~~~~~~~~~~~.W",
        "WoW.WWW.WWW.WoW",
        "W.W.........W.W",
        "W.W.W.W.W.W.W.W",
        "W......M......W",
        "W.WWW.WWW.WWW.W",
        "W......K......W",
        "WWWWWWWWWWWTTTW",
    )

    private val LEVELS: List<List<String>> = listOf(
        LEVEL_1, LEVEL_2, LEVEL_3, LEVEL_4, LEVEL_5,
        LEVEL_6, LEVEL_7, LEVEL_8, LEVEL_9, LEVEL_10,
        LEVEL_11, LEVEL_12, LEVEL_13, LEVEL_14, LEVEL_15,
        LEVEL_16, LEVEL_17, LEVEL_18, LEVEL_19, LEVEL_20,
        LEVEL_21, LEVEL_22, LEVEL_23, LEVEL_24, LEVEL_25,
        LEVEL_26, LEVEL_27, LEVEL_28, LEVEL_29, LEVEL_30,
    )

    val LEVEL_COUNT: Int get() = LEVELS.size

    init {
        LEVELS.forEachIndexed { i, layout ->
            require(layout.size == 22) { "Level ${i + 1} must be 22 rows; got ${layout.size}" }
            val badRows = layout.withIndex().filter { it.value.length != 15 }
            require(badRows.isEmpty()) {
                "Level ${i + 1} rows must be 15 chars wide; offending: ${badRows.joinToString { "${it.index}=${it.value.length}" }}"
            }
            val mCount = layout.sumOf { row -> row.count { it == 'M' } }
            require(mCount == 1) {
                "Level ${i + 1} must have exactly one 'M' (monkey spawn); got $mCount"
            }
        }
    }

    fun layoutForLevel(level: Int): List<String> {
        // Levels beyond LEVEL_COUNT cycle back to Level 1 so the game never
        // runs out of layouts — difficulty keeps climbing via piranha speed.
        val idx = ((level - 1) % LEVELS.size + LEVELS.size) % LEVELS.size
        return LEVELS[idx]
    }

    fun piranhaSpeedScale(level: Int): Float {
        // Level 1 baseline 1.0; each level adds 8% piranha speed up to a 1.6x cap.
        return (1.0f + 0.08f * (level - 1)).coerceAtMost(1.6f)
    }
}
