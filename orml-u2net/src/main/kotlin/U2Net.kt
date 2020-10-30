package org.openrndr.orml.u2net

import org.openrndr.draw.ColorBuffer
import org.openrndr.draw.ColorFormat
import org.openrndr.draw.ColorType
import org.openrndr.draw.colorBuffer
import org.openrndr.extra.tensorflow.copyTo
import org.openrndr.extra.tensorflow.summary
import org.openrndr.math.Vector4
import org.openrndr.orml.utils.MultiplyAdd
import org.openrndr.resourceUrl
import org.openrndr.shape.IntRectangle
import org.tensorflow.Graph
import org.tensorflow.Session
import org.tensorflow.Tensor
import org.tensorflow.ndarray.Shape
import org.tensorflow.proto.framework.GraphDef
import org.tensorflow.types.TFloat32
import java.net.URL

class U2Net(private val graph: Graph) {
    private val inputTensor = Tensor.of(TFloat32.DTYPE, Shape.of(1, 480, 640, 3))
    private val inputImage = colorBuffer(640, 480, format = ColorFormat.RGB, type = ColorType.FLOAT32)
    private val outputImage = colorBuffer(640, 480, format = ColorFormat.R, type = ColorType.FLOAT32)
    private val multiplyAdd = MultiplyAdd()

    var session: Session? = null
    fun start() {
        session = Session(graph)
    }

    fun close() {
        session?.close()
    }

    fun extract(image: ColorBuffer): ColorBuffer {
        if (session == null) {
            start()
        }
        image.copyTo(inputImage, targetRectangle = IntRectangle(0, inputImage.height, inputImage.width, -inputImage.height))

        multiplyAdd.scale = Vector4.ONE * 2.0
        multiplyAdd.offset = Vector4.ONE * -1.0
        multiplyAdd.apply(inputImage, inputImage)

        inputImage.copyTo(inputTensor)

        session?.let {
            val runner = it.runner()
            val tensors = runner.feed("inputs", inputTensor)
                    .fetch("functional_1/tf_op_layer_Sigmoid_6/Sigmoid_6")
                    .run()

            val identityTensor = tensors[0].expect(TFloat32.DTYPE)
            identityTensor.summary()
            identityTensor.copyTo(outputImage)

            multiplyAdd.scale = Vector4.ONE * 0.5
            multiplyAdd.offset = Vector4.ONE * 0.5
            multiplyAdd.apply(outputImage, outputImage)

            identityTensor.close()

        } ?: error("no session")
        return outputImage
    }

    companion object {
        fun load(): U2Net {
            val bytes = URL(resourceUrl("/tfmodels/u2netp_480x640_float32.pb")).readBytes()
            val g = Graph()
            g.importGraphDef(GraphDef.parseFrom(bytes))
            return U2Net(g)
        }
    }
}