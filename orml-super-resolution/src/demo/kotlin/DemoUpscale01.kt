import org.openrndr.application
import org.openrndr.draw.loadImage

fun main() = application {
    configure {
        width = 2560
        height = 960
    }
    program {
        val upscaler = ImageUpscaler.load()
        val image = loadImage("demo-data/images/image-004.jpg")
        val upscale = upscaler.upscale(image, octaves = 1)
        extend {
            drawer.image(upscale)
            drawer.image(image, 1280.0, 0.0, 1280.0, 960.0)
        }
    }
}

