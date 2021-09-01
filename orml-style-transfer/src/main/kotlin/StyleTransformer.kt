package org.openrndr.orml.styletransfer

import org.openrndr.draw.*
import org.openrndr.extra.tensorflow.copyTo
import org.openrndr.orml.utils.fetchORMLModel
import org.openrndr.resourceUrl
import org.tensorflow.Graph
import org.tensorflow.Session
import org.tensorflow.Tensor
import org.tensorflow.ndarray.Shape
import org.tensorflow.ndarray.buffer.DataBuffers
import org.tensorflow.proto.framework.GraphDef
import org.tensorflow.types.TFloat32
import java.net.URL


class StyleTransformer(val graph: Graph, val outputTensorName : String) {
    private var inputTensor = TFloat32.tensorOf(Shape.of(1, 480, 640, 3))
    private var inputStyleTensor = TFloat32.tensorOf(Shape.of(1, 1, 1, 100))
    private var inputImage = colorBuffer(640, 480, format = ColorFormat.RGB, type = ColorType.FLOAT32)
    private var outputImage = colorBuffer(640, 480, format = ColorFormat.RGB, type = ColorType.FLOAT32)

    var session: Session? = null
    fun start() {
        session = Session(graph)
    }

    fun close() {
        session?.close()
    }

    fun transformStyle(image: ColorBuffer, style: FloatArray): ColorBuffer {
        if (!inputImage.isEquivalentTo(image, ignoreFormat = true, ignoreType = true)) {
            inputImage.destroy()
            inputImage = image.createEquivalent(format = ColorFormat.RGB, type = ColorType.FLOAT32)
            outputImage.destroy()
            outputImage = image.createEquivalent(format = ColorFormat.RGB, type = ColorType.FLOAT32)
            inputTensor.close()
            inputTensor = TFloat32.tensorOf(Shape.of(1, image.height.toLong(), image.width.toLong(), 3))
        }
        image.copyTo(inputImage)

        val db = DataBuffers.of(style, false, false)
        inputStyleTensor.write(db)

        inputImage.copyTo(inputTensor)
        if (session == null) {
            start()
        }

        session?.let {
            val tensors = it.runner()
                    .feed("Placeholder", inputTensor)
                    .feed("Placeholder_1", inputStyleTensor)
                    .fetch(outputTensorName)
                    .run()

            val transformedTensor = tensors[0] as TFloat32
            transformedTensor.copyTo(outputImage)
            transformedTensor.close()

        } ?: error("no session")
        return outputImage
    }

    companion object {
        fun load(): StyleTransformer {
            val bytes = fetchORMLModel(
                "style-transformer-1.0",
                "faa3e3742c7415daf83c1cca5f756ab49d862392729ac79de5aaf5492f2fee3f"
            )
            val g = Graph()
            g.importGraphDef(GraphDef.parseFrom(bytes))
            return StyleTransformer(g, "transformer/expand/conv3/conv/Sigmoid")
        }

        fun loadSeparable(): StyleTransformer {
            val bytes = fetchORMLModel(
                "style-transformer-separable-1.0",
                "0a866ca0eb9044c8aad92644df8ce036c60ff4ee7bb3bbc399cc5cb5a230f9e0"
            )
            val g = Graph()
            g.importGraphDef(GraphDef.parseFrom(bytes))
            return StyleTransformer(g, "transformer/expand/conv3/dilate_conv/Sigmoid")
        }
    }
}