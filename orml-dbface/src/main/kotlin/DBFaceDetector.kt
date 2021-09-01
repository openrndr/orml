package org.openrndr.orml.dbface

import org.openrndr.draw.ColorBuffer
import org.openrndr.draw.ColorFormat
import org.openrndr.draw.ColorType
import org.openrndr.draw.colorBuffer
import org.openrndr.extra.tensorflow.arrays.get
import org.openrndr.extra.tensorflow.copyTo
import org.openrndr.extra.tensorflow.toFloatArray4D
import org.openrndr.math.Vector2
import org.openrndr.math.Vector4
import org.openrndr.orml.ssd.SSDRectangle
import org.openrndr.orml.ssd.nonMaxSuppression
import org.openrndr.orml.utils.MultiplyAdd
import org.openrndr.resourceUrl
import org.openrndr.shape.IntRectangle
import org.openrndr.shape.Rectangle
import org.tensorflow.Graph
import org.tensorflow.Session
import org.tensorflow.Tensor
import org.tensorflow.ndarray.Shape
import org.tensorflow.proto.framework.GraphDef
import org.tensorflow.types.TFloat32
import java.net.URL
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max

private fun _exp(v: Double): Double {
    if (abs(v) < 1.0)
        return v * exp(1.0)

    return if (v > 0.0)
        exp(v)
    else
        -exp(-v)
}

class DBFaceDetector(val graph: Graph) {
    val inputTensor = TFloat32.tensorOf(Shape.of(1, 480, 640, 3))
    val inputImage = colorBuffer(640, 480, format = ColorFormat.RGB, type = ColorType.FLOAT32)

    val multiplyAdd by lazy { MultiplyAdd() }

    var detectionThreshold = 0.3
    var iouThreshold = 0.5

    var session: Session? = null
    fun start() {
        session = Session(graph)
    }

    fun close() {
        session?.close()
    }

    fun detectFaces(squareInput: ColorBuffer, filterResults: Boolean = true): List<SSDRectangle> {
        squareInput.copyTo(target = inputImage, fromLevel = 0, toLevel = 0,
            sourceRectangle = IntRectangle(0, 0, squareInput.width, squareInput.height),
                targetRectangle = IntRectangle(0, inputImage.height, inputImage.width, -inputImage.width))
        multiplyAdd.scale = Vector4.ONE * 2.0
        multiplyAdd.offset = Vector4.ONE * -1.0
        multiplyAdd.apply(inputImage, inputImage)

        inputImage.copyTo(inputTensor)
        if (session == null) {
            start()
        }

        val result = session?.let {
            val runner = it.runner()
            val tensors = runner.feed("input", inputTensor)
                    .fetch("Identity")
                    .fetch("Identity_1")
                    .fetch("Identity_2")
                    .run()

            val identity0 = tensors[0] as TFloat32
            val identity1 = tensors[1] as TFloat32
            val identity2 = tensors[2] as TFloat32

            val boxes = identity1.toFloatArray4D()
            val scores = identity2.toFloatArray4D()
            val landmarks = identity0.toFloatArray4D()

            val resultHeight = 120
            val resultWidth = 160

            val result = mutableListOf<SSDRectangle>()

            val longestAxis = max(resultWidth, resultHeight)

            for (y in 0 until resultHeight) {
                for (x in 0 until resultWidth) {
                    val score = scores[0, y, x, 0].toDouble()
                    if (score >= detectionThreshold) {
                        val boxX = (boxes[0, y, x, 0].toDouble())
                        val boxY = (boxes[0, y, x, 1].toDouble())
                        val boxWidth = (boxes[0, y, x, 2].toDouble())
                        val boxHeight = (boxes[0, y, x, 3].toDouble())

                        val x0 = (x - boxX) / longestAxis
                        val y0 = (y - boxY) / longestAxis
                        val x1 = (x + boxWidth) /  longestAxis
                        val y1 = (y + boxHeight) / longestAxis

                        val rectangle = Rectangle(x0, y0, x1 - x0, y1 - y0)

                        val landmarkVectors = mutableListOf<Vector2>()
                        for (i in 0 until 5) {
                            landmarkVectors.add(
                                    Vector2(
                                            (x + _exp(landmarks[0, y, x, i] * 4.0)) / longestAxis,
                                            (y + _exp(landmarks[0, y, x, i + 5] * 4.0)) / longestAxis)
                            )
                        }
                        result.add(SSDRectangle(score, rectangle, landmarkVectors))
                    }
                }
            }
            identity0.close()
            identity1.close()
            identity2.close()
            result
        } ?: error("no session")

        return if (filterResults) {
            nonMaxSuppression(result, iouThreshold)
        } else {
            result
        }
    }

    companion object {
        fun load(): DBFaceDetector {
            val bytes = URL(resourceUrl("/tfmodels/dbface.pb")).readBytes()
            val g = Graph()
            g.importGraphDef(GraphDef.parseFrom(bytes))
            return DBFaceDetector(g)
        }
    }
}