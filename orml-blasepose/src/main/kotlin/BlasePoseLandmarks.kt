import org.openrndr.color.ColorRGBa
import org.openrndr.draw.*
import org.openrndr.extra.tensorflow.arrays.get
import org.openrndr.extra.tensorflow.copyTo
import org.openrndr.extra.tensorflow.toFloatArray2D
import org.openrndr.math.Vector4
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

class Landmark(val x: Double, val y: Double, val z: Double, val w1: Double) {

}

class BlasePoseLandmarks(val graph: Graph, val landmarkCount: Int) {
    val inputTensor = Tensor.of(TFloat32.DTYPE, Shape.of(1, 256, 256, 3))
    val inputImage = colorBuffer(256, 256, format = ColorFormat.RGB, type = ColorType.FLOAT32)
    val inputImageFlipped = inputImage.createEquivalent()
    val multiplyAdd = MultiplyAdd()

    var session: Session? = null
    fun start() {
        session = Session(graph)
    }

    fun close() {
        session?.close()
    }

    fun extract(image: ColorBuffer): List<Landmark> {
        val imageLongest = max(image.width, image.height)
        val sourceRect = IntRectangle(0, 0, image.width, image.height)
        val targetRect = IntRectangle(0, 0, ((image.width / imageLongest.toDouble()) * 256.0).toInt(), ((image.height / imageLongest.toDouble()) * 256.0).toInt())
        inputImage.fill(ColorRGBa.BLACK)

        image.copyTo(inputImage, sourceRectangle = sourceRect, targetRectangle = targetRect, filter = MagnifyingFilter.LINEAR)
        inputImage.copyTo(inputImageFlipped, targetRectangle = IntRectangle(0, inputImage.height, inputImage.width, -inputImage.height ))


        multiplyAdd.scale = Vector4.ONE * 2.0
        multiplyAdd.offset = Vector4.ONE * -1.0
        multiplyAdd.apply(inputImage, inputImage)
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

            val identity2 = tensors[1].expect(TFloat32.DTYPE)
            val id1f = identity2.toFloatArray2D()
            val landmarks = (0 until landmarkCount).map { i ->
                val a = (id1f[0, i * 4].toDouble() / 256.0) * imageLongest
                val b = (id1f[0, i * 4 + 1].toDouble() / 256.0) * imageLongest
                val c = id1f[0, i * 4 + 2].toDouble()
                val d = id1f[0, i * 4 + 3].toDouble()
                Landmark(a, b, c, d)
            }
            identity2.close()
            landmarks
        } ?: error("no session")
        return landmarks
    }

    companion object {
        fun fullBody(): BlasePoseLandmarks {
            val bytes = URL(resourceUrl("/tfmodels/full_pose_landmark_39p_float32.pb")).readBytes()
            val g = Graph()
            g.importGraphDef(GraphDef.parseFrom(bytes))
            g.operations().forEach {
                println(it.name())
            }
            return BlasePoseLandmarks(g, 39)
        }

        fun upperBody(): BlasePoseLandmarks {
            val bytes = URL(resourceUrl("/tfmodels/upperbody_landmark_float32.pb")).readBytes()
            val g = Graph()
            g.importGraphDef(GraphDef.parseFrom(bytes))
            g.operations().forEach {
                println(it.name())
            }
            return BlasePoseLandmarks(g, 31)
        }
    }
}