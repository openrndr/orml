import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.*
import org.openrndr.extensions.Screenshots
import org.openrndr.extra.tensorflow.copyTo
import org.openrndr.ffmpeg.PlayMode
import org.openrndr.ffmpeg.VideoPlayerConfiguration
import org.openrndr.ffmpeg.loadVideo
import org.openrndr.orml.bodypix.BodyPix
import org.openrndr.orml.bodypix.BodyPixArchitecture
import org.openrndr.orml.bodypix.toInputResolutionHeightAndWidth
import org.openrndr.shape.IntRectangle
import org.tensorflow.ndarray.Shape
import org.tensorflow.types.TFloat32

fun main() = application {

    configure {
        width = 1080 / 2
        height = 1920 / 2
    }

    program {
        val bodyPix = BodyPix.load(architecture = BodyPixArchitecture.MOBILENET)
        val inputTensor = TFloat32.tensorOf(Shape.of(1, 1920 / 2, 1080 / 2, 3))

        val vc = VideoPlayerConfiguration().apply {
            allowFrameSkipping = false
        }
        val video = loadVideo("demo-data/replace.mp4", PlayMode.VIDEO, vc)
        video.play()

        val canvasWidth = 1080
        val canvasHeight = 1920

        val outputStride = 16
        val cameraScale = 2

        val (inputHeight, inputWidth) = toInputResolutionHeightAndWidth(
            1.0,
            outputStride,
            canvasHeight / cameraScale,
            canvasWidth / cameraScale
        )

        val segmentationWidth = (inputWidth / outputStride) + 1
        val segmentationHeight = (inputHeight / outputStride) + 1

        println("segmentation size $segmentationWidth $segmentationHeight")

        val segmentationImage = colorBuffer(34, 60, type = ColorType.FLOAT32, format = ColorFormat.R)
        val segmentationFlipped = colorBuffer(34, 60, type = ColorType.FLOAT32, format = ColorFormat.R)
        val downScaleVideo = renderTarget(1080 / 2, 1920 / 2) {
            colorBuffer(type = ColorType.FLOAT32, format = ColorFormat.RGB)
        }
        val inputFlipped = colorBuffer(1080 / 2, 1920 / 2, type = ColorType.FLOAT32, format = ColorFormat.RGB)

        extend(Screenshots())
        extend {
            drawer.isolatedWithTarget(downScaleVideo) {
                drawer.ortho(downScaleVideo)
                video.draw(drawer, 0.0, 0.0, 1080.0 / 2.0, 1920.0 / 2)
            }
            drawer.image(downScaleVideo.colorBuffer(0))
            downScaleVideo.colorBuffer(0).copyTo(
                inputFlipped,
                sourceRectangle = IntRectangle(0, 0, downScaleVideo.width, downScaleVideo.height),
                targetRectangle = IntRectangle(0, 1920 / 2, 1080 / 2, -1920 / 2)
            )
            inputFlipped.copyTo(inputTensor)

            val result = bodyPix.infer(inputTensor)
            result.segmentation.copyTo(segmentationImage)
            segmentationImage.copyTo(
                segmentationFlipped,
                sourceRectangle = IntRectangle(0, 0, segmentationImage.width, segmentationImage.height),
                targetRectangle = IntRectangle(
                    0,
                    segmentationImage.height,
                    segmentationImage.width,
                    -segmentationImage.height
                )
            )
            drawer.drawStyle.colorMatrix = tint(ColorRGBa.WHITE.opacify(0.5))
            //drawer.image(segmentationFlipped,  (width - (34 * (width/34)).toDouble())/2.0 , 0.0, (34 * (width/34)).toDouble(), height*1.0)
            drawer.image(segmentationFlipped, 0.0, 0.0, width * 1.0, height * 1.0)
        }
    }
}