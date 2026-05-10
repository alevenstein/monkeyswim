package com.monkeyswim.game

enum class Tile {
    WALL,
    PATH,
    PELLET,
    POWER_PELLET,
    PEN_DOOR,
    PEN_INTERIOR,
    BOTTOM_GATEWAY,
    TUNNEL,
    MONKEY_SPAWN;  // walkable like PATH; carries no pellet; marks where the monkey spawns

    val carriesPellet: Boolean
        get() = this == PELLET || this == POWER_PELLET

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
            else -> error("Unknown tile char: '$c'")
        }
    }
}
