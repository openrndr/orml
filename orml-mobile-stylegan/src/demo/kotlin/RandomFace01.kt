import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.tint
import kotlin.random.Random

fun main() {

    val styleGan = MobileStyleGAN.load(MobileStyleGANModel.FLOAT32)


    application {
        configure {
            width = 1024
            height = 1024

        }

        program {
            //val vector = FloatArray(512) { 0.0f }
            //styleGan.generate(vector)

            //println(mean.joinToString(", "))

            extend {
                //val mean = styleGan.styleMean()
                val vector = FloatArray(512) { Random.nextFloat()*2.0f - 1.0f }
                val style = styleGan.varToStyle(vector)
                val image = styleGan.styleToImage(style)
                drawer.drawStyle.colorMatrix = tint(ColorRGBa.WHITE.shade(40.0))
                drawer.image(image)
                image.destroy()
            }

        }

    }
}