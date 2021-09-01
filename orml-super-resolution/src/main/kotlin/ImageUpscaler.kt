import org.openrndr.draw.*
import org.openrndr.extra.tensorflow.copyTo
import org.openrndr.orml.utils.fetchORMLModel
import org.openrndr.resourceUrl
import org.openrndr.shape.IntRectangle
import org.tensorflow.Graph
import org.tensorflow.Session
import org.tensorflow.Tensor
import org.tensorflow.ndarray.Shape
import org.tensorflow.proto.framework.GraphDef
import org.tensorflow.types.TFloat32
import java.net.URL

/**
 * image upscaler based on super resolution neural net
 */
class ImageUpscaler(val graph: Graph) {
    private var inputTensorY = TFloat32.tensorOf(Shape.of(1, 480, 640, 1))
    private var inputTensorPbPr = TFloat32.tensorOf(Shape.of(1, 960, 1280, 2))
    private var inputImageY = colorBuffer(640, 480, format = ColorFormat.R, type = ColorType.FLOAT32)
    private var inputImagePbPr = colorBuffer(640, 480, format = ColorFormat.RG, type = ColorType.FLOAT32)
    private var inputImagePbPr2 = colorBuffer(1280, 960, format = ColorFormat.RG, type = ColorType.FLOAT32)
    private val rgbToYpbpr by lazy { RgbToYpbpr() }

    var session: Session? = null
    fun start() {
        session = Session(graph)
    }

    fun close() {
        session?.close()
    }

    /**
     * upscale [image] 2x
     *
     * Upscale image using a super resolution neural net.
     *
     * @param image the image [ColorBuffer] to upscale
     * @param result optional [ColorBuffer] for result, when null a new [ColorBuffer] will be allocated
     * @param prescaleFilter [MagnifyingFilter] to use in prescale stage, default is [MagnifyingFilter.LINEAR]
     */
    fun upscale(
            image: ColorBuffer,
            result: ColorBuffer? = null,
            octaves: Int = 1,
            prescaleFilter: MagnifyingFilter = MagnifyingFilter.LINEAR
    ): ColorBuffer {
        if (inputImageY.width != image.width || inputImageY.height != image.height) {
            inputImageY.destroy()
            inputImageY = colorBuffer(image.width, image.height, format = ColorFormat.R, type = ColorType.FLOAT32)
            inputImagePbPr.destroy()
            inputImagePbPr = colorBuffer(image.width, image.height, format = ColorFormat.RG, type = ColorType.FLOAT32)
            inputImagePbPr2 = colorBuffer(image.width * 2, image.height * 2, format = ColorFormat.RG, type = ColorType.FLOAT32)
            inputTensorY.close()
            inputTensorY = TFloat32.tensorOf(Shape.of(1, image.height.toLong(), image.width.toLong(), 1))
            inputTensorPbPr.close()
            inputTensorPbPr = TFloat32.tensorOf(Shape.of(1, image.height.toLong() * 2, image.width.toLong() * 2, 2))
        }
        if (session == null) {
            start()
        }

        rgbToYpbpr.apply(image, arrayOf(inputImageY, inputImagePbPr))
        inputImagePbPr.copyTo(inputImagePbPr2,
                sourceRectangle = IntRectangle(0, 0, inputImagePbPr.width, inputImagePbPr.height),
                targetRectangle = IntRectangle(0, 0, inputImagePbPr2.width, inputImagePbPr2.height),
                filter = MagnifyingFilter.NEAREST
        )

        inputImageY.copyTo(inputTensorY)
        inputImagePbPr2.copyTo(inputTensorPbPr)

        val outputImage = session?.let {
            val tensors = it.runner()
                    .feed("input_image_evaluate_y", inputTensorY)
                    .feed("input_image_evaluate_pbpr", inputTensorPbPr)
                    .fetch("test_sr_evaluator_i1_b0_g/target")
                    .run()

            val upscaledTensor = tensors[0] as TFloat32
            val upscaledShape = upscaledTensor.shape().asArray()
            val outputImage = result ?: colorBuffer(upscaledShape[2].toInt(), upscaledShape[1].toInt(), format = ColorFormat.RGB, type = ColorType.FLOAT32)
            upscaledTensor.copyTo(outputImage)
            upscaledTensor.close()
            outputImage
        } ?: error("no session")


        return if (octaves == 1) {
            outputImage
        } else {
            val finalResult = upscale(outputImage, octaves = octaves - 1, prescaleFilter = prescaleFilter)
            outputImage.destroy()
            finalResult
        }
    }

    companion object {
        /**
         * load instance of [ImageUpscaler]
         */
        fun load(): ImageUpscaler {
            val bytes = fetchORMLModel(
                "FALSR-A-1.0",
                "639cd2ea510990fa58855a7a15bd1ea0d8756b6e6ffe7a980d0427f61f2fb4a1"
            )
            val g = Graph()
            g.importGraphDef(GraphDef.parseFrom(bytes))
            return ImageUpscaler(g)
        }
    }
}