package com.lionstone.monkeyrapids

import kotlin.math.abs

/**
 * Linear interpolation for a render position between the previous and current
 * simulation tick, given [alpha] in 0..1 (how far the renderer is into the next,
 * not-yet-simulated tick).
 *
 * Large deltas — tunnel wraps and respawns/teleports — are NOT interpolated:
 * we snap to [cur] so the sprite doesn't streak across the whole maze on the
 * frame it jumps. A single tick of normal movement is well under 0.2 tiles, so
 * the 0.6-tile threshold cleanly separates real movement from a teleport.
 */
internal fun renderLerp(prev: Float, cur: Float, alpha: Float): Float {
    val d = cur - prev
    if (abs(d) > 0.6f) return cur
    return prev + d * alpha
}
