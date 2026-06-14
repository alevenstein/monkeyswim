package com.lionstone.monkeyrapids

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/**
 * Procedural fruit sprites used in place of yellow power pellets. Each fruit
 * is centred on (cx, cy) with overall extent roughly ±s; the body is drawn
 * around 2·(0.85·s) wide so passing s ≈ cellSize·0.40 yields a fruit that
 * occupies most of a tile.
 */
object FruitRenderer {

    enum class FruitType { APPLE, ORANGE, GRAPES, STRAWBERRY, PINEAPPLE }

    private val appleBody = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D63A3A"); style = Paint.Style.FILL
    }
    private val appleHi = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF8B8B"); style = Paint.Style.FILL
    }
    private val orangeBody = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF8C2E"); style = Paint.Style.FILL
    }
    private val orangeHi = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFB066"); style = Paint.Style.FILL
    }
    private val orangeDimple = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D26418"); style = Paint.Style.FILL
    }
    private val grapeBody = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7B3F99"); style = Paint.Style.FILL
    }
    private val grapeHi = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#A772BD"); style = Paint.Style.FILL
    }
    private val strawBody = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E63946"); style = Paint.Style.FILL
    }
    private val strawSeed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFE066"); style = Paint.Style.FILL
    }
    private val pineBody = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FDB827"); style = Paint.Style.FILL
    }
    private val pineHi = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFE08A"); style = Paint.Style.FILL
    }
    private val pineDark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#C68A1B"); style = Paint.Style.FILL
    }
    private val stem = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#5D4037"); style = Paint.Style.FILL
    }
    private val leaf = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4CAF50"); style = Paint.Style.FILL
    }
    private val leafDark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2E7D32"); style = Paint.Style.FILL
    }
    private val bananaBody = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFE135"); style = Paint.Style.FILL
    }
    private val bananaBrown = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7B5424"); style = Paint.Style.FILL
    }

    private val tmpRect = RectF()
    private val tmpPath = Path()

    /**
     * Banana sprite used by the powerup spawn. Oriented like the katakana ノ —
     * top tip in the upper-right, bottom tip in the lower-left. The outer
     * (right) edge bows further right than the inner (left) edge so the
     * silhouette tapers to points at both ends.
     */
    fun drawBanana(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        tmpPath.reset()
        tmpPath.moveTo(cx + s * 0.25f, cy - s * 0.85f)
        tmpPath.quadTo(cx + s * 0.95f, cy + s * 0.10f, cx - s * 0.45f, cy + s * 0.85f)
        tmpPath.quadTo(cx + s * 0.05f, cy - s * 0.05f, cx + s * 0.25f, cy - s * 0.85f)
        tmpPath.close()
        canvas.drawPath(tmpPath, bananaBody)
        // Brown stem nub just past the upper-right tip.
        canvas.drawCircle(cx + s * 0.30f, cy - s * 0.95f, s * 0.10f, bananaBrown)
    }

    fun draw(canvas: Canvas, type: FruitType, cx: Float, cy: Float, s: Float) {
        when (type) {
            FruitType.APPLE -> drawApple(canvas, cx, cy, s)
            FruitType.ORANGE -> drawOrange(canvas, cx, cy, s)
            FruitType.GRAPES -> drawGrapes(canvas, cx, cy, s)
            FruitType.STRAWBERRY -> drawStrawberry(canvas, cx, cy, s)
            FruitType.PINEAPPLE -> drawPineapple(canvas, cx, cy, s)
        }
    }

    private fun drawApple(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        canvas.drawCircle(cx, cy + s * 0.05f, s * 0.85f, appleBody)
        canvas.drawCircle(cx - s * 0.30f, cy - s * 0.20f, s * 0.22f, appleHi)
        tmpRect.set(cx - s * 0.06f, cy - s * 0.95f, cx + s * 0.06f, cy - s * 0.65f)
        canvas.drawRect(tmpRect, stem)
        tmpPath.reset()
        tmpPath.moveTo(cx + s * 0.05f, cy - s * 0.85f)
        tmpPath.quadTo(cx + s * 0.50f, cy - s * 1.00f, cx + s * 0.50f, cy - s * 0.65f)
        tmpPath.quadTo(cx + s * 0.25f, cy - s * 0.65f, cx + s * 0.05f, cy - s * 0.85f)
        tmpPath.close()
        canvas.drawPath(tmpPath, leaf)
    }

    private fun drawOrange(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        canvas.drawCircle(cx, cy, s * 0.90f, orangeBody)
        canvas.drawCircle(cx + s * 0.30f, cy + s * 0.10f, s * 0.07f, orangeDimple)
        canvas.drawCircle(cx - s * 0.25f, cy + s * 0.30f, s * 0.06f, orangeDimple)
        canvas.drawCircle(cx + s * 0.20f, cy - s * 0.40f, s * 0.06f, orangeDimple)
        canvas.drawCircle(cx - s * 0.40f, cy - s * 0.10f, s * 0.06f, orangeDimple)
        canvas.drawCircle(cx + s * 0.10f, cy + s * 0.50f, s * 0.05f, orangeDimple)
        canvas.drawCircle(cx - s * 0.30f, cy - s * 0.30f, s * 0.20f, orangeHi)
        tmpRect.set(cx - s * 0.05f, cy - s * 1.00f, cx + s * 0.05f, cy - s * 0.85f)
        canvas.drawRect(tmpRect, stem)
        tmpPath.reset()
        tmpPath.moveTo(cx + s * 0.05f, cy - s * 0.95f)
        tmpPath.quadTo(cx + s * 0.40f, cy - s * 1.05f, cx + s * 0.40f, cy - s * 0.75f)
        tmpPath.quadTo(cx + s * 0.20f, cy - s * 0.75f, cx + s * 0.05f, cy - s * 0.95f)
        tmpPath.close()
        canvas.drawPath(tmpPath, leaf)
    }

    private fun drawGrapes(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val r = s * 0.28f
        canvas.drawCircle(cx - s * 0.45f, cy - s * 0.30f, r, grapeBody)
        canvas.drawCircle(cx, cy - s * 0.40f, r, grapeBody)
        canvas.drawCircle(cx + s * 0.45f, cy - s * 0.30f, r, grapeBody)
        canvas.drawCircle(cx - s * 0.22f, cy + s * 0.05f, r, grapeBody)
        canvas.drawCircle(cx + s * 0.22f, cy + s * 0.05f, r, grapeBody)
        canvas.drawCircle(cx, cy + s * 0.45f, r, grapeBody)
        canvas.drawCircle(cx - s * 0.50f, cy - s * 0.40f, r * 0.30f, grapeHi)
        canvas.drawCircle(cx - s * 0.05f, cy - s * 0.50f, r * 0.30f, grapeHi)
        canvas.drawCircle(cx - s * 0.27f, cy - s * 0.05f, r * 0.30f, grapeHi)
        tmpRect.set(cx - s * 0.05f, cy - s * 0.85f, cx + s * 0.05f, cy - s * 0.55f)
        canvas.drawRect(tmpRect, stem)
        tmpPath.reset()
        tmpPath.moveTo(cx + s * 0.04f, cy - s * 0.75f)
        tmpPath.quadTo(cx + s * 0.40f, cy - s * 0.90f, cx + s * 0.45f, cy - s * 0.55f)
        tmpPath.quadTo(cx + s * 0.20f, cy - s * 0.55f, cx + s * 0.04f, cy - s * 0.75f)
        tmpPath.close()
        canvas.drawPath(tmpPath, leaf)
    }

    private fun drawStrawberry(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        // Heart/teardrop body pointing down
        tmpPath.reset()
        tmpPath.moveTo(cx, cy + s * 0.95f)
        tmpPath.cubicTo(cx + s * 0.95f, cy + s * 0.40f, cx + s * 0.95f, cy - s * 0.30f, cx, cy - s * 0.30f)
        tmpPath.cubicTo(cx - s * 0.95f, cy - s * 0.30f, cx - s * 0.95f, cy + s * 0.40f, cx, cy + s * 0.95f)
        tmpPath.close()
        canvas.drawPath(tmpPath, strawBody)
        // Seeds
        canvas.drawCircle(cx + s * 0.35f, cy + s * 0.05f, s * 0.06f, strawSeed)
        canvas.drawCircle(cx - s * 0.35f, cy + s * 0.05f, s * 0.06f, strawSeed)
        canvas.drawCircle(cx, cy + s * 0.30f, s * 0.06f, strawSeed)
        canvas.drawCircle(cx + s * 0.20f, cy + s * 0.55f, s * 0.06f, strawSeed)
        canvas.drawCircle(cx - s * 0.20f, cy + s * 0.55f, s * 0.06f, strawSeed)
        canvas.drawCircle(cx + s * 0.05f, cy - s * 0.10f, s * 0.06f, strawSeed)
        // Green leafy cap with five points
        tmpPath.reset()
        tmpPath.moveTo(cx, cy - s * 0.05f)
        tmpPath.lineTo(cx - s * 0.85f, cy - s * 0.30f)
        tmpPath.lineTo(cx - s * 0.50f, cy - s * 0.50f)
        tmpPath.lineTo(cx - s * 0.20f, cy - s * 0.70f)
        tmpPath.lineTo(cx, cy - s * 0.40f)
        tmpPath.lineTo(cx + s * 0.20f, cy - s * 0.70f)
        tmpPath.lineTo(cx + s * 0.50f, cy - s * 0.50f)
        tmpPath.lineTo(cx + s * 0.85f, cy - s * 0.30f)
        tmpPath.close()
        canvas.drawPath(tmpPath, leafDark)
    }

    private fun drawPineapple(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        // Body oval (slightly taller than wide)
        tmpRect.set(cx - s * 0.65f, cy - s * 0.30f, cx + s * 0.65f, cy + s * 0.95f)
        canvas.drawOval(tmpRect, pineBody)
        // Crosshatch pattern of small dots — alternating light/dark for diamond texture
        for (i in 0..2) {
            for (j in 0..2) {
                val px = cx - s * 0.32f + i * s * 0.32f
                val py = cy - s * 0.05f + j * s * 0.32f
                canvas.drawCircle(px, py, s * 0.07f, if ((i + j) % 2 == 0) pineHi else pineDark)
            }
        }
        // Spiky crown of leaves
        tmpPath.reset()
        val crownBase = cy - s * 0.30f
        tmpPath.moveTo(cx - s * 0.55f, crownBase)
        tmpPath.lineTo(cx - s * 0.45f, cy - s * 0.85f)
        tmpPath.lineTo(cx - s * 0.20f, crownBase - s * 0.15f)
        tmpPath.lineTo(cx - s * 0.10f, cy - s * 1.00f)
        tmpPath.lineTo(cx + s * 0.10f, crownBase - s * 0.15f)
        tmpPath.lineTo(cx + s * 0.20f, cy - s * 1.00f)
        tmpPath.lineTo(cx + s * 0.45f, crownBase - s * 0.15f)
        tmpPath.lineTo(cx + s * 0.55f, cy - s * 0.85f)
        tmpPath.lineTo(cx + s * 0.55f, crownBase)
        tmpPath.close()
        canvas.drawPath(tmpPath, leafDark)
    }
}
