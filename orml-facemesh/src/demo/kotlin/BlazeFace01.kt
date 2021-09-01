
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.colorBuffer
import org.openrndr.draw.rectangleFormat
import org.openrndr.ffmpeg.loadVideoDevice
import org.openrndr.orml.facemesh.BlazeFace
import org.openrndr.shape.IntRectangle
import org.openrndr.shape.Rectangle

fun main() {
    application {
        configure {
            width = 640
            height = 640
        }
        program {
            val video = loadVideoDevice()
            video.play()
            val squareImage = colorBuffer(640, 640)
            video.newFrame.listen {
                it.frame.copyTo(squareImage,
                    sourceRectangle = IntRectangle(0, 0, it.frame.width, it.frame.height),
                    targetRectangle = IntRectangle(0, squareImage.height -  (squareImage.height-480)/2, it.frame.width, -it.frame.height))
            }

            val bf = BlazeFace.load()
            extend {
                video.draw(drawer, blind = true)
                val rectangles = bf.detectFaces(squareImage)
                drawer.image(squareImage)
                drawer.image(bf.inputImage)

                for (rectangle in rectangles) {
                    val s = 640.0
                    val r = Rectangle(rectangle.area.corner * s, rectangle.area.width * s, rectangle.area.height*s)
                    drawer.fill = null
                    drawer.stroke = ColorRGBa.PINK
                    drawer.rectangle(r)
                }
            }
        }
    }
}