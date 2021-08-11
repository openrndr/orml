import org.openrndr.application
import org.openrndr.draw.loadImage
import org.openrndr.extensions.Screenshots
import org.openrndr.orml.styletransfer.StyleEncoder
import org.openrndr.orml.styletransfer.StyleTransformer

fun main() = application {
    program {
        val encoder = StyleEncoder.load()
        val transformer = StyleTransformer.load()

        val contentImage = loadImage("demo-data/images/image-003.jpg")
        val styleImage = loadImage("demo-data/images/style-003.jpg")
        val styleVector = encoder.encodeStyle(styleImage)
        extend(Screenshots())
        extend {
            val transformed = transformer.transformStyle(contentImage, styleVector)
            drawer.image(transformed)
        }
    }
}