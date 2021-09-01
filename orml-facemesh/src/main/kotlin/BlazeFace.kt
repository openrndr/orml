package org.openrndr.orml.facemesh

import org.openrndr.draw.*
import org.openrndr.extra.tensorflow.arrays.get
import org.openrndr.extra.tensorflow.copyTo
import org.openrndr.extra.tensorflow.summary
import org.openrndr.extra.tensorflow.toFloatArray3D
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



class BlazeFace(val graph: Graph) {

    val inputTensor = TFloat32.tensorOf(Shape.of(1, 128, 128, 3))
    val inputImage = colorBuffer(128, 128, format = ColorFormat.RGB, type = ColorType.FLOAT32)
    val inputImageFlipped = inputImage.createEquivalent()
    val multiplyAdd = MultiplyAdd()

    var session: Session? = null
    fun start() {
        session = Session(graph)
    }

    fun close() {
        session?.close()
    }

    fun detectFaces(squareInput: ColorBuffer, filter:Boolean = true) : List<SSDRectangle> {
        if (session == null) {
            start()
        }
        squareInput.copyTo(inputImage, sourceRectangle = IntRectangle(0, 0, squareInput.width, squareInput.height),targetRectangle = IntRectangle(0, 128, 128, -128))
        multiplyAdd.scale = Vector4.ONE * 0.8
        multiplyAdd.offset = Vector4.ONE * 0.1
        multiplyAdd.apply(inputImage, inputImage)

        inputImage.copyTo(inputTensor)
        val result = session?.let {
            val runner = it.runner()
            val tensors = runner.feed("input", inputTensor)
                    .fetch("Identity")
                    .run()

            val identityTensor = tensors[0]

            val rectangleFloats = (identityTensor as TFloat32).toFloatArray3D()

            val landmarkCount = 6

            val ssdRectangles = (0 until 896).map { rectangleIndex ->
                val anchorX = anchors[rectangleIndex * 2] * 128.0
                val anchorY = anchors[rectangleIndex * 2 + 1] * 128.0

                val score = rectangleFloats[0, rectangleIndex, 0].toDouble()
                val cx = rectangleFloats[0, rectangleIndex, 1].toDouble() + anchorX
                val cy = rectangleFloats[0, rectangleIndex, 2].toDouble() + anchorY
                val w = rectangleFloats[0, rectangleIndex, 3].toDouble()
                val h = rectangleFloats[0, rectangleIndex, 4].toDouble()

                val fx = (cx - w / 2.0) / 128.0
                val fy = (cy - h / 2.0) / 128.0
                val fw = w / 128.0
                val fh = h / 128.0

                val rectangle = Rectangle(fx, fy, fw, fh)

                val landmarks = (0 until landmarkCount).map { landmarkIndex ->
                    val lx = rectangleFloats[0, rectangleIndex, 5 + landmarkIndex * 2] + anchorX
                    val ly = rectangleFloats[0, rectangleIndex, 5 + landmarkIndex * 2 + 1] + anchorY
                    Vector2(lx / 128.0, ly / 128.0)
                }

                SSDRectangle(score, rectangle, landmarks)
            }
            identityTensor.close()

            ssdRectangles.sortedByDescending { it.score }.let {
                if (filter) {
                    it.take(1)
                } else {
                    it
                }
                //nonMaxSuppression(ssdRectangles)
            }
        } ?: error("no session")
        return result
    }

    companion object {
        fun load(): BlazeFace {
            val bytes = URL(resourceUrl("/tfmodels/blazeface.pb")).readBytes()
            val g = Graph()
            g.importGraphDef(GraphDef.parseFrom(bytes))
            return BlazeFace(g)
        }
    }

}