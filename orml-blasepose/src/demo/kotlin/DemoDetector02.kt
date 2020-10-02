import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.colorBuffer
import org.openrndr.draw.isolated
import org.openrndr.draw.isolatedWithTarget
import org.openrndr.draw.renderTarget
import org.openrndr.ffmpeg.loadVideoDevice

fun main() = application {


    program {
        val video = loadVideoDevice()
        video.play()
        val detector = BlasePoseDetector.load()
        val landmarks = BlasePoseLandmarks.upperBody()
        val videoImage = colorBuffer(video.width, video.height)
        val roiExtractor = RoiExtractor()
        val landmarkImage = renderTarget(256, 256) {
            colorBuffer()
        }

        video.newFrame.listen {
            it.frame.copyTo(videoImage)
        }
        extend {
            video.draw(drawer, blind = false)
            val regions = detector.detect(videoImage)

            for (region in regions) {
                computeRoi(region)
                drawer.fill = null
                drawer.rectangle(region.rectangle.x * 640.0, region.rectangle.y * 640.0, region.rectangle.width * 640.0, region.rectangle.height * 640.0)


                println(region.keys.size)
                for ((index, key) in region.keys.withIndex()) {
                    if (index == 0) {
                        // mid hip center?
                        drawer.fill = ColorRGBa.PINK
                    }
                    if (index == 1) {
                        drawer.fill = ColorRGBa.RED
                    }
                    if (index == 2) {
                        drawer.fill = ColorRGBa.BLUE
                    }
                    drawer.circle(key * 640.0, 10.0)

                }
                drawer.isolated {
                    drawer.strokeWeight = 2.0
                    drawer.stroke = ColorRGBa.GREEN
                    drawer.lineSegment(region.keys[0]*640.0, region.keys[1]*640.0)
                }

                drawer.fill = ColorRGBa.GREEN
                println(region.rotation)



                for (coord in region.roi_coord) {

//                    println(coord)
                    drawer.circle(coord * 640.0, 10.0)
                }
                drawer.lineSegment(region.roi_coord[0] * 640.0, region.roi_coord[1] * 640.0)
                drawer.lineSegment(region.roi_coord[1] * 640.0, region.roi_coord[2] * 640.0)
                drawer.lineSegment(region.roi_coord[2] * 640.0, region.roi_coord[3] * 640.0)
                drawer.lineSegment(region.roi_coord[3] * 640.0, region.roi_coord[0] * 640.0)

                drawer.isolatedWithTarget(landmarkImage) {
                    roiExtractor.extractRoi(drawer, videoImage, region)
                }
                drawer.image(landmarkImage.colorBuffer(0))
                val lms = landmarks.extract(landmarkImage.colorBuffer(0))
                for(lm in lms) {
                    drawer.circle(lm.x, lm.y, 10.0)
                }
            }
        }
    }
}