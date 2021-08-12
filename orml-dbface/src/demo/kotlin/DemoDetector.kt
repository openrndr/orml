import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.loadImage
import org.openrndr.extensions.Screenshots
import org.openrndr.math.Vector2
import org.openrndr.orml.dbface.DBFaceDetector
import org.openrndr.orml.ssd.map
import org.openrndr.shape.Rectangle

fun main() {
    application {
        program {
            val dbface = DBFaceDetector.load()
            val image = loadImage("demo-data/images/image-005.jpg")
            extend(Screenshots())
            extend {
                // center image
                drawer.translate((drawer.bounds.position(1.0, 1.0) - image.bounds.position(1.0, 1.0)) / 2.0)
                drawer.image(image)
                val rectangles = dbface.detectFaces(image)
                drawer.fill = null
                drawer.stroke = ColorRGBa.PINK
                for (r in rectangles) {
                    drawer.rectangle(r.area.map(Rectangle(0.0, 0.0, 1.0, 1.0), image.bounds))
                    for (l in r.landmarks) {
                        drawer.circle(l.map(Rectangle(0.0, 0.0, 1.0, 1.0), image.bounds), 10.0)
                    }
                }
            }
        }
    }
}

private operator fun Rectangle.times(scale: Vector2): Rectangle {
    return Rectangle(x * scale.x, y * scale.y, width * scale.x, height * scale.y)
}
