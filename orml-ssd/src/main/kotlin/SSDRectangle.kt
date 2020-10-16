package org.openrndr.orml.ssd


import org.openrndr.math.Vector2
import org.openrndr.math.map
import org.openrndr.shape.Rectangle
import kotlin.math.max
import kotlin.math.min

val unitRectangle = Rectangle(0.0, 0.0, 1.0, 1.0)

fun Vector2.map(before: Rectangle, after: Rectangle): Vector2 {
    val nx = x.map(before.x, before.x + before.width, after.x, after.x + after.width)
    val ny = y.map(before.y, before.y + before.height, after.y, after.y + after.height)
    return Vector2(nx, ny)
}

fun Rectangle.map(before: Rectangle, after: Rectangle): Rectangle {
    return Rectangle(corner.map(before, after), width.map(0.0, before.width, 0.0, after.width), height.map(0.0, before.height, 0.0, after.height))
}

data class SSDRectangle(val score: Double, val area: Rectangle, val landmarks: List<Vector2>, val reference: Rectangle = unitRectangle) {
    fun map(rectangle: Rectangle): SSDRectangle {
        return SSDRectangle(score, area.map(reference, rectangle), landmarks.map { it.map(reference, rectangle) })
    }
}


fun intersectionOverUnion(region0: Rectangle, region1: Rectangle): Double {
    val sx0 = region0.corner.x
    val sy0 = region0.corner.y
    val ex0 = region0.corner.x + region0.width
    val ey0 = region0.corner.y + region0.height
    val sx1 = region1.corner.x
    val sy1 = region1.corner.y
    val ex1 = region1.corner.x + region1.width
    val ey1 = region1.corner.y + region1.height

    val xmin0 = min(sx0, ex0)
    val ymin0 = min(sy0, ey0)
    val xmax0 = max(sx0, ex0)
    val ymax0 = max(sy0, ey0)
    val xmin1 = min(sx1, ex1)
    val ymin1 = min(sy1, ey1)
    val xmax1 = max(sx1, ex1)
    val ymax1 = max(sy1, ey1)

    val area0 = (ymax0 - ymin0) * (xmax0 - xmin0)
    val area1 = (ymax1 - ymin1) * (xmax1 - xmin1)
    if (area0 <= 0 || area1 <= 0) return 0.0

    val intersectXmin = max(xmin0, xmin1)
    val intersectYmin = max(ymin0, ymin1)
    val intersectXmax = min(xmax0, xmax1)
    val intersectYmax = min(ymax0, ymax1)

    val intersectArea = max(intersectYmax - intersectYmin, 0.0) *
            max(intersectXmax - intersectXmin, 0.0)

    return intersectArea / (area0 + area1 - intersectArea)
}

fun nonMaxSuppression(input: List<SSDRectangle>, threshold: Double = 0.5): List<SSDRectangle> {
    val sorted = input.sortedByDescending { it.score }
    val result = mutableListOf<SSDRectangle>()

    for (regionCandidate in sorted) {
        var ignoreCandidate = false
        for (regionNms in result) {
            val iou = intersectionOverUnion(regionCandidate.area, regionNms.area)
            if (iou > threshold) {
                ignoreCandidate = true
                break
            }
        }
        if (!ignoreCandidate) {
            result.add(regionCandidate)
        }
    }
    return result
}
