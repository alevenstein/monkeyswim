package com.monkeyswim.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/**
 * Procedural top-down sprites for the monkey and the piranhas. Each sprite is
 * drawn facing RIGHT by default and then rotated by direction.
 */
object SpriteRenderer {

    private val monkeyFur = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8B5A2B"); style = Paint.Style.FILL
    }
    private val monkeyFurLight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#C58A4E"); style = Paint.Style.FILL
    }
    private val monkeyBelly = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F2D2A8"); style = Paint.Style.FILL
    }
    private val eyeWhite = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; style = Paint.Style.FILL
    }
    private val eyeBlack = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK; style = Paint.Style.FILL
    }
    private val piranhaBody = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7E8C99"); style = Paint.Style.FILL
    }
    private val piranhaBelly = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#C44A3D"); style = Paint.Style.FILL
    }
    private val piranhaFin = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3F4853"); style = Paint.Style.FILL
    }
    private val frightBody = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3050C8"); style = Paint.Style.FILL
    }
    private val frightFin = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1B2A6B"); style = Paint.Style.FILL
    }
    private val toothPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; style = Paint.Style.FILL
    }
    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#80FFFFFF"); style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val monkeyOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2A1605"); style = Paint.Style.FILL
    }
    private val piranhaOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1F2630"); style = Paint.Style.FILL
    }
    private val wakePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(170, 230, 245, 255); style = Paint.Style.FILL
    }

    private val tmpRect = RectF()
    private val tmpPath = Path()

    fun drawMonkey(
        canvas: Canvas,
        cx: Float, cy: Float,
        cellSize: Float,
        direction: Direction,
        frame: Int,
    ) {
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(rotationFor(direction))
        val s = cellSize * 0.75f  // half-size
        val armOffset = if (frame % 2 == 0) 1f else -1f

        // Wake foam trail (behind = LEFT in local frame)
        canvas.drawCircle(-s * 1.05f, -s * 0.30f, cellSize * 0.13f, wakePaint)
        canvas.drawCircle(-s * 1.30f,  s * 0.20f, cellSize * 0.10f, wakePaint)
        canvas.drawCircle(-s * 1.55f, -s * 0.10f + cellSize * 0.04f * armOffset, cellSize * 0.07f, wakePaint)

        // Trailing ripples
        val rippleX = -s * 0.9f - cellSize * 0.05f
        canvas.drawCircle(rippleX, -s * 0.4f, cellSize * 0.10f, ripplePaint)
        canvas.drawCircle(rippleX - cellSize * 0.10f, s * 0.4f, cellSize * 0.10f, ripplePaint)

        // Outline halo
        canvas.drawCircle(0f, 0f, s * 1.07f, monkeyOutline)

        // Body (round brown disc)
        canvas.drawCircle(0f, 0f, s, monkeyFur)
        // Belly highlight (oval, slightly forward)
        tmpRect.set(s * 0.05f, -s * 0.55f, s * 0.85f, s * 0.55f)
        canvas.drawOval(tmpRect, monkeyBelly)

        // Ears (top-down: small circles at sides toward back)
        canvas.drawCircle(-s * 0.55f, -s * 0.65f, s * 0.32f, monkeyOutline)
        canvas.drawCircle(-s * 0.55f,  s * 0.65f, s * 0.32f, monkeyOutline)
        canvas.drawCircle(-s * 0.55f, -s * 0.65f, s * 0.28f, monkeyFur)
        canvas.drawCircle(-s * 0.55f,  s * 0.65f, s * 0.28f, monkeyFur)
        canvas.drawCircle(-s * 0.55f, -s * 0.65f, s * 0.16f, monkeyFurLight)
        canvas.drawCircle(-s * 0.55f,  s * 0.65f, s * 0.16f, monkeyFurLight)

        // Eyes (forward) with sparkle
        canvas.drawCircle(s * 0.45f, -s * 0.30f, s * 0.20f, eyeWhite)
        canvas.drawCircle(s * 0.45f,  s * 0.30f, s * 0.20f, eyeWhite)
        canvas.drawCircle(s * 0.55f, -s * 0.30f, s * 0.11f, eyeBlack)
        canvas.drawCircle(s * 0.55f,  s * 0.30f, s * 0.11f, eyeBlack)
        canvas.drawCircle(s * 0.60f, -s * 0.34f, s * 0.05f, eyeWhite)
        canvas.drawCircle(s * 0.60f,  s * 0.26f, s * 0.05f, eyeWhite)

        // Swimming arms — alternate per frame
        canvas.drawCircle(s * 0.30f, -s * 0.95f - cellSize * 0.04f * armOffset, s * 0.20f, monkeyOutline)
        canvas.drawCircle(-s * 0.10f, s * 0.95f + cellSize * 0.04f * armOffset, s * 0.20f, monkeyOutline)
        canvas.drawCircle(s * 0.30f, -s * 0.95f - cellSize * 0.04f * armOffset, s * 0.18f, monkeyFur)
        canvas.drawCircle(-s * 0.10f, s * 0.95f + cellSize * 0.04f * armOffset, s * 0.18f, monkeyFur)
        canvas.drawCircle(s * 0.30f, -s * 0.95f - cellSize * 0.04f * armOffset, s * 0.10f, monkeyFurLight)
        canvas.drawCircle(-s * 0.10f, s * 0.95f + cellSize * 0.04f * armOffset, s * 0.10f, monkeyFurLight)

        canvas.restore()
    }

    fun drawPiranha(
        canvas: Canvas,
        cx: Float, cy: Float,
        cellSize: Float,
        direction: Direction,
        frame: Int,
        frightened: Boolean,
        frightBlink: Boolean,
    ) {
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(rotationFor(direction))
        val s = cellSize * 0.75f

        val body = if (frightened) {
            if (frightBlink) Paint(frightBody).apply { color = Color.parseColor("#E0E0FF") }
            else frightBody
        } else piranhaBody
        val fin = if (frightened) frightFin else piranhaFin

        // Wake foam trail behind tail
        canvas.drawCircle(-s * 1.65f, -s * 0.20f, cellSize * 0.10f, wakePaint)
        canvas.drawCircle(-s * 1.85f,  s * 0.25f, cellSize * 0.07f, wakePaint)

        // Tail fin (behind = LEFT in local frame), animates between two angles
        tmpPath.reset()
        val tailFlick = if (frame % 2 == 0) -0.4f else 0.4f
        tmpPath.moveTo(-s * 1.0f, 0f)
        tmpPath.lineTo(-s * 1.5f, -s * 0.6f + tailFlick * cellSize * 0.10f)
        tmpPath.lineTo(-s * 1.5f,  s * 0.6f - tailFlick * cellSize * 0.10f)
        tmpPath.close()
        canvas.drawPath(tmpPath, fin)

        // Body outline (slightly larger oval behind)
        tmpRect.set(-s * 1.06f, -s * 0.62f, s * 1.06f, s * 0.62f)
        canvas.drawOval(tmpRect, piranhaOutline)

        // Body oval (longer than tall)
        tmpRect.set(-s, -s * 0.55f, s, s * 0.55f)
        canvas.drawOval(tmpRect, body)

        // Belly stripe (red) — only when not frightened
        if (!frightened) {
            tmpRect.set(-s * 0.85f, s * 0.15f, s * 0.85f, s * 0.55f)
            canvas.drawOval(tmpRect, piranhaBelly)
        }

        // Side fins (alternate per frame)
        val sideFinOff = if (frame % 2 == 0) -1f else 1f
        tmpPath.reset()
        tmpPath.moveTo(-s * 0.10f, -s * 0.50f)
        tmpPath.lineTo(-s * 0.40f, -s * 0.85f + cellSize * 0.05f * sideFinOff)
        tmpPath.lineTo( s * 0.20f, -s * 0.55f)
        tmpPath.close()
        canvas.drawPath(tmpPath, fin)
        tmpPath.reset()
        tmpPath.moveTo(-s * 0.10f, s * 0.50f)
        tmpPath.lineTo(-s * 0.40f, s * 0.85f - cellSize * 0.05f * sideFinOff)
        tmpPath.lineTo( s * 0.20f, s * 0.55f)
        tmpPath.close()
        canvas.drawPath(tmpPath, fin)

        // Mouth with teeth (front)
        tmpRect.set(s * 0.55f, -s * 0.25f, s * 1.05f, s * 0.25f)
        canvas.drawOval(tmpRect, body)
        // Teeth (frightened sprite gets a calm closed mouth — no teeth)
        if (!frightened) {
            tmpPath.reset()
            tmpPath.moveTo(s * 0.65f, -s * 0.05f)
            tmpPath.lineTo(s * 0.75f, -s * 0.20f)
            tmpPath.lineTo(s * 0.85f, -s * 0.05f)
            tmpPath.close()
            canvas.drawPath(tmpPath, toothPaint)
            tmpPath.reset()
            tmpPath.moveTo(s * 0.65f, s * 0.05f)
            tmpPath.lineTo(s * 0.75f, s * 0.20f)
            tmpPath.lineTo(s * 0.85f, s * 0.05f)
            tmpPath.close()
            canvas.drawPath(tmpPath, toothPaint)
        }

        // Eye (single, top-down view) with sparkle
        canvas.drawCircle(s * 0.30f, -s * 0.20f, s * 0.18f, eyeWhite)
        canvas.drawCircle(s * 0.36f, -s * 0.20f, s * 0.10f, eyeBlack)
        canvas.drawCircle(s * 0.40f, -s * 0.24f, s * 0.04f, eyeWhite)

        canvas.restore()
    }

    private fun rotationFor(direction: Direction): Float = when (direction) {
        Direction.RIGHT, Direction.NONE -> 0f
        Direction.DOWN -> 90f
        Direction.LEFT -> 180f
        Direction.UP -> 270f
    }
}
