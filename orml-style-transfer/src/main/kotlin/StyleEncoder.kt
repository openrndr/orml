package org.openrndr.orml.styletransfer

import org.openrndr.draw.ColorBuffer
import org.openrndr.draw.ColorFormat
import org.openrndr.draw.ColorType
import org.openrndr.draw.colorBuffer
import org.openrndr.extra.tensorflow.copyTo
import org.openrndr.extra.tensorflow.toFloatArray4D
import org.openrndr.resourceUrl
import org.tensorflow.Graph
import org.tensorflow.Session
import org.tensorflow.Tensor
import org.tensorflow.ndarray.Shape
import org.tensorflow.proto.framework.GraphDef
import org.tensorflow.types.TFloat32
import java.net.URL

class StyleEncoder(val graph: Graph) {
    var inputTensor = TFloat32.tensorOf(Shape.of(1, 480, 640, 3))
    var inputImage = colorBuffer(640, 480, format = ColorFormat.RGB, type = ColorType.FLOAT32)

    var session: Session? = null
    fun start() {
        session = Session(graph)
    }

    fun close() {
        session?.close()
    }

    fun encodeStyle(image: ColorBuffer) : FloatArray {
        if (!inputImage.isEquivalentTo(image, ignoreFormat = true, ignoreType = true)) {
            inputImage.destroy()
            inputImage = image.createEquivalent(format = ColorFormat.RGB, type = ColorType.FLOAT32)
            inputTensor.close()
            inputTensor = TFloat32.tensorOf(Shape.of(1, image.height.toLong(), image.width.toLong(), 3))
        }
        image.copyTo(inputImage)
        inputImage.copyTo(inputTensor)
        if (session == null) {
            start()
        }

        return session?.let {
            val tensors = it.runner()
                    .feed("Placeholder", inputTensor)
                    .fetch("Conv/BiasAdd")
                    .run()
            val styleTensor = tensors[0] as TFloat32
            val style = styleTensor.toFloatArray4D()[0][0][0]
            styleTensor.close()
            style
        } ?: error("no session")
    }

    companion object {
        fun load(): StyleEncoder {
            val bytes = URL(resourceUrl("/tfmodels/style-inception.pb")).readBytes()
            val g = Graph()
            g.importGraphDef(GraphDef.parseFrom(bytes))
            return StyleEncoder(g)
        }
    }
}