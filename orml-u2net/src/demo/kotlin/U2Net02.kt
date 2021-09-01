import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.colorBuffer
import org.openrndr.draw.grayscale
import org.openrndr.draw.loadImage
import org.openrndr.extensions.Screenshots
import org.openrndr.ffmpeg.VideoPlayerConfiguration
import org.openrndr.ffmpeg.loadVideoDevice
import org.openrndr.orml.u2net.U2Net
import org.openrndr.shape.IntRectangle

fun main() {
    application {
        configure {
            width = 1280
            height = 480
        }
        program {
            val u2= U2Net.load()
            val vc = VideoPlayerConfiguration()
            vc.videoFrameQueueSize = 2
            vc.allowFrameSkipping = true
            vc.useHardwareDecoding = false

            val video = loadVideoDevice(width = 640, height = 480, configuration = vc)
            video.play()
            val extracted = colorBuffer(video.width, video.height)
            val videoFrame = colorBuffer(video.width, video.height)
            video.newFrame.listen {
                it.frame.copyTo(videoFrame,
                    sourceRectangle = IntRectangle(0, 0, it.frame.width, it.frame.height),
                    targetRectangle = IntRectangle(0, videoFrame.height - (videoFrame.height - 480) / 2, it.frame.width, -it.frame.height))
            }
            extend {
                video.draw(drawer, blind = true)
                drawer.clear(ColorRGBa.PINK)
                u2.removeBackground(videoFrame, extracted)
                drawer.image(videoFrame)
                drawer.image(extracted, 640.0, 0.0)
            }
        }
    }
}