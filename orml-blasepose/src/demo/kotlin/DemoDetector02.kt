import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.colorBuffer
import org.openrndr.draw.isolated
import org.openrndr.draw.isolatedWithTarget
import org.openrndr.draw.renderTarget
import org.openrndr.ffmpeg.PlayMode
import org.openrndr.ffmpeg.VideoPlayerConfiguration
import org.openrndr.ffmpeg.loadVideo
import org.openrndr.ffmpeg.loadVideoDevice
import org.openrndr.math.Vector2
import org.openrndr.math.Vector4
import org.openrndr.shape.IntRectangle
import kotlin.math.max

fun main() = application {
    configure {
        width = 1000
        height = 1000
    }

    program {
        val vc = VideoPlayerConfiguration().apply {
            allowFrameSkipping = false
        }
        //val video = loadVideo("orml-blasepose/debug-data/output.mkv", PlayMode.VIDEO, configuration = vc)
        val video = loadVideoDevice()
        video.play()
        val detector = BlasePoseDetector.load()
        val landmarks = BlasePoseLandmarks.upperBody()
        val longestVideoAxis = max(video.width, video.height)
        val videoImage = colorBuffer(longestVideoAxis, longestVideoAxis)

        video.ended.listen {
            video.restart()
        }

        video.newFrame.listen {
            val xOffset = (longestVideoAxis - it.frame.width) / 2
            val yOffset = (longestVideoAxis - it.frame.height) / 2
            it.frame.copyTo(videoImage, targetRectangle = IntRectangle(xOffset, videoImage.height - yOffset, it.frame.width, -it.frame.height))
        }
        extend {
            video.draw(drawer, blind = true)
            drawer.image(videoImage)
            val regions = detector.detect(videoImage)

            for (region in regions) {
                computeRoi(region)
                drawer.fill = null
                drawer.rectangle(region.rectangle.x * 640.0, region.rectangle.y * 640.0, region.rectangle.width * 640.0, region.rectangle.height * 640.0)

                for ((index, key) in region.keys.withIndex()) {
                    if (index == 0) {
                        drawer.fill = ColorRGBa.PINK
                    }
                    if (index == 1) {
                        drawer.fill = ColorRGBa.RED
                    }
                    drawer.circle(key * 640.0, 10.0)

                }
                drawer.isolated {
                    drawer.strokeWeight = 2.0
                    drawer.stroke = ColorRGBa.GREEN
                    drawer.lineSegment(region.keys[0] * 640.0, region.keys[1] * 640.0)
                }

                drawer.fill = ColorRGBa.GREEN
                for (coord in region.roi_coord) {
                    drawer.circle(coord * 640.0, 10.0)
                }
                drawer.lineSegment(region.roi_coord[0] * 640.0, region.roi_coord[1] * 640.0)
                drawer.lineSegment(region.roi_coord[1] * 640.0, region.roi_coord[2] * 640.0)
                drawer.lineSegment(region.roi_coord[2] * 640.0, region.roi_coord[3] * 640.0)
                drawer.lineSegment(region.roi_coord[3] * 640.0, region.roi_coord[0] * 640.0)

                val lms = landmarks.extract(drawer, region,  videoImage)
                val ti = region.transform

                drawer.image(landmarks.landmarkImage.colorBuffer(0))

                for (lm in lms) {
                    drawer.fill = ColorRGBa.GREEN
                    drawer.circle(lm.imagePosition,10.0 )
                }
                drawer.defaults()
                drawer.translate(400.0, 0.0)
                drawer.image(detector.inputImage)
            }
        }
    }
}