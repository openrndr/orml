import org.openrndr.application
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
            val image = loadImage("demo-data/images/image-002.jpg")
            val result = u2.extract(image)

            extend {
                drawer.image(image)
                drawer.drawStyle.colorMatrix = grayscale(1.0, 0.0, 0.0)
                drawer.image(result, 640.0, 480.0, 640.0, -480.0)
            }
        }
    }
}