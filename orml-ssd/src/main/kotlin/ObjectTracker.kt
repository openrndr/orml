package org.openrndr.orml.ssd

import org.openrndr.events.Event
import org.openrndr.math.Polar
import org.openrndr.math.Vector2
import org.openrndr.math.max
import org.openrndr.math.min
import org.openrndr.shape.Rectangle
import kotlin.math.abs
import kotlin.math.exp

class Particle {
    var rectangle = Rectangle(0.0, 0.0, 0.0, 0.0)
    var weight = 0.0
}

fun List<Particle>.normalize(): List<Particle> {
    val input = this
    val totalWeight = input.sumByDouble { it.weight }
    return if (totalWeight < 1E-7) {
        input
    } else {
        input.map {
            Particle().apply {
                weight = it.weight / totalWeight
                rectangle = it.rectangle
            }
        }
    }
}

fun List<Particle>.sample(number: Int = this.size): List<Particle> {
    val particles = this
    val sorted = particles.sortedBy { it.weight }
    val totalWeight = particles.sumByDouble { it.weight }
    return if (totalWeight < 1E-7) {
        (0 until number).map {
            sorted.random()
        }
    } else {
        val randomNumbers = (0 until number).map { Math.random() * totalWeight }.sortedDescending().toMutableList()
        val result = mutableListOf<Particle>()

        var sum = 0.0
        for (s in sorted) {
            sum += s.weight
            if (randomNumbers.isEmpty()) {
                break
            }
            while (randomNumbers.last() < sum) {
                result.add(s)
                randomNumbers.removeLast()
                if (randomNumbers.isEmpty()) {
                    break
                }
            }
        }
        result
    }
}

fun List<SSDRectangle>.best(p: Particle): SSDRectangle {
    return this.maxByOrNull {
        intersectionOverUnion(it.area, p.rectangle)
    }!!
}

fun List<Particle>.propagate(): List<Particle> {
    val input = this
    return input.map {
        val distance = Math.random() * 1.0 / 4.0
        val a = Math.random() * 360.0
        val offset = Polar(a, distance).cartesian
        val distanceScore = Math.exp(-distance * 1.0)

        val sizeIncrement = (Math.random() * 2.0 - 1.0) * 0.05
        val ds = abs(sizeIncrement)
        val scaleScore = Math.exp(-ds * 1.0)

        Particle().apply {
            val newPos = max(Vector2.ZERO, min(Vector2.ONE, it.rectangle.center + offset))
            rectangle = Rectangle.fromCenter(newPos, (it.rectangle.width + sizeIncrement).coerceIn(0.05, 0.5), (it.rectangle.height + sizeIncrement).coerceIn(0.05, 0.5))
            weight = scaleScore * distanceScore
        }
    }
}

fun List<Particle>.observe(ssdRectangles: List<SSDRectangle>): List<Particle> {
    val input = this
    return input.map { particle ->
        val bestSSD = ssdRectangles.best(particle)
        val iou = 1.0 - intersectionOverUnion(bestSSD.area, particle.rectangle)

        val p = 1.0 / (1.0 + exp(-bestSSD.score))
        Particle().apply {
            rectangle = particle.rectangle
            weight = particle.weight * p * exp(-iou)
        }
    }
}

class ObjectEvent

class ObjectTracker {
    private var particles = List(1000) {
        Particle().apply {
            weight = 1.0 / 1000.0
            rectangle = Rectangle.fromCenter(Vector2(Math.random(), Math.random()), 0.1, 0.1)
        }
    }

    var threshold = 0.4

    private val history = mutableListOf<Boolean>()
    var hasObject = false
        private set

    val newObject = Event<ObjectEvent>()
    val lostObject = Event<ObjectEvent>()

    var objectRectangle = Rectangle(0.0, 0.0, 0.0, 0.0)
    var objectSmoothRectangle = objectRectangle
    var objectSSDRectangle = SSDRectangle(area = objectRectangle, score = 0.0, landmarks = emptyList())
    var objectSmoothSSDRectangle = objectSSDRectangle
    fun update(ssdRectangles: List<SSDRectangle>) {
        particles = particles.sample()
        particles = particles.propagate()
        particles = particles.observe(ssdRectangles)

        val best = particles.sample(100)
        val averageWeight = best.sumByDouble { it.weight } / 100.0

        var averageRectangle = Rectangle(0.0, 0.0, 0.0, 0.0)
        var weight = 0.0
        for (p in best) {
            averageRectangle += p.rectangle * p.weight
            weight += p.weight
        }
        averageRectangle /= weight

        val similarity = intersectionOverUnion(objectRectangle, averageRectangle) / 1.0

        objectRectangle = averageRectangle
        objectSSDRectangle = ssdRectangles.maxByOrNull { intersectionOverUnion(it.area, averageRectangle) }
                ?: error("no ssd rectangles")
        objectSmoothRectangle = objectSmoothRectangle * (similarity) + objectRectangle * (1.0 - similarity)
        objectSmoothSSDRectangle = SSDRectangle(1.0, objectSmoothRectangle, emptyList())

        history.add(0, averageWeight > threshold)
        if (history.size > 10) {
            history.removeLast()
        }

        if (!hasObject && history.all { it }) {
            hasObject = true
            newObject.trigger(ObjectEvent())
        }

        if (hasObject && history.none { it }) {
            hasObject = false
            lostObject.trigger(ObjectEvent())
        }
    }
}