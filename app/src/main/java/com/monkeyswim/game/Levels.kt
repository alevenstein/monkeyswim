package com.monkeyswim.game

object Levels {
    // 15 columns x 17 rows. Larger cells -> larger sprites and wider visual lanes.
    // W = wall (canal bank)
    // . = pellet (banana coin)
    // o = power pellet (golden banana)
    //   = empty water (no pellet)
    // - = piranha pen door
    // = = piranha pen interior
    // T = tunnel mouth (wraps to opposite side)
    // X = bottom gateway (locked until last pellet eaten)
    private val LEVEL_1: List<String> = listOf(
        "WWWWWWWWWWWWWWW", //  0
        "W......W......W", //  1
        "WoWWWW.W.WWWWoW", //  2
        "W......W......W", //  3
        "W.WW.W.W.W.WW.W", //  4
        "W..W.......W..W", //  5
        "WW.W.WW WW.W.WW", //  6
        "WW.W.W---W.W.WW", //  7
        "T....W===W....T", //  8 (tunnel row + pen interior)
        "WW.W.W===W.W.WW", //  9
        "WW.W.WWWWW.W.WW", // 10
        "W..W.......W..W", // 11
        "W.WW.W.W.W.WW.W", // 12
        "W......W......W", // 13
        "WoWWWW...WWWWoW", // 14
        "W.....   .....W", // 15 (monkey spawn row, no pellets at center)
        "WWWWWWXXXWWWWWW", // 16
    )

    init {
        require(LEVEL_1.all { it.length == 15 }) {
            val bad = LEVEL_1.withIndex().filter { it.value.length != 15 }
                .joinToString { "${it.index}=${it.value.length}" }
            "All maze rows must be 15 chars wide; offending rows: $bad"
        }
    }

    fun layoutForLevel(level: Int): List<String> {
        // For now every level reuses Level 1's layout. Difficulty ramps via piranha speed.
        return LEVEL_1
    }

    fun piranhaSpeedScale(level: Int): Float {
        // Level 1 baseline 1.0; each level adds 8% piranha speed up to a 1.6x cap.
        return (1.0f + 0.08f * (level - 1)).coerceAtMost(1.6f)
    }

    // Spawn coords (col, row).
    val MONKEY_SPAWN: Pair<Int, Int> = 7 to 15
    val PIRANHA_SPAWNS: List<Pair<Int, Int>> = listOf(
        6 to 8,
        8 to 8,
        6 to 9,
        8 to 9,
    )
}
