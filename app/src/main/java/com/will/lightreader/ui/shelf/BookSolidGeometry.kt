package com.will.lightreader.ui.shelf

import kotlin.math.cos
import kotlin.math.sin

internal const val JacketWidth = 104f
internal const val JacketHeight = 154f
internal const val JacketAspectRatio = JacketWidth / JacketHeight
internal const val ShelfBankLayerThreshold = .02f

/** A raised book initially shares the same visible silhouette as it had in the bank. */
internal fun shelfPullTuck(amount: Float): Float = ((.56f - amount) / .52f).coerceIn(0f, 1f)

/** Independent orthographic cuboid, in dp. All faces reuse the same projected vertices. */
internal data class SolidPoint(val x: Float, val y: Float)
internal data class SolidFace(val a: SolidPoint, val b: SolidPoint, val c: SolidPoint, val d: SolidPoint) {
    val points = listOf(a, b, c, d)
    val left = points.minOf { it.x }
    val top = points.minOf { it.y }
    val right = points.maxOf { it.x }
    val bottom = points.maxOf { it.y }
    fun contains(x: Float, y: Float): Boolean {
        if (x < left || x > right || y < top || y > bottom) return false
        val area = (b.x - a.x) * (d.y - a.y) - (b.y - a.y) * (d.x - a.x)
        if (kotlin.math.abs(area) < .01f) return false
        return points.indices.all { i ->
            val p = points[i]
            val q = points[(i + 1) % points.size]
            val cross = (q.x - p.x) * (y - p.y) - (q.y - p.y) * (x - p.x)
            if (area > 0f) cross >= -.01f else cross <= .01f
        }
    }
}
internal data class BookSolid(val spine: SolidFace, val cover: SolidFace, val cap: SolidFace) {
    fun contains(x: Float, y: Float) = spine.contains(x, y) || cover.contains(x, y) || cap.contains(x, y)
}

/** The cover starts turning first. Insertion overlaps the turn, then eases to the resting plane. */
internal fun projectReturningBook(progress: Float): BookSolid {
    fun ease(start: Float, end: Float): Float {
        val t = ((progress - start) / (end - start)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
    return projectBookSolid(pull = 1f - ease(.14f, 1f), facing = 1f - ease(0f, .79f))
}

internal fun projectBookSolid(pull: Float, height: Float = JacketHeight, width: Float = JacketWidth, thickness: Float = 17f, facing: Float = 0f): BookSolid {
    val amount = if (pull.isFinite()) pull.coerceIn(0f, 1f) else 0f
    val turn = if (facing.isFinite()) facing.coerceIn(0f,1f) else 0f
    val yaw = Math.toRadians(33.0 + 57.0*turn)
    val pitch = Math.toRadians(20.0 * (1-turn))
    val sideX = sin(yaw).toFloat()
    val sideY = -(cos(yaw) * sin(pitch)).toFloat()
    val frontX = cos(yaw).toFloat()
    val frontY = (sin(yaw) * sin(pitch)).toFloat()
    val travel = width * .76f * amount
    val lift = height * .5f * amount * amount * (3 - 2 * amount)
    fun p(x: Float, y: Float, z: Float) = SolidPoint(
        52f + 30f*turn + x * frontX + (z - travel) * sideX,
        92f + y + x * frontY + (z - travel) * sideY - lift,
    )
    val a = p(0f, 0f, 0f); val b = p(thickness, 0f, 0f)
    val c = p(thickness, height, 0f); val d = p(0f, height, 0f)
    val e = p(0f, 0f, width); val f = p(thickness, 0f, width)
    val g = p(thickness, height, width)
    return BookSolid(SolidFace(a,b,c,d), SolidFace(b,f,g,c), SolidFace(a,e,f,b))
}
