import org.openrndr.draw.ColorBuffer
import org.openrndr.draw.ColorFormat
import org.openrndr.draw.ColorType
import org.openrndr.draw.colorBuffer
import org.openrndr.extra.tensorflow.copyTo
import org.openrndr.orml.utils.fetchORMLModel
import org.tensorflow.EagerSession
import org.tensorflow.Graph
import org.tensorflow.Session
import org.tensorflow.ndarray.Shape
import org.tensorflow.ndarray.buffer.DataBuffers
import org.tensorflow.op.Ops
import org.tensorflow.op.math.Mean
import org.tensorflow.proto.framework.ConfigProto
import org.tensorflow.proto.framework.GraphDef
import org.tensorflow.types.TFloat32
import kotlin.random.Random

enum class MobileStyleGANModel {
    FLOAT32
}

class MobileStyleGAN(
    sGraph: Graph,
    mGraph: Graph,
    val model: MobileStyleGANModel
) {
    val configBuilder = ConfigProto.newBuilder().apply {
        setGpuOptions(this.gpuOptionsBuilder.setAllowGrowth(true).build())
    }
    val sSession = Session(sGraph, configBuilder.build())
    val mSession = Session(mGraph, configBuilder.build())

    fun styleMean(n: Int = 4096): FloatArray {
        val random = FloatArray(n * 512) {
            Random.nextFloat()
        }
        val r = mSession.runner()
        val floatData = DataBuffers.of(*random)
        r.feed("var", TFloat32.tensorOf(Shape.of(n.toLong(), 512), floatData))
        r.fetch("Identity")
        val res = r.run()
        println(res[0].shape().asArray().joinToString(","))

        val stylesTensor = res[0] as TFloat32

        val session = EagerSession.options().async(false).build()
        val tf = Ops.create(session)
        val styles = tf.constant(stylesTensor)
        val mean = tf.math.mean(styles, tf.constant(0), Mean.keepDims(true))

        val result = DataBuffers.ofFloats(512)
        mean.asTensor().read(result)
        val resultArray = FloatArray(512)
        result.read(resultArray)

        session.close()

        return resultArray
    }

    fun varToStyle(vars: FloatArray): FloatArray {
        val r = mSession.runner()
        val floatData = DataBuffers.of(*vars)
        r.feed("var", TFloat32.tensorOf(Shape.of(1, 512), floatData))
        r.fetch("Identity")
        val res = r.run()
        println(res[0].shape().asArray().joinToString(","))

        val result = DataBuffers.ofFloats(512)
        val style = res[0] as TFloat32
        style.read(result)
        val resultArray = FloatArray(512)
        result.read(resultArray)
        return resultArray
    }

    fun styleToImage(style: FloatArray): ColorBuffer {
        val r = sSession.runner()
        val floatData = DataBuffers.of(*style)
        r.feed("style", TFloat32.tensorOf(Shape.of(512), floatData))
        r.fetch("Identity")
        val res = r.run()
        val imageTensor = res[0] as TFloat32
        println(imageTensor.shape().asArray().joinToString(", "))

        val session = EagerSession.options().async(false).build()
        val tf = Ops.create(session)

        val input = tf.constant(imageTensor)
        val transposed = tf.linalg.transpose(input, tf.constant(intArrayOf(0, 2, 3, 1)))


        println(transposed.shape().asArray().joinToString(", "))
        val resultImage = colorBuffer(1024, 1024, format = ColorFormat.RGB, type = ColorType.FLOAT32)
        transposed.asTensor().copyTo(resultImage)

        session.close()
        return resultImage
    }


//    fun generate(vector: FloatArray) {
//        val r = session.runner()
//        val floatData = DataBuffers.of(*vector)
//        r.feed("var", TFloat32.tensorOf(Shape.of(1, 512), floatData))
//        r.fetch("Identity")
//        val res = r.run()
//        println( res[0].shape().asArray().joinToString(","))
//
//    }

    companion object {
        fun load(model: MobileStyleGANModel): MobileStyleGAN {
            val mModelBytes = fetchORMLModel(
                "mobile-stylegan-m-float32-1.0",
                "155dc06a68a69eb0162c9ef04bf989d07ce6b43e48bba107bd74d2e0d410f7f7"
            )
            val mModel = Graph()
            mModel.importGraphDef(GraphDef.parseFrom(mModelBytes))

            val sModelBytes = fetchORMLModel(
                "mobile-stylegan-s-float32-1.0",
                "632010b573ff684d2345832b98b5d175aeba7dfcf9d377f0f51475b0caf9e407"
            )
            val sModel = Graph()
            sModel.importGraphDef(GraphDef.parseFrom(sModelBytes))

            return MobileStyleGAN(sGraph = sModel, mGraph = mModel, model)

        }
    }

}