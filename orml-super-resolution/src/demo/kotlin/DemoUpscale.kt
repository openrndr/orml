import org.openrndr.application
import org.openrndr.draw.loadImage
import org.openrndr.extensions.Screenshots
import org.openrndr.shape.Rectangle

fun main() = application {
    configure {
        width = 1280
        height = 720
    }
    program {
        val upscaler = ImageUpscaler.load()
        val image = loadImage("demo-data/images/image-003.jpg")
        val upscale = upscaler.upscale(image, octaves = 1)
        extend(Screenshots())
        extend {
            drawer.translate((width - upscale.width) / 2.0, (height - upscale.height) / 2.0)
            drawer.image(upscale)

            val x = mouse.position.x
            val sr = Rectangle(x / 2.0, 0.0, image.width - x / 2.0, image.height * 1.0)
            val tr = Rectangle(x, 0.0, (image.width - x / 2.0) * 2.0, image.height * 2.0)

            drawer.image(image, sr, tr)
            drawer.defaults()
            drawer.lineSegment(x, 0.0, x, height * 1.0)
        }
    }
}