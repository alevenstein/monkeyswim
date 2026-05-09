package com.monkeyswim.game

enum class Tile {
    WALL,
    PATH,
    PELLET,
    POWER_PELLET,
    PEN_DOOR,
    PEN_INTERIOR,
    BOTTOM_GATEWAY,
    TUNNEL;

    val carriesPellet: Boolean
        get() = this == PELLET || this == POWER_PELLET

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
            else -> error("Unknown tile char: '$c'")
        }
    }
}
