import org.openrndr.application
import org.openrndr.orml.bodypix.BodyPix
import org.tensorflow.TensorFlow
import org.tensorflow.ndarray.Shape
import org.tensorflow.types.TFloat32

fun main() = application {
    program {
        val bodyPix = BodyPix.load()

        val inputTensor = TFloat32.tensorOf(Shape.of(1, 720, 1280, 3))
        println(TensorFlow.version())

        extend {
            val result = bodyPix.adjustFunction.call(inputTensor)
            result.close()
            bodyPix.infer(inputTensor)
        }
    }
}