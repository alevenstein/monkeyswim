package com.lionstone.monkeyrapids

enum class Tile {
    WALL,
    PATH,
    PELLET,
    POWER_PELLET,
    PEN_DOOR,
    PEN_INTERIOR,
    BOTTOM_GATEWAY,
    TUNNEL,
    MONKEY_SPAWN,  // walkable like PATH; carries no pellet; marks where the monkey spawns
    // Currents — walkable like PATH (no pellet). Entities moving with a
    // current get +50% speed; against it -50%; perpendicular unaffected.
    CURRENT_UP,
    CURRENT_DOWN,
    CURRENT_LEFT,
    CURRENT_RIGHT,
    // Tide tiles — toggle between walkable and wall on a global ~6s cycle.
    // No pellet (avoid unreachable-pellet trap during the wall phase).
    TIDE,
    // Lily pads — walkable like PATH, no pellet. Slippery: an entity that
    // enters keeps moving in its entry direction (no turning, no reverse)
    // until it slides off the lily-pad tile. EATEN piranhas are exempt.
    LILY_PAD,
    // Crocodile spawn marker — walkable like PATH (no pellet); GameState
    // spawns a Crocodile entity at this cell at level start. The crocodile
    // patrols along the axis defined by its walkable neighbours.
    CROCODILE_SPAWN;

    val carriesPellet: Boolean
        get() = this == PELLET || this == POWER_PELLET

    val currentDirection: Direction?
        get() = when (this) {
            CURRENT_UP -> Direction.UP
            CURRENT_DOWN -> Direction.DOWN
            CURRENT_LEFT -> Direction.LEFT
            CURRENT_RIGHT -> Direction.RIGHT
            else -> null
        }

    val char: Char
        get() = when (this) {
            WALL -> 'W'
            PELLET -> '.'
            POWER_PELLET -> 'o'
            PATH -> ' '
            PEN_DOOR -> '-'
            PEN_INTERIOR -> '='
            BOTTOM_GATEWAY -> 'X'
            TUNNEL -> 'T'
            MONKEY_SPAWN -> 'M'
            CURRENT_UP -> '^'
            CURRENT_DOWN -> 'v'
            CURRENT_LEFT -> '<'
            CURRENT_RIGHT -> '>'
            TIDE -> '~'
            LILY_PAD -> 'L'
            CROCODILE_SPAWN -> 'K'
        }

    companion object {
        fun fromChar(c: Char): Tile = when (c) {
            'W' -> WALL
            '.' -> PELLET
            'o' -> POWER_PELLET
            ' ' -> PATH
            '-' -> PEN_DOOR
            '=' -> PEN_INTERIOR
            'X' -> BOTTOM_GATEWAY
            'T' -> TUNNEL
            'M' -> MONKEY_SPAWN
            '^' -> CURRENT_UP
            'v' -> CURRENT_DOWN
            '<' -> CURRENT_LEFT
            '>' -> CURRENT_RIGHT
            '~' -> TIDE
            'L' -> LILY_PAD
            'K' -> CROCODILE_SPAWN
            else -> error("Unknown tile char: '$c'")
        }
    }
}
