import org.openrndr.application
import org.openrndr.draw.colorBuffer
import org.openrndr.orml.imageclassifier.ImageClassifier

fun main() {
    application {
        program {
            val classifier = ImageClassifier.load()
            val image = colorBuffer(224,224)
            val embedding = classifier.embed(image)

            println(embedding.joinToString(", "))

            extend {

            }
        }
    }
}