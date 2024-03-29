import org.openrndr.application
import org.openrndr.draw.loadImage
import org.openrndr.extensions.Screenshots
import org.openrndr.ffmpeg.MP4Profile
import org.openrndr.ffmpeg.ScreenRecorder
import org.openrndr.orml.styletransfer.StyleEncoder
import org.openrndr.orml.styletransfer.StyleTransformer

fun main() = application {
    program {
        val encoder = StyleEncoder.load()
        val transformer = StyleTransformer.load()

        val contentImage = loadImage("demo-data/images/image-001.png")
        val styleImage0 = loadImage("demo-data/images/style-003.jpg")
        val styleImage1 = loadImage("demo-data/images/style-001.jpg")
        val styleVector0 = encoder.encodeStyle(styleImage0)
        val styleVector1 = encoder.encodeStyle(styleImage1)
        extend {
            val f = (mouse.position.y / height).toFloat()
            val styleVector = (styleVector0 zip styleVector1).map {
                it.first * f + it.second * (1.0f-f)
            }.toFloatArray()

            val transformed = transformer.transformStyle(contentImage, styleVector)
            drawer.image(transformed)
        }
    }
}