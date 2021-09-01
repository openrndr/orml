import org.openrndr.color.ColorRGBa
import org.openrndr.draw.*
import org.openrndr.extra.tensorflow.arrays.get
import org.openrndr.extra.tensorflow.copyTo
import org.openrndr.extra.tensorflow.toFloatArray2D
import org.openrndr.math.Vector2
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
import kotlin.math.max

class Landmark(val x: Double, val y: Double, val z: Double, val w1: Double, val imagePosition: Vector2)

class BlazePoseLandmarks(val graph: Graph, val landmarkCount: Int) {
    private val inputTensor = TFloat32.tensorOf(Shape.of(1, 256, 256, 3))
    private val inputImage = colorBuffer(256, 256, format = ColorFormat.RGB, type = ColorType.FLOAT32)
    private val inputImageFlipped = inputImage.createEquivalent()
    private val multiplyAdd = MultiplyAdd()
    val landmarkImage = renderTarget(256, 256) {
        colorBuffer()
    }

    private val roiExtractor = RoiExtractor()

    var session: Session? = null
    fun start() {
        session = Session(graph)
    }

    fun close() {
        session?.close()
    }

    fun extract(drawer: Drawer, region: Region, originalImage: ColorBuffer): List<Landmark> {

        computeRoi(region)
        drawer.isolatedWithTarget(landmarkImage) {
            roiExtractor.extractRoi(drawer, originalImage, region)
        }


        val rotatedImage = landmarkImage.colorBuffer(0)

        val imageLongest = max(rotatedImage.width, rotatedImage.height)
        val sourceRect = IntRectangle(0, 0, rotatedImage.width, rotatedImage.height)
        val targetRect = IntRectangle(0, 0, ((rotatedImage.width / imageLongest.toDouble()) * 256.0).toInt(), ((rotatedImage.height / imageLongest.toDouble()) * 256.0).toInt())
        inputImage.fill(ColorRGBa.BLACK)

        rotatedImage.copyTo(inputImage, sourceRectangle = sourceRect, targetRectangle = targetRect, filter = MagnifyingFilter.LINEAR)
        inputImage.copyTo(inputImageFlipped, sourceRectangle = IntRectangle(0, 0, inputImage.width, inputImage.height),targetRectangle = IntRectangle(0, inputImage.height, inputImage.width, -inputImage.height))

        multiplyAdd.scale = Vector4.ONE * 2.0
        multiplyAdd.offset = Vector4.ONE * -1.0
        multiplyAdd.apply(inputImageFlipped, inputImageFlipped)
        multiplyAdd.apply(inputImage, inputImage)
        inputImage.copyTo(inputImageFlipped, sourceRectangle = IntRectangle(0, 0, inputImage.width, inputImage.height), targetRectangle = IntRectangle(0, inputImage.height, inputImage.width, -inputImage.height))
        inputImageFlipped.copyTo(inputTensor)
        if (session == null) {
            start()
        }

        val landmarks = session?.let {
            val runner = it.runner()
            val tensors = runner.feed("input", inputTensor)
                    .fetch("Identity_1")
                    .fetch("Identity_2")
                    .run()

            val identity2 = tensors[1] as TFloat32
            val id1f = identity2.toFloatArray2D()
            val landmarks = (0 until landmarkCount).map { i ->
                val x = (id1f[0, i * 4].toDouble() / 256.0)
                val y = (id1f[0, i * 4 + 1].toDouble() / 256.0)
                val z = id1f[0, i * 4 + 2].toDouble()
                val score = id1f[0, i * 4 + 3].toDouble()

                val imagePosition = (region.transform * Vector2(x, y).xy01).div.xy * 640.0

                Landmark(x, y, z, score, imagePosition)
            }
            identity2.close()
            landmarks
        } ?: error("no session")
        return landmarks
    }

    companion object {
        fun fullBody(): BlazePoseLandmarks {
            val bytes = URL(resourceUrl("/tfmodels/full_pose_landmark_39p_float32.pb")).readBytes()
            val g = Graph()
            g.importGraphDef(GraphDef.parseFrom(bytes))
            g.operations().forEach {
                println(it.name())
            }
            return BlazePoseLandmarks(g, 39)
        }

        fun upperBody(): BlazePoseLandmarks {
            val bytes = URL(resourceUrl("/tfmodels/upperbody_landmark_float32.pb")).readBytes()
            val g = Graph()
            g.importGraphDef(GraphDef.parseFrom(bytes))
            g.operations().forEach {
                println(it.name())
            }
            return BlazePoseLandmarks(g, 31)
        }
    }
}