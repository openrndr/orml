import org.openrndr.application
import org.openrndr.draw.loadImage
import org.openrndr.extensions.Screenshots
import org.openrndr.orml.imageclassifier.ImageClassifier
import kotlin.math.sqrt

fun main() {
    application {
        program {
            val classifier = ImageClassifier.load()
            val image = loadImage("demo-data/images/image-001.png")
            // find the image embedding
            val embedding = classifier.embed(image)
            extend(Screenshots())
            extend {
                drawer.image(image)
                // center the grid
                drawer.translate((width - 31 * 15.0) / 2.0, (height - 31 * 15.0) / 2.0)
                // draw the grid
                for (y in 0 until 32) {
                    for (x in 0 until 32) {
                        val idx = y * 32 + x
                        if (idx < embedding.size) {
                            val radius = sqrt(embedding[idx] * 1.0) * 10.0
                            drawer.circle(x * 15.0, y * 15.0, radius)
                        }
                    }
                }
            }
        }
    }
}