import org.openrndr.application
import org.openrndr.draw.loadFont
import org.openrndr.draw.loadImage
import org.openrndr.extensions.Screenshots
import org.openrndr.orml.imageclassifier.ImageClassifier
import org.openrndr.orml.imageclassifier.imagenetLabels

fun main() {
    application {
        program {
            val classifier = ImageClassifier.load()
            val image = loadImage("demo-data/images/image-001.png")
            val scores = classifier.classify(image)
            val label = (scores zip imagenetLabels).maxByOrNull { it.first } ?: error("no label")
            extend(Screenshots())
            extend {
                drawer.fontMap = loadFont("demo-data/fonts/default.ttf", 24.0)
                drawer.image(image)
                drawer.text("This is a ${label.second} (${String.format("%.2f", label.first*100.0)}% confident)", 20.0, 30.0)
            }
        }
    }
}