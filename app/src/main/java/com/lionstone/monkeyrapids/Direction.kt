package com.lionstone.monkeyrapids

enum class Direction(val dx: Int, val dy: Int) {
    NONE(0, 0),
    UP(0, -1),
    DOWN(0, 1),
    LEFT(-1, 0),
    RIGHT(1, 0);

    fun opposite(): Direction = when (this) {
        UP -> DOWN
        DOWN -> UP
        LEFT -> RIGHT
        RIGHT -> LEFT
        NONE -> NONE
    }

    val isHorizontal: Boolean get() = this == LEFT || this == RIGHT
    val isVertical: Boolean get() = this == UP || this == DOWN
}
