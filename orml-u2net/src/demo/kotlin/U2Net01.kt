import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.colorBuffer
import org.openrndr.draw.grayscale
import org.openrndr.draw.loadImage
import org.openrndr.extensions.Screenshots
import org.openrndr.orml.u2net.U2Net

fun main() {
    application {
        configure {
            width = 1280
            height = 480
        }
        program {
            val u2= U2Net.load()
            val image = loadImage("demo-data/images/image-001.png")
            val result = u2.removeBackground(image)

            extend {
                drawer.clear(ColorRGBa.PINK)
                drawer.image(image)
                drawer.image(result, 640.0, 0.0)
            }
        }
    }
}