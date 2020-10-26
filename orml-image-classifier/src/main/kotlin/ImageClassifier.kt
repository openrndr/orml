import org.openrndr.draw.ColorBuffer
import org.openrndr.draw.ColorFormat
import org.openrndr.draw.ColorType
import org.openrndr.draw.colorBuffer
import org.openrndr.extra.tensorflow.*
import org.openrndr.resourceUrl
import org.openrndr.shape.IntRectangle
import org.tensorflow.Graph
import org.tensorflow.Session
import org.tensorflow.Tensor
import org.tensorflow.ndarray.Shape
import org.tensorflow.proto.framework.GraphDef
import org.tensorflow.types.TFloat32
import java.net.URL

class ImageClassifier(val graph: Graph) {
    private val inputTensor = Tensor.of(TFloat32.DTYPE, Shape.of(1, 224, 224, 3))
    private val inputImage = colorBuffer(224, 224, format = ColorFormat.RGB, type = ColorType.FLOAT32)

    var session: Session? = null
    fun start() {
        session = Session(graph)
    }

    fun close() {
        session?.close()
    }

    fun classify(image: ColorBuffer): FloatArray {
        return infer(image, "MobilenetV3/Predictions/Softmax")
    }

    fun embed(image: ColorBuffer): FloatArray {
        return infer(image, "MobilenetV3/Logits/Conv2d_1c_1x1/BiasAdd")
    }

    private fun infer(image: ColorBuffer, resultTensorName: String): FloatArray {
        image.copyTo(inputImage, targetRectangle = IntRectangle(0, inputImage.height, inputImage.width, -inputImage.height))
        inputImage.copyTo(inputTensor)
        if (session == null) {
            start()
        }
        val result = session?.let {
            val runner = it.runner()
            val tensors = runner.feed("input", inputTensor)
                    .fetch(resultTensorName)
                    .run()

            val res = tensors[0].expect(TFloat32.DTYPE)
            when (res.shape().numDimensions()) {
                4 -> res.toFloatArray4D()[0][0][0]
                3 -> res.toFloatArray3D()[0][0]
                2 -> res.toFloatArray2D()[0]
                1 -> res.toFloatArray()
                else -> error("unsupported tensor dimensions")
            }

        } ?: error("no session")
        return result
    }


    companion object {
        fun load(): ImageClassifier {
            val bytes = URL(resourceUrl("/tfmodels/v3-large-minimalistic_224_1.0_float.pb")).readBytes()
            val g = Graph()
            g.importGraphDef(GraphDef.parseFrom(bytes))
            return ImageClassifier(g)
        }
    }
}