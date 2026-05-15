package com.monkeyswim.game

/**
 * Level catalog. Each level is a 22×15 layout that *must* contain:
 *   • exactly one 'M' (monkey spawn)
 *   • one '-' (pen door) and a contiguous '===' pen interior block
 *   • exactly one 3-cell 'T' tunnel group on the top row and a matching
 *     group on the bottom row (same columns — `tunnelWrap` in Maze.kt
 *     teleports by column without verifying the destination is also a T)
 *   • exactly one wall of 'X' level-portal cells
 *   • four 'o' power pellets
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
 *   D = deep / dive tile (monkey may enter only while breath > 0; piranhas
 *       treat as wall; no pellet)
 *   ~ = tide cell (walkable during the high-tide phase of the global
 *       ~6s cycle, wall during the low phase; no pellet)
 *
 * Level 1 is the hand-laid baseline (pen + portal + tunnels all centered).
 * Levels 2-10 each carry their own pen position, portal wall, and tunnel
 * column triple so the gameplay feel changes per level — piranhas emerge
 * from a different direction, the exit is in a different place, and the
 * wrap tunnels are not always centered. Levels 11-20 layer in new
 * mechanics: currents introduced at L10, expanded through L11-L14; the
 * tide cycle introduced at L15; dive tiles introduced at L17; level 20
 * combines all three. The monkey spawn stays at (col 7, row 18) across
 * every level so the player always starts in the same orientation; what
 * varies is the pen position, the exit wall, the tunnel set, and which
 * mechanics are in play. Piranha AI + pen-exit logic are fully driven
 * from the layout chars (`maze.penExitTile`, `maze.piranhaSpawnTiles`),
 * so moving the pen or adding mechanics needs no AI changes. Difficulty
 * also ramps via piranha speed (Levels.piranhaSpeedScale).
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
    // 4-6 (left-of-centre). Piranhas now spawn near the top and have to chase
    // the monkey downward; the exit is on the opposite wall from L1.
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
    // cols 7-9 (just right of centre). With the pen just two rows above the
    // monkey spawn, early-game pressure is high — piranhas reach the player
    // fast.
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

    // Level 4 — Pen LEFT-CENTER (rows 8-11, cols 1-5), portal RIGHT WALL,
    // tunnels RIGHT-ONLY (cols 10-12). Pen abuts the left bank, so piranhas
    // emerge facing right and the chase plays out horizontally.
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

    // Level 5 — Pen RIGHT-CENTER (rows 8-11, cols 9-13), portal LEFT WALL,
    // tunnels LEFT-ONLY (cols 2-4). Mirror of L4.
    private val LEVEL_5: List<String> = listOf(
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

    // Level 6 — Pen TOP-LEFT (rows 4-7, cols 1-5), portal RIGHT WALL, tunnels
    // RIGHT-ONLY (cols 10-12). Pen and portal are on opposite corners; long
    // diagonal chase to the exit.
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

    // Level 7 — Pen TOP-RIGHT (rows 4-7, cols 9-13), portal LEFT WALL,
    // tunnels LEFT-ONLY (cols 2-4). Mirror of L6.
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

    // Level 8 — Pen BOTTOM-LEFT (rows 13-16, cols 1-5), portal RIGHT WALL,
    // tunnels at cols 8-10 (right side, same direction as the portal). Pen
    // and monkey share the lower half of the maze, so the player is herded
    // toward the upper area early. Rows 8, 11, and 17 are deliberately broken
    // up so adjacent rows never both turn into wide horizontal corridors.
    // Row 11 cols 2-3 are kept open so the piranha leaving the vestibule at
    // (3,12) can swim left past the PP at (1,11) and escape upward via col 1
    // — mirror of the same trick in L9.
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
        "W.............W",
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
        "W.............W",
        "WWWWWWWWTTTWWWW",
    )

    // Level 9 — Pen BOTTOM-RIGHT (rows 13-16, cols 9-13), portal LEFT WALL,
    // tunnels at cols 1-3 (far left, opposite of the pen). Mirror of L8 with
    // the portal on the opposite wall. Rows 8, 11, 17 are deliberately broken up so adjacent
    // rows never both turn into wide horizontal corridors (no 2x2 path
    // blocks). Row 11 cols 11-12 are kept open so piranhas leaving the
    // vestibule at (11,12) can swim right past the PP at (13,11) and escape
    // upward — without the gap, every neighbor of (11,11) is a wall.
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
        "W.............W",
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

    // Level 10 — Currents introduced: row 12 is a leftward current that boosts
    // monkey speed toward the portal (col 0 left wall) once pellets are
    // cleared. Pen centre, tunnels cols 11-13 (far right, opposite the
    // portal). Currents have no pellets, so this row contributes 2 pellets
    // (cols 1 and 13) instead of the usual 13 — a small trade for the new
    // mechanic introduction.
    private val LEVEL_10: List<String> = listOf(
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

    // Level 11 — Currents (L1 skeleton). Row 12 (portal access corridor) is a
    // right-flowing current that boosts the monkey toward the right-wall
    // portal once pellets are cleared.
    private val LEVEL_11: List<String> = listOf(
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
        "W.............W",
        "W.WWW.WWW.WWW.W",
        "WoW.........WoW",
        "W.W.WWW.WWW.W.W",
        "W......M......W",
        "W.WWW.WWW.WWW.W",
        "W.............W",
        "WWWWWWTTTWWWWWW",
    )

    // Level 12 — Currents (L2 skeleton, pen top-centre). Row 12 left-flowing
    // current toward the left-wall portal.
    private val LEVEL_12: List<String> = listOf(
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
        "X.<<<<<<<<<<<.W",
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

    // Level 13 — Currents (L4 skeleton, pen left-centre). Two opposing
    // horizontal currents: row 1 flows right (away from portal at right wall,
    // pushing the monkey BACK toward the pen), row 12 flows right (toward
    // portal). The player must choose which lane to use.
    private val LEVEL_13: List<String> = listOf(
        "WWWWWWWWWWTTTWW",
        "W>>>>>>>>>>>>>W",
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
        "W.............W",
        "WoW.WWW.WWW.WoW",
        "W.W.........W.W",
        "W.W.WWW.WWW.W.W",
        "W......M......W",
        "W.WWW.WWW.WWW.W",
        "W.............W",
        "WWWWWWWWWWTTTWW",
    )

    // Level 14 — Currents (L5 skeleton, pen right-centre). Vertical current
    // column at col 1 flowing DOWN through rows 1-7, pushing the monkey
    // toward the bottom of the maze; row 12 left-current ferries the monkey
    // to the portal once pellets are cleared. The top-half PPs that would
    // normally sit at (1, 3) are displaced inward to (3, 3) since col 1 is
    // now a current; bottom-half PPs stay at the corners (1, 15) and (13, 15)
    // since the current doesn't reach that low. Row 1 is the full corridor
    // and row 2 has the alternating-wall pattern (same ordering as L5) — the
    // earlier version swapped them, which produced a 2×9 walkable block
    // between rows 2 and 3.
    private val LEVEL_14: List<String> = listOf(
        "WWTTTWWWWWWWWWW",
        "Wv............W",
        "WvWWW.WWW.WWW.W",
        "WvWoW.......WoW",
        "WvW.WWW.WWW.W.W",
        "WvW...W.W...W.W",
        "WvWWW.WWW.W.W.W",
        "Wv........W W.W",
        "WW.WW.WW.WW-WWW",
        "WW.WW.WW.W===WW",
        "XW.WW.WW.W===WW",
        "XW.WW.WW.WWWWWW",
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

    // Level 15 — TIDE introduced (L6 skeleton, pen top-left). A small ring of
    // tide tiles around the pen exit creates a periodic chokepoint — when
    // the tide drops, piranhas can be temporarily walled in. Currents in
    // row 12 toward portal continue the previous mechanic.
    private val LEVEL_15: List<String> = listOf(
        "WWWWWWWWWWTTTWW",
        "W.............W",
        "W.W W.WWW.WWW.W",
        "W.W W.........W",
        "WWW-WW.WW.WW.WW",
        "WW===W.WW.WW.WW",
        "WW===W.WW.WW.WW",
        "WWWWWW.WW.WW.WW",
        "W.~~~~~~~~~~~.W",
        "W.WWW.W.W.WWW.W",
        "WoW.........WoX",
        "W.W.WWWWWWW.W.X",
        "W.>>>>>>>>>>>.X",
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

    // Level 16 — TIDE expanded (L7 skeleton, pen top-right). Tide passage on
    // row 8 creates a periodic walkable shortcut between the upper and lower
    // halves of the maze.
    private val LEVEL_16: List<String> = listOf(
        "WWTTTWWWWWWWWWW",
        "W.............W",
        "W.WWW.WWW.W W.W",
        "W.........W W.W",
        "WW.WW.WW.WW-WWW",
        "WW.WW.WW.W===WW",
        "WW.WW.WW.W===WW",
        "WW.WW.WW.WWWWWW",
        "W.~~~~~~~~~~~.W",
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

    // Level 17 — DIVE introduced (L8 skeleton, pen bottom-left). Small dive
    // shortcut on row 1 lets the monkey skip across the top while underwater
    // (piranhas can't follow). Watch the breath bar.
    private val LEVEL_17: List<String> = listOf(
        "WWWWWWWWTTTWWWW",
        "W.DDDDDDDDDDD.W",
        "W.WWW.WWW.WWW.W",
        "WoW.........WoW",
        "W.W.WWW.WWW.W.W",
        "W.W...W.W...W.W",
        "W.WWW.W.W.WWW.W",
        "W.....W.W.....W",
        "W.WWW.W.W.WWW.W",
        "W.............W",
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
        "W.............W",
        "WWWWWWWWTTTWWWW",
    )

    // Level 18 — DIVE + currents (L9 skeleton, pen bottom-right). Vertical
    // dive column on col 7 and a current on row 12 (toward portal). The
    // monkey can dive the dive column to bypass piranhas in the centre.
    private val LEVEL_18: List<String> = listOf(
        "WTTTWWWWWWWWWWW",
        "W.............W",
        "W.WWW.WWW.WWW.W",
        "WoW.........WoW",
        "W.W.WWW.WWW.W.W",
        "W.W.W.D.W.W.W.W",
        "W.WWW.D.W.WWW.W",
        "W.....D.W.....W",
        "W.WWW.D.W.WWW.W",
        "W.....D.......W",
        "W.WWW.D.W.WWW.W",
        "WoW...D.....WoW",
        "W.........W W.W",
        "WW.WW.WW.WW-WWW",
        "WW.WW.WW.W===WW",
        "XW.WW.WW.W===WW",
        "XW.WW.WW.WWWWWW",
        "X.<<<<<<<<<<<.W",
        "W......M......W",
        "W.WWW.WWW.WWW.W",
        "W.............W",
        "WTTTWWWWWWWWWWW",
    )

    // Level 19 — TIDE + DIVE (L3 skeleton, pen bottom-centre). Tide cells form
    // a ring around the monkey spawn; dive tiles open a vertical shortcut up
    // through the middle.
    private val LEVEL_19: List<String> = listOf(
        "WWWWWWWTTTWWWWW",
        "W.............W",
        "W.WWW.W.W.WWW.W",
        "W.W.........W.W",
        "W.W.WWW.WWW.W.W",
        "WoW...D.D...WoW",
        "W.WWW.D.D.WWW.W",
        "W.....D.D.....W",
        "W.WWW.D.D.WWW.W",
        "W.....D.D.....W",
        "W.WWW.W.W.WWW.X",
        "WoWWWWW WWWWWoX",
        "W.....W W.....X",
        "W.WW.WW-WW.WW.W",
        "W.WW.W===W.WW.W",
        "W.WW.W===W.WW.W",
        "W.WW.WWWWW.WW.W",
        "W.~~~~~~~~~~~.W",
        "W......M......W",
        "W.WWW.WWW.WWW.W",
        "W.............W",
        "WWWWWWWTTTWWWWW",
    )

    // Level 20 — Everything combined (boss): pen centre, portal left wall,
    // tunnels at cols 11-13 (mirror of L10). Currents in row 12, a dive
    // column near the right, and a tide bar near the top.
    private val LEVEL_20: List<String> = listOf(
        "WWWWWWWWWWWTTTW",
        "W.~~~~~~~~~~~.W",
        "W.W.W.W.W.W.W.W",
        "WoW.....D...WoW",
        "W.W.WWW.D.W.W.W",
        "W.W...W.D...W.W",
        "W.WWWWW.D.WWW.W",
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

    private val LEVELS: List<List<String>> = listOf(
        LEVEL_1, LEVEL_2, LEVEL_3, LEVEL_4, LEVEL_5,
        LEVEL_6, LEVEL_7, LEVEL_8, LEVEL_9, LEVEL_10,
        LEVEL_11, LEVEL_12, LEVEL_13, LEVEL_14, LEVEL_15,
        LEVEL_16, LEVEL_17, LEVEL_18, LEVEL_19, LEVEL_20,
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
