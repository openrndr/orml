import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.ColorType
import org.openrndr.draw.colorBuffer
import org.openrndr.extra.tensorflow.arrays.get
import org.openrndr.extra.tensorflow.copyTo
import org.openrndr.extra.tensorflow.toFloatArray3D
import org.openrndr.ffmpeg.loadVideoDevice
import org.tensorflow.Tensor
import org.tensorflow.ndarray.Shape
import org.tensorflow.types.TFloat32
import kotlin.math.abs
import kotlin.math.max

fun main() = application {
    program {
        val video = loadVideoDevice()
        video.play()

        val cb = colorBuffer(256, 256, type = ColorType.FLOAT32)
        cb.fill(ColorRGBa(0.11, 0.11, 0.11))
        val tensor = Tensor.of(TFloat32.DTYPE, Shape.of(256, 256, 4))
        cb.copyTo(tensor)
        val f = tensor.toFloatArray3D()
        val r = f[0, 0, 0]

        val detector = BlasePoseLandmarks.upperBody()
        detector.extract(cb)


        val videoImage = colorBuffer(video.width, video.height)

        video.newFrame.listen {
            it.frame.copyTo(videoImage)
        }

        extend {
            video.draw(drawer, blind = false)
            val landmarks = detector.extract(videoImage)
            for (landmark in landmarks) {
                drawer.circle(landmark.x, landmark.y, 10.0)
            }
        }
    }
}