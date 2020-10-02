import org.openrndr.color.ColorRGBa
import org.openrndr.draw.*
import org.openrndr.extra.tensorflow.arrays.get
import org.openrndr.extra.tensorflow.copyTo
import org.openrndr.extra.tensorflow.toFloatArray3D
import org.openrndr.math.Vector2
import org.openrndr.math.Vector3
import org.openrndr.math.Vector4
import org.openrndr.resourceUrl
import org.openrndr.shape.IntRectangle
import org.openrndr.shape.Rectangle
import org.tensorflow.Graph
import org.tensorflow.Session
import org.tensorflow.Tensor
import org.tensorflow.ndarray.Shape
import org.tensorflow.proto.framework.GraphDef
import org.tensorflow.types.TFloat32
import java.net.URL
import kotlin.math.*

fun normalizeRadians(angle: Double): Double {
    return angle - 2 * Math.PI * floor((angle - (-Math.PI)) / (2 * Math.PI));
}

data class Region(val score: Double, val rectangle: Rectangle, val keys: List<Vector2>) {
    val rotation = 0.0 // by lazy { rotation() }

    private fun rotation(): Double {
        val region = this
        val x0 = region.keys[0].x;
        val y0 = region.keys[0].y;
        val x1 = (region.rectangle.center.x) * 0.5;
        val y1 = (region.rectangle.center.y) * 0.5;

        val targetAngle = Math.PI * 0.5;
        val rotation = targetAngle - atan2(-(y1 - y0), x1 - x0);
        return normalizeRadians(rotation);
    }

    var roi_center = Vector2.ZERO
    var roi_size = Vector2.ZERO
    var roi_coord = Array<Vector2>(0){ Vector2.ZERO}
}

val kMidHipCenter      = 0;
val kFullBodySizeRot   = 1;
val kMidShoulderCenter = 2;
val kUpperBodySizeRot  = 3;
val kPoseDetectKeyNum  = 2;

fun computeRoi(region: Region) {
    val input_img_w = 128
    val input_img_h = 128
    val center = Vector2(region.keys[kMidHipCenter].x,  region.keys[kMidHipCenter].y)
    val scale = Vector2(region.keys[kFullBodySizeRot].x,  region.keys[kFullBodySizeRot].y)

    val d = (scale-center) * 1.5
    val dp = d.perpendicular()

    region.roi_coord = Array(4) { Vector2.ZERO }
    region.roi_coord[0] = center+d+dp/2.0
    region.roi_coord[1] = center+d-dp/2.0
    region.roi_coord[2] = center-d-dp/2.0
    region.roi_coord[3] = center-d+dp/2.0

}

fun rot_vec(vec:Vector2, rotation:Double) : Vector2
{
    val sx = vec.x;
    val sy = vec.y;
    return Vector2(sx * Math.cos(rotation) - sy * Math.sin(rotation),
    sx * Math.sin(rotation) + sy * Math.cos(rotation))
}

private fun intersectionOverUnion(region0: Rectangle, region1: Rectangle): Double {
    val sx0 = region0.corner.x
    val sy0 = region0.corner.y
    val ex0 = region0.corner.x + region0.width
    val ey0 = region0.corner.y + region0.height
    val sx1 = region1.corner.x
    val sy1 = region1.corner.y
    val ex1 = region1.corner.x + region1.width
    val ey1 = region1.corner.y + region1.height

    val xmin0 = min(sx0, ex0)
    val ymin0 = min(sy0, ey0)
    val xmax0 = max(sx0, ex0)
    val ymax0 = max(sy0, ey0)
    val xmin1 = min(sx1, ex1)
    val ymin1 = min(sy1, ey1)
    val xmax1 = max(sx1, ex1)
    val ymax1 = max(sy1, ey1)

    val area0 = (ymax0 - ymin0) * (xmax0 - xmin0)
    val area1 = (ymax1 - ymin1) * (xmax1 - xmin1)
    if (area0 <= 0 || area1 <= 0) return 0.0

    val intersectXmin = max(xmin0, xmin1)
    val intersectYmin = max(ymin0, ymin1)
    val intersectXmax = min(xmax0, xmax1)
    val intersectYmax = min(ymax0, ymax1)

    val intersectArea = max(intersectYmax - intersectYmin, 0.0) *
            max(intersectXmax - intersectXmin, 0.0)

    return intersectArea / (area0 + area1 - intersectArea)
}

private fun nonMaxSuppression(input: List<Region>, threshold: Double = 0.5): List<Region> {
    val sorted = input.sortedBy { it.score }
    val result = mutableListOf<Region>()

    for (regionCandidate in sorted) {
        var ignoreCandidate = false
        for (regionNms in result) {
            val iou = intersectionOverUnion(regionCandidate.rectangle, regionNms.rectangle)
            if (iou > threshold) {
                ignoreCandidate = true
                break
            }
        }
        if (!ignoreCandidate) {
            result.add(regionCandidate)
        }
    }
    return result
}

class RoiExtractor {

    val vbo = vertexBuffer(vertexFormat {
        position(3)
        textureCoordinate(2)
    }, 6)

    fun extractRoi(drawer:Drawer, image:ColorBuffer, region:Region ) {
        vbo.put {



            write(Vector3(0.0, 0.0, 0.0))
            write(region.roi_coord[0])

            write(Vector3(256.0, 0.0, 0.0))
            write(region.roi_coord[1])

            write(Vector3(256.0, 256.0, 0.0))
            write(region.roi_coord[2])

            write(Vector3(256.0, 256.0, 0.0))
            write(region.roi_coord[2])

            write(Vector3(0.0, 256.0, 0.0))
            write(region.roi_coord[3])

            write(Vector3(0.0, 0.0, 0.0))
            write(region.roi_coord[0])
        }

        drawer.isolated {

            drawer.defaults()
            drawer.ortho(RenderTarget.active)
            drawer.shadeStyle = shadeStyle {
                fragmentTransform = """
                    vec2 ts = textureSize(p_image, 0);
                    float ar = ts.y / ts.x;
                    x_fill = texture(p_image, va_texCoord0 * vec2(1.0, ar));
                """.trimIndent()
                parameter("image", image)
            }
            drawer.vertexBuffer(vbo, DrawPrimitive.TRIANGLES)

        }
    }

}


class BlasePoseDetector(val graph: Graph) {
    val kMidHipCenter = 0;
    val kFullBodySizeRot = 1;
    val kMidShoulderCenter = 2;
    val kUpperBodySizeRot = 3;
    val kPoseDetectKeyNum = 2;

    val anchors = generateAnchors(AnchorOptions())
    val inputTensor = Tensor.of(TFloat32.DTYPE, Shape.of(1, 128, 128, 3))
    val inputImage = colorBuffer(128, 128, format = ColorFormat.RGB, type = ColorType.FLOAT32)
    val inputImageFlipped = inputImage.createEquivalent()

    var session: Session? = null
    fun start() {
        session = Session(graph)
    }

    fun close() {
        session?.close()
    }

    fun detect(image: ColorBuffer): List<Region> {
        inputImage.fill(ColorRGBa.BLACK)
        val imageLongest = max(image.width, image.height)
        val sourceRect = IntRectangle(0, 0, image.width, image.height)
        val targetRect = IntRectangle(0, 0, ((image.width / imageLongest.toDouble()) * 128.0).toInt(), ((image.height / imageLongest.toDouble()) * 128.0).toInt())
        image.copyTo(inputImage, sourceRectangle = sourceRect, targetRectangle = targetRect, filter = MagnifyingFilter.LINEAR)
        inputImage.copyTo(inputImageFlipped, targetRectangle = IntRectangle(0, inputImage.height, inputImage.width, -inputImage.height ))

        inputImageFlipped.copyTo(inputTensor)

        if (session == null) {
            start()
        }

        return session?.let {
            val runner = it.runner()
            val tensors = runner.feed("input", inputTensor)
                    .fetch("Identity")
                    .fetch("Identity_1")
                    .run()

            val logits0 = tensors[0].expect(TFloat32.DTYPE)
            val logits1 = tensors[1].expect(TFloat32.DTYPE)

            val scores_ptr = logits0.toFloatArray3D()
            val bbox_ptr = logits1.toFloatArray3D()

            val score_thresh = 0.3

            val regions = (anchors.indices).mapNotNull { i ->
                val anchor = anchors[i]
                val score0 = scores_ptr[0, i, 0].toDouble()
                val score = 1.0 / (1.0 + exp(-score0))

                if (score > score_thresh) {
                    val sx = bbox_ptr[0, i, 0].toDouble()
                    val sy = bbox_ptr[0, i, 1].toDouble()

                    val w = (bbox_ptr[0, i, 2].toDouble() / 128.0)
                    val h = (bbox_ptr[0, i, 3].toDouble() / 128.0)

                    val cx = ((sx + anchor.xCenter * 128.0) / 128.0)
                    val cy = ((sy + anchor.yCenter * 128.0) / 128.0)

                    val topleft = Vector2(cx - w * 0.5, 1.0 - (cy - h * 0.5))
                    val rectangle = Rectangle(topleft, w, h)

                    val keypoints = (0 until kPoseDetectKeyNum).map { j ->
                        val lx = (bbox_ptr[0, i, 4 + (2 * j) + 0].toDouble() + anchor.xCenter * 128.0) / 128.0
                        val ly = (bbox_ptr[0, i, 4 + (2 * j) + 1].toDouble() + anchor.yCenter * 128.0) / 128.0
                        Vector2(lx, 1.0-ly)
                    }
                    Region(score, rectangle, keypoints)
                } else {
                    null
                }
            }
            logits0.close()
            logits1.close()
            regions.sortedBy { it.score }.take(1)

        } ?: error("no session")
    }

    companion object {
        fun load(): BlasePoseDetector {
            val bytes = URL(resourceUrl("/tfmodels/full_pose_detection_float32.pb")).readBytes()
            val g = Graph()
            g.importGraphDef(GraphDef.parseFrom(bytes))
            return BlasePoseDetector(g)
        }
    }
}