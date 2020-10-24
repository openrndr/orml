package org.openrndr.orml.styletransfer

import org.openrndr.draw.ColorBuffer
import org.openrndr.draw.ColorFormat
import org.openrndr.draw.ColorType
import org.openrndr.draw.colorBuffer
import org.openrndr.extra.tensorflow.copyTo
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
    private var inputTensor = Tensor.of(TFloat32.DTYPE, Shape.of(1, 480, 640, 3))
    private var inputStyleTensor = Tensor.of(TFloat32.DTYPE, Shape.of(1, 1, 1, 100))
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
            inputTensor = Tensor.of(TFloat32.DTYPE, Shape.of(1, image.height.toLong(), image.width.toLong(), 3))
        }
        image.copyTo(inputImage)

        val db = DataBuffers.of(style, false, false)
        inputStyleTensor.data().write(db)

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

            val transformedTensor = tensors[0].expect(TFloat32.DTYPE)
            transformedTensor.copyTo(outputImage)
            transformedTensor.close()

        } ?: error("no session")
        return outputImage
    }

    companion object {
        fun load(): StyleTransformer {
            val bytes = URL(resourceUrl("/tfmodels/transformer.pb")).readBytes()
            val g = Graph()
            g.importGraphDef(GraphDef.parseFrom(bytes))
            return StyleTransformer(g, "transformer/expand/conv3/conv/Sigmoid")
        }

        fun loadSeparable(): StyleTransformer {
            val bytes = URL(resourceUrl("/tfmodels/transformer-separable.pb")).readBytes()
            val g = Graph()
            g.importGraphDef(GraphDef.parseFrom(bytes))
            return StyleTransformer(g, "transformer/expand/conv3/dilate_conv/Sigmoid")
        }
    }
}