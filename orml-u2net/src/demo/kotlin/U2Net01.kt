import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.loadImage
import org.openrndr.extensions.Screenshots
import org.openrndr.orml.u2net.U2Net
import org.openrndr.shape.Rectangle
import java.io.File

fun main() {
    application {
        configure {
            width = 640
            height = 480
        }
        program {
            val u2= U2Net.load()
            val image = loadImage("demo-data/images/image-001.png")
            val result = u2.removeBackground(image)

            extend(Screenshots())
            extend {
                drawer.clear(ColorRGBa.PINK)
                val x = mouse.position.x
                val r0 = Rectangle(0.0, 0.0, x, result.height*1.0)
                drawer.image(image, r0, r0)

                val r1 = Rectangle(x, 0.0, result.width - x, result.height*1.0)
                drawer.image(result, r1, r1)
                drawer.lineSegment(x, 0.0, x, height * 1.0)
            }
        }
    }
}