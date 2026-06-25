package com.lionstone.monkeyrapids

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
    private val monkeyFurDark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6E441F"); style = Paint.Style.FILL
    }
    private val monkeyFurHi = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#A56A33"); style = Paint.Style.FILL
    }
    private val monkeyTail = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8B5A2B"); style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val monkeyFaceShade = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E0BC8F"); style = Paint.Style.FILL
    }
    private val monkeyFace = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F2D2A8"); style = Paint.Style.FILL
    }
    private val monkeyBrow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#5A3A1C"); style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
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
    private val piranhaBack = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#5A6573"); style = Paint.Style.FILL
    }
    private val piranhaBelly = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D9543F"); style = Paint.Style.FILL
    }
    private val piranhaFin = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3F4853"); style = Paint.Style.FILL
    }
    private val piranhaMouth = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4A1010"); style = Paint.Style.FILL
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
        color = Color.parseColor("#1F2630"); style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val wakePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(170, 230, 245, 255); style = Paint.Style.FILL
    }
    // Bait fish — small silver/yellow lure
    private val baitBody = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD24A"); style = Paint.Style.FILL
    }
    private val baitBelly = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFF0B0"); style = Paint.Style.FILL
    }
    private val baitOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7A5A10"); style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    // Shark
    private val sharkBody = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#5A6B7A"); style = Paint.Style.FILL
    }
    private val sharkBelly = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#C8D5DD"); style = Paint.Style.FILL
    }
    private val sharkFin = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3F4C58"); style = Paint.Style.FILL
    }
    // Crocodile
    private val crocBody = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3F6E3B"); style = Paint.Style.FILL
    }
    private val crocBelly = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#A9C285"); style = Paint.Style.FILL
    }
    private val crocBack = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2D5A2A"); style = Paint.Style.FILL
    }
    private val crocTooth = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; style = Paint.Style.FILL
    }
    // Turtle (matches the side-profile turtle in Brick Basher's white powerup)
    private val turtleLegFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(102, 187, 106); style = Paint.Style.FILL
    }
    private val turtleLegStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(30, 80, 30); style = Paint.Style.STROKE; strokeWidth = 3f
    }
    private val turtleShellFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(56, 142, 60); style = Paint.Style.FILL
    }
    private val turtleShellStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(20, 70, 20); style = Paint.Style.STROKE; strokeWidth = 5f
    }
    private val turtleHexStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(25, 75, 25); style = Paint.Style.STROKE; strokeWidth = 3f
    }
    private val turtleEye = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK; style = Paint.Style.FILL
    }
    // Black hole
    private val bhRingOuter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#5A2D82"); style = Paint.Style.FILL
    }
    private val bhRingMid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2D1244"); style = Paint.Style.FILL
    }
    private val bhCore = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK; style = Paint.Style.FILL
    }
    private val bhSparkle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; style = Paint.Style.FILL
    }
    // Lightning flash
    private val lightningPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; style = Paint.Style.FILL
    }
    // Heart (extra-life powerup) — colors mirror Brick Basher's pink-powerup heart.
    private val heartFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val heartStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 5f
    }

    // Hooked-worm (bait powerup icon). Metal hook is a rounded silver stroke;
    // the worm is a fat rounded reddish-pink stroke with darker segment ticks.
    private val hookMetal = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val wormBody = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val wormSegment = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
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
        // Mirror horizontally for LEFT rather than rotating 180°, which would also
        // flip the sprite upside down (see drawPiranha).
        if (direction == Direction.LEFT) {
            canvas.scale(-1f, 1f)
        } else {
            canvas.rotate(rotationFor(direction))
        }
        val s = cellSize * 0.75f  // half-size
        val armOffset = if (frame % 2 == 0) 1f else -1f

        if (direction != Direction.NONE) {
            // Wake foam trail (behind = LEFT in local frame)
            canvas.drawCircle(-s * 1.05f, -s * 0.30f, cellSize * 0.13f, wakePaint)
            canvas.drawCircle(-s * 1.30f,  s * 0.20f, cellSize * 0.10f, wakePaint)
            canvas.drawCircle(-s * 1.55f, -s * 0.10f + cellSize * 0.04f * armOffset, cellSize * 0.07f, wakePaint)

            // Trailing ripples
            val rippleX = -s * 0.9f - cellSize * 0.05f
            canvas.drawCircle(rippleX, -s * 0.4f, cellSize * 0.10f, ripplePaint)
            canvas.drawCircle(rippleX - cellSize * 0.10f, s * 0.4f, cellSize * 0.10f, ripplePaint)
        }

        // Curled tail trailing behind, wagging gently with the swim frame. Drawn
        // first so the body overlaps its root. A darker rim is stroked under the
        // fur-coloured stroke for a bit of definition.
        val tailWag = cellSize * 0.05f * armOffset
        tmpPath.reset()
        tmpPath.moveTo(-s * 0.85f, s * 0.10f)
        tmpPath.quadTo(-s * 1.55f, s * 0.05f + tailWag, -s * 1.50f, -s * 0.55f + tailWag)
        tmpPath.quadTo(-s * 1.46f, -s * 0.95f + tailWag, -s * 1.12f, -s * 0.92f + tailWag)
        monkeyTail.color = Color.parseColor("#3F2A12"); monkeyTail.strokeWidth = s * 0.26f
        canvas.drawPath(tmpPath, monkeyTail)
        monkeyTail.color = Color.parseColor("#8B5A2B"); monkeyTail.strokeWidth = s * 0.18f
        canvas.drawPath(tmpPath, monkeyTail)

        // Ears (drawn first; body covers their inner edge). Set at the temples,
        // small and on the sides — not Mickey-Mouse circles at the back.
        canvas.drawCircle(s * 0.15f, -s * 0.92f, s * 0.27f, monkeyOutline)
        canvas.drawCircle(s * 0.15f,  s * 0.92f, s * 0.27f, monkeyOutline)
        canvas.drawCircle(s * 0.15f, -s * 0.92f, s * 0.21f, monkeyFur)
        canvas.drawCircle(s * 0.15f,  s * 0.92f, s * 0.21f, monkeyFur)
        canvas.drawCircle(s * 0.15f, -s * 0.92f, s * 0.10f, monkeyFace)
        canvas.drawCircle(s * 0.15f,  s * 0.92f, s * 0.10f, monkeyFace)

        // Body outline — slightly elongated forward (primate proportions, not a disc)
        tmpRect.set(-s * 1.02f, -s * 0.92f, s * 1.05f, s * 0.92f)
        canvas.drawOval(tmpRect, monkeyOutline)

        // Body fill
        tmpRect.set(-s * 0.95f, -s * 0.85f, s, s * 0.85f)
        canvas.drawOval(tmpRect, monkeyFur)

        // Volumetric fur shading, clipped to the body: a darker belly band along
        // the underside (+y) and a lighter highlight sweep along the back (-y).
        tmpPath.reset()
        tmpPath.addOval(tmpRect, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(tmpPath)
        tmpRect.set(-s * 0.95f, s * 0.18f, s, s * 1.05f)
        canvas.drawOval(tmpRect, monkeyFurDark)
        tmpRect.set(-s * 0.80f, -s * 1.00f, s * 0.70f, -s * 0.18f)
        canvas.drawOval(tmpRect, monkeyFurHi)
        canvas.restore()

        // Face mask — pale-tan peanut/figure-8 covering the front (the signature
        // monkey marking). A slightly larger, faintly darker tan sits behind it
        // first so the mask has a soft contoured edge rather than a flat cutout.
        canvas.drawCircle(s * 0.30f, -s * 0.32f, s * 0.44f, monkeyFaceShade)
        canvas.drawCircle(s * 0.30f,  s * 0.32f, s * 0.44f, monkeyFaceShade)
        tmpRect.set(s * 0.06f, -s * 0.44f, s * 0.88f, s * 0.44f)
        canvas.drawOval(tmpRect, monkeyFaceShade)
        canvas.drawCircle(s * 0.32f, -s * 0.30f, s * 0.40f, monkeyFace)
        canvas.drawCircle(s * 0.32f,  s * 0.30f, s * 0.40f, monkeyFace)
        tmpRect.set(s * 0.10f, -s * 0.40f, s * 0.85f, s * 0.40f)
        canvas.drawOval(tmpRect, monkeyFace)

        // Muzzle — rounded snout poking forward, same pale tone so it reads as one face
        tmpRect.set(s * 0.72f, -s * 0.28f, s * 1.05f, s * 0.28f)
        canvas.drawOval(tmpRect, monkeyFace)
        // Nostrils
        canvas.drawCircle(s * 0.93f, -s * 0.08f, s * 0.045f, monkeyOutline)
        canvas.drawCircle(s * 0.93f,  s * 0.08f, s * 0.045f, monkeyOutline)

        // Brow ridge — short dark arcs above each eye, primate signature
        monkeyBrow.strokeWidth = s * 0.06f
        tmpRect.set(s * 0.28f, -s * 0.46f, s * 0.58f, -s * 0.04f)
        canvas.drawArc(tmpRect, 200f, 100f, false, monkeyBrow)
        tmpRect.set(s * 0.28f,  s * 0.04f, s * 0.58f,  s * 0.46f)
        canvas.drawArc(tmpRect, 60f, 100f, false, monkeyBrow)

        // Eyes — closer together than the bear version, sitting on the face mask.
        // A thin dark rim gives them more definition than a bare white disc.
        monkeyBrow.strokeWidth = s * 0.03f
        canvas.drawCircle(s * 0.42f, -s * 0.22f, s * 0.16f, eyeWhite)
        canvas.drawCircle(s * 0.42f,  s * 0.22f, s * 0.16f, eyeWhite)
        canvas.drawCircle(s * 0.42f, -s * 0.22f, s * 0.16f, monkeyBrow)
        canvas.drawCircle(s * 0.42f,  s * 0.22f, s * 0.16f, monkeyBrow)
        canvas.drawCircle(s * 0.48f, -s * 0.22f, s * 0.10f, eyeBlack)
        canvas.drawCircle(s * 0.48f,  s * 0.22f, s * 0.10f, eyeBlack)
        canvas.drawCircle(s * 0.52f, -s * 0.25f, s * 0.04f, eyeWhite)
        canvas.drawCircle(s * 0.52f,  s * 0.19f, s * 0.04f, eyeWhite)

        // Swimming arms with pale palms (primates have palms!). Three short
        // finger creases on each palm sell the "hand" at higher resolutions.
        val ax1 = s * 0.30f; val ay1 = -s * 0.95f - cellSize * 0.04f * armOffset
        val ax2 = -s * 0.10f; val ay2 = s * 0.95f + cellSize * 0.04f * armOffset
        canvas.drawCircle(ax1, ay1, s * 0.20f, monkeyOutline)
        canvas.drawCircle(ax2, ay2, s * 0.20f, monkeyOutline)
        canvas.drawCircle(ax1, ay1, s * 0.18f, monkeyFur)
        canvas.drawCircle(ax2, ay2, s * 0.18f, monkeyFur)
        canvas.drawCircle(ax1, ay1, s * 0.11f, monkeyFace)
        canvas.drawCircle(ax2, ay2, s * 0.11f, monkeyFace)
        monkeyBrow.strokeWidth = s * 0.025f
        for (i in -1..1) {
            val fx = i * s * 0.07f
            canvas.drawLine(ax1 + fx, ay1 - s * 0.10f, ax1 + fx, ay1 + s * 0.02f, monkeyBrow)
            canvas.drawLine(ax2 + fx, ay2 - s * 0.10f, ax2 + fx, ay2 + s * 0.02f, monkeyBrow)
        }

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
        bodyColor: Int = 0xFFC0392B.toInt(),
    ) {
        canvas.save()
        canvas.translate(cx, cy)
        // The base sprite points right with its red belly down. For LEFT, mirror
        // horizontally rather than rotating 180° — a half-turn would also flip the
        // belly up, leaving the fish swimming upside down.
        if (direction == Direction.LEFT) {
            canvas.scale(-1f, 1f)
        } else {
            canvas.rotate(rotationFor(direction))
        }
        val s = cellSize * 0.75f

        // Resolve the palette. A fright overrides any per-fish tint (they all
        // turn the same scared blue); otherwise the fish wears its assigned
        // colour with a darker back-shade and an even darker fin derived from it.
        val bodyCol = when {
            frightened && frightBlink -> Color.parseColor("#E0E0FF")
            frightened -> Color.parseColor("#3050C8")
            else -> bodyColor
        }
        piranhaBody.color = bodyCol
        piranhaFin.color = if (frightened) Color.parseColor("#1B2A6B") else darken(bodyColor, 0.58f)
        piranhaBack.color = if (frightened) Color.parseColor("#22368A") else darken(bodyColor, 0.78f)

        // Wake foam trail behind the tail.
        canvas.drawCircle(-s * 1.70f, -s * 0.20f, cellSize * 0.10f, wakePaint)
        canvas.drawCircle(-s * 1.90f,  s * 0.25f, cellSize * 0.07f, wakePaint)

        // Forked caudal (tail) fin — two prongs with a central notch read far
        // more "fish" than the old single triangle. Flicks with the frame.
        val tailFlick = (if (frame % 2 == 0) -1f else 1f) * cellSize * 0.07f
        tmpPath.reset()
        tmpPath.moveTo(-s * 0.92f, 0f)
        tmpPath.lineTo(-s * 1.62f, -s * 0.62f + tailFlick)
        tmpPath.lineTo(-s * 1.30f, 0f)
        tmpPath.lineTo(-s * 1.62f,  s * 0.62f - tailFlick)
        tmpPath.close()
        canvas.drawPath(tmpPath, piranhaFin)

        // Dorsal fin — a triangular sail along the back (dorsal = -y, the side
        // opposite the red belly), set just behind the head hump.
        tmpPath.reset()
        tmpPath.moveTo(-s * 0.20f, -s * 0.55f)
        tmpPath.lineTo( s * 0.05f, -s * 0.98f)
        tmpPath.lineTo( s * 0.34f, -s * 0.52f)
        tmpPath.close()
        canvas.drawPath(tmpPath, piranhaFin)

        // Body — a humped, deep-bellied profile tapering to a blunt jaw, the
        // classic piranha silhouette. A path (not an ellipse) gives the steep
        // forehead and jutting lower jaw that say "piranha" rather than "fish".
        buildPiranhaBody(s)
        canvas.drawPath(tmpPath, piranhaBody)

        // Volume + markings, clipped to the body so nothing spills past the edge:
        // a darker band along the back and (unless frightened) the red belly.
        canvas.save()
        canvas.clipPath(tmpPath)
        tmpRect.set(-s * 1.05f, -s * 0.75f, s * 1.2f, -s * 0.02f)
        canvas.drawOval(tmpRect, piranhaBack)
        if (!frightened) {
            tmpRect.set(-s * 0.85f, s * 0.12f, s * 0.95f, s * 0.75f)
            canvas.drawOval(tmpRect, piranhaBelly)
        }
        canvas.restore()

        // Outline stroke around the silhouette for a crisp edge (buildPiranhaBody
        // left the body path in tmpPath; clipPath/drawOval above don't modify it).
        piranhaOutline.strokeWidth = s * 0.07f
        canvas.drawPath(tmpPath, piranhaOutline)

        // Pectoral fins flaring from the sides, alternating per frame.
        val sideFinOff = if (frame % 2 == 0) -1f else 1f
        tmpPath.reset()
        tmpPath.moveTo(s * 0.05f, -s * 0.46f)
        tmpPath.lineTo(-s * 0.32f, -s * 0.82f + cellSize * 0.05f * sideFinOff)
        tmpPath.lineTo( s * 0.30f, -s * 0.48f)
        tmpPath.close()
        canvas.drawPath(tmpPath, piranhaFin)
        tmpPath.reset()
        tmpPath.moveTo(s * 0.05f, s * 0.46f)
        tmpPath.lineTo(-s * 0.32f, s * 0.82f - cellSize * 0.05f * sideFinOff)
        tmpPath.lineTo( s * 0.30f, s * 0.48f)
        tmpPath.close()
        canvas.drawPath(tmpPath, piranhaFin)

        // Gill slit — a short curved line behind the head.
        tmpPath.reset()
        tmpPath.moveTo(s * 0.40f, -s * 0.42f)
        tmpPath.quadTo(s * 0.30f, 0f, s * 0.40f, s * 0.42f)
        canvas.drawPath(tmpPath, piranhaOutline)

        // Jaw + interlocking teeth (frightened sprite keeps a calm closed mouth).
        if (!frightened) {
            // Dark mouth gape at the snout.
            tmpRect.set(s * 0.62f, -s * 0.14f, s * 1.16f, s * 0.22f)
            canvas.drawOval(tmpRect, piranhaMouth)
            // A saw-tooth row across the jaw line: upper teeth point down, lower
            // teeth point up, interlocking like a piranha's fearsome grin.
            val tx0 = s * 0.64f
            val tx1 = s * 1.12f
            val n = 5
            val tw = (tx1 - tx0) / n
            tmpPath.reset()
            for (i in 0 until n) {
                val xa = tx0 + i * tw
                tmpPath.moveTo(xa, -s * 0.05f)
                tmpPath.lineTo(xa + tw * 0.5f, s * 0.05f)
                tmpPath.lineTo(xa + tw, -s * 0.05f)
                tmpPath.close()
            }
            for (i in 0 until n) {
                val xa = tx0 + tw * 0.5f + i * tw
                tmpPath.moveTo(xa, s * 0.13f)
                tmpPath.lineTo(xa + tw * 0.5f, s * 0.03f)
                tmpPath.lineTo(xa + tw, s * 0.13f)
                tmpPath.close()
            }
            canvas.drawPath(tmpPath, toothPaint)
        }

        // Eye with a small angry brow and a sparkle.
        canvas.drawCircle(s * 0.40f, -s * 0.24f, s * 0.17f, eyeWhite)
        canvas.drawCircle(s * 0.46f, -s * 0.24f, s * 0.095f, eyeBlack)
        canvas.drawCircle(s * 0.50f, -s * 0.28f, s * 0.04f, eyeWhite)
        piranhaOutline.strokeWidth = s * 0.06f
        canvas.drawLine(s * 0.24f, -s * 0.46f, s * 0.56f, -s * 0.30f, piranhaOutline)

        canvas.restore()
    }

    /** Builds the piranha body silhouette into [tmpPath], facing RIGHT (+x):
     *  a humped back, deep belly, and a blunt jutting jaw at the snout. */
    private fun buildPiranhaBody(s: Float) {
        tmpPath.reset()
        tmpPath.moveTo(-s * 0.95f, -s * 0.26f)               // tail root, upper
        tmpPath.cubicTo(-s * 0.35f, -s * 0.72f, s * 0.28f, -s * 0.66f, s * 0.60f, -s * 0.44f) // humped back
        tmpPath.quadTo(s * 0.96f, -s * 0.28f, s * 1.14f, -s * 0.02f) // steep forehead to snout
        tmpPath.lineTo(s * 1.14f, s * 0.12f)                 // blunt snout / mouth front
        tmpPath.quadTo(s * 0.80f, s * 0.54f, s * 0.12f, s * 0.60f)  // jutting jaw + belly
        tmpPath.cubicTo(-s * 0.45f, s * 0.66f, -s * 0.84f, s * 0.46f, -s * 0.95f, s * 0.26f) // belly back to tail
        tmpPath.close()
    }

    /** Multiplies a colour's RGB by [factor] (clamped) for cheap shading tints. */
    private fun darken(color: Int, factor: Float): Int = Color.rgb(
        (Color.red(color) * factor).toInt().coerceIn(0, 255),
        (Color.green(color) * factor).toInt().coerceIn(0, 255),
        (Color.blue(color) * factor).toInt().coerceIn(0, 255),
    )

    private fun rotationFor(direction: Direction): Float = when (direction) {
        Direction.RIGHT, Direction.NONE -> 0f
        Direction.DOWN -> 90f
        Direction.LEFT -> 180f
        Direction.UP -> 270f
    }

    /**
     * Crocodile — long, low-slung body with a chunky tail and a toothy snout
     * pointing in its facing direction. Slightly bigger than the monkey/
     * piranha to communicate "this is a different threat." Frame toggles a
     * subtle tail wag.
     */
    fun drawCrocodile(
        canvas: Canvas,
        cx: Float, cy: Float,
        cellSize: Float,
        direction: Direction,
        frame: Int,
    ) {
        canvas.save()
        canvas.translate(cx, cy)
        // Orient the sprite to its direction of motion (drawn pointing RIGHT by
        // default). LEFT mirrors horizontally rather than rotating 180°, which
        // would also flip the croc upside down (belly up, ridges down).
        if (direction == Direction.LEFT) {
            canvas.scale(-1f, 1f)
        } else {
            val angleDeg = when (direction) {
                Direction.DOWN -> 90f
                Direction.UP -> 270f
                else -> 0f
            }
            canvas.rotate(angleDeg)
        }

        val s = cellSize * 0.6f
        // Main body — long flat oval extending forward.
        tmpRect.set(-s * 1.4f, -s * 0.5f, s * 1.2f, s * 0.5f)
        canvas.drawOval(tmpRect, crocBody)
        // Belly highlight (lighter underside).
        tmpRect.set(-s * 1.3f, -s * 0.15f, s * 1.0f, s * 0.45f)
        canvas.drawOval(tmpRect, crocBelly)
        // Tail — a triangular extension behind the body.
        val wag = if (frame == 0) s * 0.05f else -s * 0.05f
        tmpPath.reset()
        tmpPath.moveTo(-s * 1.3f, -s * 0.3f)
        tmpPath.lineTo(-s * 1.9f, wag)
        tmpPath.lineTo(-s * 1.3f, s * 0.3f)
        tmpPath.close()
        canvas.drawPath(tmpPath, crocBody)
        // Back ridges — three little nubs along the spine.
        for (i in 0..2) {
            val rx = -s * 0.8f + i * s * 0.6f
            canvas.drawCircle(rx, -s * 0.5f, s * 0.12f, crocBack)
        }
        // Snout — a wedge at the front with teeth.
        tmpPath.reset()
        tmpPath.moveTo(s * 1.0f, -s * 0.35f)
        tmpPath.lineTo(s * 1.7f, -s * 0.05f)
        tmpPath.lineTo(s * 1.7f, s * 0.05f)
        tmpPath.lineTo(s * 1.0f, s * 0.35f)
        tmpPath.close()
        canvas.drawPath(tmpPath, crocBody)
        // Tooth row — three little triangles along the upper jaw.
        for (i in 0..2) {
            val tx = s * 1.15f + i * s * 0.15f
            tmpPath.reset()
            tmpPath.moveTo(tx, -s * 0.05f)
            tmpPath.lineTo(tx + s * 0.06f, -s * 0.05f)
            tmpPath.lineTo(tx + s * 0.03f, s * 0.05f)
            tmpPath.close()
            canvas.drawPath(tmpPath, crocTooth)
        }
        // Eye — small dark dot on top of the head.
        canvas.drawCircle(s * 0.8f, -s * 0.30f, s * 0.10f, eyeWhite)
        canvas.drawCircle(s * 0.82f, -s * 0.30f, s * 0.05f, eyeBlack)

        canvas.restore()
    }

    fun drawShark(
        canvas: Canvas,
        cx: Float, cy: Float,
        cellSize: Float,
        direction: Direction,
        frame: Int,
    ) {
        canvas.save()
        canvas.translate(cx, cy)
        // Mirror horizontally for LEFT rather than rotating 180°, which would also
        // flip the sprite upside down (see drawPiranha).
        if (direction == Direction.LEFT) {
            canvas.scale(-1f, 1f)
        } else {
            canvas.rotate(rotationFor(direction))
        }
        val s = cellSize * 0.65f
        val tailFlick = if (frame % 2 == 0) -1f else 1f

        // Tail fin (behind = LEFT in local frame)
        tmpPath.reset()
        tmpPath.moveTo(-s * 0.95f, 0f)
        tmpPath.lineTo(-s * 1.40f, -s * 0.55f + tailFlick * cellSize * 0.05f)
        tmpPath.lineTo(-s * 1.40f,  s * 0.55f - tailFlick * cellSize * 0.05f)
        tmpPath.close()
        canvas.drawPath(tmpPath, sharkFin)

        // Body
        tmpRect.set(-s, -s * 0.45f, s * 1.05f, s * 0.45f)
        canvas.drawOval(tmpRect, sharkBody)

        // Lighter belly underside
        tmpRect.set(-s * 0.85f, s * 0.08f, s * 0.85f, s * 0.45f)
        canvas.drawOval(tmpRect, sharkBelly)

        // Dorsal fin
        tmpPath.reset()
        tmpPath.moveTo(-s * 0.20f, -s * 0.45f)
        tmpPath.lineTo( s * 0.10f, -s * 0.85f)
        tmpPath.lineTo( s * 0.30f, -s * 0.45f)
        tmpPath.close()
        canvas.drawPath(tmpPath, sharkFin)

        // Side pectoral fin
        tmpPath.reset()
        tmpPath.moveTo(-s * 0.05f,  s * 0.40f)
        tmpPath.lineTo(-s * 0.30f,  s * 0.80f + tailFlick * cellSize * 0.03f)
        tmpPath.lineTo( s * 0.20f,  s * 0.45f)
        tmpPath.close()
        canvas.drawPath(tmpPath, sharkFin)

        // Eye
        canvas.drawCircle(s * 0.55f, -s * 0.18f, s * 0.10f, eyeWhite)
        canvas.drawCircle(s * 0.58f, -s * 0.18f, s * 0.06f, eyeBlack)

        // Teeth: zigzag of small triangles
        tmpPath.reset()
        tmpPath.moveTo(s * 0.65f, s * 0.05f)
        tmpPath.lineTo(s * 0.72f, s * 0.20f)
        tmpPath.lineTo(s * 0.79f, s * 0.05f)
        tmpPath.lineTo(s * 0.86f, s * 0.20f)
        tmpPath.lineTo(s * 0.93f, s * 0.05f)
        tmpPath.lineTo(s * 1.00f, s * 0.20f)
        tmpPath.lineTo(s * 1.00f, s * 0.05f)
        tmpPath.close()
        canvas.drawPath(tmpPath, toothPaint)

        canvas.restore()
    }

    /**
     * Side-profile turtle that pops in over 0.25s and fades out across the
     * full duration. Visual style copied from Brick Basher's white-powerup
     * turtle: legs first (rounded ovals with dark outline), head poking out
     * the front (left side), shell as a dark-green dome with five hexagonal
     * scute outlines.
     */
    fun drawTurtle(
        canvas: Canvas,
        cx: Float, cy: Float,
        size: Float,
        timeLeft: Float,
        duration: Float,
    ) {
        val popScale = if (timeLeft > duration - 0.25f) (duration - timeLeft) / 0.25f else 1.0f
        val s = size * popScale
        val a = ((timeLeft / duration) * 255f).toInt().coerceIn(0, 255)
        turtleLegFill.alpha = a
        turtleLegStroke.alpha = a
        turtleShellFill.alpha = a
        turtleShellStroke.alpha = a
        turtleHexStroke.alpha = a
        turtleEye.alpha = a

        // Four legs (light green, behind shell)
        for ((dx, dy) in listOf(
            -0.65f to -0.25f, 0.65f to -0.25f,
            -0.55f to 0.45f, 0.55f to 0.45f,
        )) {
            tmpRect.set(
                cx + dx * s - s * 0.13f, cy + dy * s - s * 0.13f,
                cx + dx * s + s * 0.13f, cy + dy * s + s * 0.13f,
            )
            canvas.drawOval(tmpRect, turtleLegFill)
            canvas.drawOval(tmpRect, turtleLegStroke)
        }

        // Head poking out the left side
        val headCx = cx - s * 0.85f
        val headCy = cy + s * 0.05f
        canvas.drawCircle(headCx, headCy, s * 0.18f, turtleLegFill)
        canvas.drawCircle(headCx, headCy, s * 0.18f, turtleLegStroke)
        canvas.drawCircle(headCx - s * 0.05f, headCy - s * 0.05f, s * 0.03f, turtleEye)

        // Shell (dark green dome)
        tmpRect.set(cx - s * 0.7f, cy - s * 0.45f, cx + s * 0.7f, cy + s * 0.45f)
        canvas.drawOval(tmpRect, turtleShellFill)
        canvas.drawOval(tmpRect, turtleShellStroke)

        // Hexagonal scute pattern on shell (5 small hexes)
        val hexR = s * 0.13f
        val centers = listOf(
            cx to cy,
            cx - s * 0.36f to cy - s * 0.05f,
            cx + s * 0.36f to cy - s * 0.05f,
            cx - s * 0.18f to cy + s * 0.22f,
            cx + s * 0.18f to cy + s * 0.22f,
        )
        for ((hx, hy) in centers) {
            tmpPath.reset()
            for (i in 0..6) {
                val angle = (i * 60.0 - 90.0) * Math.PI / 180.0
                val px = hx + (hexR * kotlin.math.cos(angle)).toFloat()
                val py = hy + (hexR * kotlin.math.sin(angle)).toFloat()
                if (i == 0) tmpPath.moveTo(px, py) else tmpPath.lineTo(px, py)
            }
            tmpPath.close()
            canvas.drawPath(tmpPath, turtleHexStroke)
        }
    }

    /**
     * Pink heart that pops centre-screen when the extra-life powerup is collected,
     * then fades out over [duration]. Visual style copied from Brick Basher's
     * pink-powerup heart (cubic-Bezier path, hot-pink fill with light-pink stroke).
     */
    fun drawHeart(
        canvas: Canvas,
        cx: Float, cy: Float,
        size: Float,
        timeLeft: Float,
        duration: Float,
    ) {
        val popScale = if (timeLeft > duration - 0.25f) (duration - timeLeft) / 0.25f else 1.0f
        val s = size * popScale
        val alpha = ((timeLeft / duration) * 255f).toInt().coerceIn(0, 255)

        tmpPath.reset()
        tmpPath.moveTo(cx, cy - s * 0.2f)
        tmpPath.cubicTo(cx + s * 0.5f, cy - s * 0.9f, cx + s, cy - s * 0.3f, cx, cy + s * 0.7f)
        tmpPath.cubicTo(cx - s, cy - s * 0.3f, cx - s * 0.5f, cy - s * 0.9f, cx, cy - s * 0.2f)
        tmpPath.close()

        heartFill.color = Color.argb(alpha, 255, 80, 160)
        canvas.drawPath(tmpPath, heartFill)
        heartStroke.color = Color.argb(alpha, 255, 210, 230)
        canvas.drawPath(tmpPath, heartStroke)
    }

    /**
     * Fishing hook with a worm threaded on it — the bait powerup's pickup icon.
     * Pops centre-screen and fades over [duration], matching the turtle/heart.
     * The hook is a silver J with an eyelet loop and a barb; the worm is a fat
     * wiggly body draped over the shank with a few darker segment ticks.
     */
    fun drawHookedWorm(
        canvas: Canvas,
        cx: Float, cy: Float,
        size: Float,
        timeLeft: Float,
        duration: Float,
    ) {
        val popScale = if (timeLeft > duration - 0.25f) (duration - timeLeft) / 0.25f else 1.0f
        val s = size * popScale
        val alpha = ((timeLeft / duration) * 255f).toInt().coerceIn(0, 255)

        // --- Hook (silver) ---
        hookMetal.color = Color.argb(alpha, 200, 208, 215)
        hookMetal.strokeWidth = s * 0.10f
        // Eyelet loop at the very top.
        canvas.drawCircle(cx, cy - s * 0.78f, s * 0.13f, hookMetal)
        // Shank down from the eyelet, curving into a J and back up to the point.
        tmpPath.reset()
        tmpPath.moveTo(cx, cy - s * 0.65f)
        tmpPath.lineTo(cx, cy + s * 0.45f)
        tmpPath.quadTo(cx, cy + s * 0.95f, cx - s * 0.42f, cy + s * 0.82f)
        tmpPath.quadTo(cx - s * 0.78f, cy + s * 0.66f, cx - s * 0.58f, cy + s * 0.28f)
        canvas.drawPath(tmpPath, hookMetal)
        // Barb just below the point.
        tmpPath.reset()
        tmpPath.moveTo(cx - s * 0.58f, cy + s * 0.28f)
        tmpPath.lineTo(cx - s * 0.40f, cy + s * 0.46f)
        canvas.drawPath(tmpPath, hookMetal)

        // --- Worm (reddish-pink) draped over the upper shank ---
        wormBody.color = Color.argb(alpha, 205, 92, 106)
        wormBody.strokeWidth = s * 0.26f
        tmpPath.reset()
        tmpPath.moveTo(cx - s * 0.55f, cy - s * 0.42f)
        tmpPath.quadTo(cx - s * 0.05f, cy - s * 0.72f, cx + s * 0.22f, cy - s * 0.40f)
        tmpPath.quadTo(cx + s * 0.48f, cy - s * 0.08f, cx + s * 0.16f, cy + s * 0.16f)
        canvas.drawPath(tmpPath, wormBody)
        // A couple of darker segment ticks so it reads as a worm, not a noodle.
        wormSegment.color = Color.argb(alpha, 150, 60, 72)
        wormSegment.strokeWidth = s * 0.045f
        canvas.drawCircle(cx - s * 0.22f, cy - s * 0.55f, s * 0.12f, wormSegment)
        canvas.drawCircle(cx + s * 0.16f, cy - s * 0.40f, s * 0.12f, wormSegment)
        canvas.drawCircle(cx + s * 0.27f, cy - s * 0.10f, s * 0.11f, wormSegment)
    }

    /**
     * Small yellow bait fish — drawn at ±s extent. Body oval + triangular
     * tail + dorsal nub. Faces left/right based on a slow animation phase so
     * it looks like it's wiggling in place.
     */
    fun drawBaitFish(canvas: Canvas, cx: Float, cy: Float, s: Float, animTime: Float) {
        val wiggle = kotlin.math.sin(animTime * 6f) * 0.15f
        // Body: oval slightly tilted by wiggle
        tmpRect.set(cx - s * 0.6f, cy - s * 0.35f, cx + s * 0.4f, cy + s * 0.35f)
        canvas.drawOval(tmpRect, baitBody)
        // Belly highlight
        tmpRect.set(cx - s * 0.35f, cy - s * 0.05f, cx + s * 0.25f, cy + s * 0.25f)
        canvas.drawOval(tmpRect, baitBelly)
        // Tail (right side, wiggling)
        tmpPath.reset()
        tmpPath.moveTo(cx + s * 0.4f, cy)
        tmpPath.lineTo(cx + s * 0.75f, cy - s * (0.3f + wiggle))
        tmpPath.lineTo(cx + s * 0.75f, cy + s * (0.3f - wiggle))
        tmpPath.close()
        canvas.drawPath(tmpPath, baitBody)
        // Eye (dark dot on the left, "head" end)
        canvas.drawCircle(cx - s * 0.4f, cy - s * 0.10f, s * 0.06f, eyeBlack)
        // Outline
        tmpRect.set(cx - s * 0.6f, cy - s * 0.35f, cx + s * 0.4f, cy + s * 0.35f)
        canvas.drawOval(tmpRect, baitOutline)
    }

    fun drawBlackHole(canvas: Canvas, cx: Float, cy: Float, cellSize: Float, animTime: Float) {
        val s = cellSize * 0.45f
        canvas.drawCircle(cx, cy, s * 1.00f, bhRingOuter)
        canvas.drawCircle(cx, cy, s * 0.75f, bhRingMid)
        canvas.drawCircle(cx, cy, s * 0.45f, bhCore)
        // 8 sparkles drifting around the rim — accretion-disk feel.
        for (i in 0..7) {
            val angle = animTime * 1.5f + i * Math.PI.toFloat() / 4f
            val r = s * (0.92f + 0.05f * kotlin.math.sin(animTime * 2f + i.toFloat()).toFloat())
            val sx = cx + kotlin.math.cos(angle) * r
            val sy = cy + kotlin.math.sin(angle) * r
            canvas.drawCircle(sx, sy, s * 0.06f, bhSparkle)
        }
    }

    fun drawLightningOverlay(canvas: Canvas, viewWidth: Float, viewHeight: Float, alpha: Float) {
        lightningPaint.alpha = (alpha.coerceIn(0f, 1f) * 230f).toInt()
        canvas.drawRect(0f, 0f, viewWidth, viewHeight, lightningPaint)
    }
}
