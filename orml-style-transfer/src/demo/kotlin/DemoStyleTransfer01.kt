import org.openrndr.application
import org.openrndr.draw.colorBuffer
import org.openrndr.ffmpeg.loadVideoDevice
import org.openrndr.orml.styletransfer.StyleEncoder
import org.openrndr.orml.styletransfer.StyleTransformer
import org.openrndr.shape.IntRectangle

fun main() = application {
    program {
        val encoder = StyleEncoder.load()
        val transformer = StyleTransformer.load()

        val video = loadVideoDevice(width = 640, height = 480)
        video.play()

        val videoFrame = colorBuffer(video.width, video.height)
        video.newFrame.listen {
            it.frame.copyTo(videoFrame,
                sourceRectangle = IntRectangle(0, 0, it.frame.width, it.frame.height),
                targetRectangle = IntRectangle(0, videoFrame.height - (videoFrame.height - 480) / 2, it.frame.width, -it.frame.height))
        }

        var style = FloatArray(0)
        extend {
            video.draw(drawer)
            style = encoder.encodeStyle(videoFrame)
            val transformed = transformer.transformStyle(videoFrame, style)
            drawer.image(transformed)
        }
    }
}