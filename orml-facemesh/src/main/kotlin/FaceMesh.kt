package org.openrndr.orml.facemesh

import org.openrndr.draw.*
import org.openrndr.extra.tensorflow.arrays.get
import org.openrndr.extra.tensorflow.copyTo
import org.openrndr.extra.tensorflow.summary
import org.openrndr.extra.tensorflow.toFloatArray2D
import org.openrndr.math.Matrix44
import org.openrndr.math.Vector2
import org.openrndr.math.Vector3
import org.openrndr.orml.ssd.SSDRectangle
import org.openrndr.orml.utils.fetchORMLModel
import org.openrndr.resourceUrl
import org.openrndr.shape.IntRectangle
import org.openrndr.shape.ShapeContour
import org.tensorflow.Graph
import org.tensorflow.Session
import org.tensorflow.Tensor
import org.tensorflow.ndarray.Shape
import org.tensorflow.proto.framework.GraphDef
import org.tensorflow.types.TFloat32
import java.net.URL

/**
 * A face landmark
 * @param regionPosition the position as given when processing the region
 * @param position the square image position
 */
class FaceLandmark(val regionPosition: Vector3, val position: Vector3)

val faceSilhouette = intArrayOf(
        10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288,
        397, 365, 379, 378, 400, 377, 152, 148, 176, 149, 150, 136,
        172, 58, 132, 93, 234, 127, 162, 21, 54, 103, 67, 109)

val lipsUpperOuter = intArrayOf(61, 185, 40, 39, 37, 0, 267, 269, 270, 409, 291)
val lipsLowerOuter = intArrayOf(146, 91, 181, 84, 17, 314, 405, 321, 375, 291)
val lipsUpperInner = intArrayOf(78, 191, 80, 81, 82, 13, 312, 311, 310, 415, 308)
val lipsLowerInner = intArrayOf(78, 95, 88, 178, 87, 14, 317, 402, 318, 324, 308)


val rightEyeUpper0 = intArrayOf(246, 161, 160, 159, 158, 157, 173)
val rightEyeLower0 = intArrayOf(33, 7, 163, 144, 145, 153, 154, 155, 133)
val rightEyeUpper1 = intArrayOf(247, 30, 29, 27, 28, 56, 190)
val rightEyeLower1 = intArrayOf(130, 25, 110, 24, 23, 22, 26, 112, 243)
val rightEyeUpper2 = intArrayOf(113, 225, 224, 223, 222, 221, 189)
val rightEyeLower2 = intArrayOf(226, 31, 228, 229, 230, 231, 232, 233, 244)
val rightEyeLower3 = intArrayOf(143, 111, 117, 118, 119, 120, 121, 128, 245)

val rightEyebrowUpper = intArrayOf(156, 70, 63, 105, 66, 107, 55, 193)
val rightEyebrowLower = intArrayOf(35, 124, 46, 53, 52, 65)

val leftEyeUpper0 = intArrayOf(466, 388, 387, 386, 385, 384, 398)
val leftEyeLower0 = intArrayOf(263, 249, 390, 373, 374, 380, 381, 382, 362)
val leftEyeUpper1 = intArrayOf(467, 260, 259, 257, 258, 286, 414)
val leftEyeLower1 = intArrayOf(359, 255, 339, 254, 253, 252, 256, 341, 463)
val leftEyeUpper2 = intArrayOf(342, 445, 444, 443, 442, 441, 413)
val leftEyeLower2 = intArrayOf(446, 261, 448, 449, 450, 451, 452, 453, 464)
val leftEyeLower3 = intArrayOf(372, 340, 346, 347, 348, 349, 350, 357, 465)

val leftEyebrowUpper = intArrayOf(383, 300, 293, 334, 296, 336, 285, 417)
val leftEyebrowLower = intArrayOf(265, 353, 276, 283, 282, 295)

val midwayBetweenEyes = intArrayOf(168)

val noseTip = intArrayOf(1)
val noseBottom = intArrayOf(2)
val noseRightCorner = intArrayOf(98)
val noseLeftCorner = intArrayOf(327)

val rightCheek = intArrayOf(205)
val leftCheek = intArrayOf(42)

fun List<FaceLandmark>.contourOf(indices: IntArray, closed:Boolean = true): ShapeContour {
    require(indices.size>=3)
    return ShapeContour.fromPoints(indices.map {
        this[it].position.xy
    }, closed)

}

class FaceMesh(val graph: Graph) {
    val inputTensor = TFloat32.tensorOf(Shape.of(1, 192, 192, 3))
    val inputImage = colorBuffer(192, 192, format = ColorFormat.RGB, type = ColorType.FLOAT32)
    val referencePoints = loadReferenceMesh()

    var session: Session? = null
    fun start() {
        session = Session(graph)
    }

    fun close() {
        session?.close()
    }

    fun extractPose(landmarks: List<FaceLandmark>) : Matrix44 {
        return estimatePoseMatrix( referencePoints, landmarks.map { it.position }, referencePoints.map { 1.0 } )
    }

    fun extractLandmarks(squareImage: ColorBuffer, ssdRectangle: SSDRectangle): List<FaceLandmark> {
        if (session == null) {
            start()
        }
        val squareRectangle = ssdRectangle.map(squareImage.bounds)
        val center = squareRectangle.area.center
        val longestAxis = (squareRectangle.area.width * 1.5).toInt()

        val adjustedCorner = squareRectangle.area.center - Vector2(longestAxis / 2.0, longestAxis / 2.0)

        val sr = IntRectangle(center.x.toInt() - longestAxis / 2, center.y.toInt() - longestAxis / 2, longestAxis, longestAxis)
        val tr = IntRectangle(0, inputImage.height, inputImage.width, -inputImage.height)
        squareImage.copyTo(inputImage, sourceRectangle = sr, targetRectangle = tr, filter = MagnifyingFilter.LINEAR)

        inputImage.copyTo(inputTensor)

        val result = session?.let {
            val runner = it.runner()
            val tensors = runner.feed("input_1", inputTensor)
                    .fetch("Identity")
                    .run()

            val identityTensor = tensors[0] as TFloat32

            val landmarkFloats = identityTensor.toFloatArray2D()
            val landmarks = (0 until 468).map { landmarkIndex ->
                val x = landmarkFloats[0, landmarkIndex * 3].toDouble()
                val y = landmarkFloats[0, landmarkIndex * 3 + 1].toDouble()
                val z = landmarkFloats[0, landmarkIndex * 3 + 2].toDouble()

                val regionPosition = Vector3(x, y, z)
                val screenPosition = Vector3(x / 192.0, y / 192.0, z / 192.0) * squareRectangle.area.width * 1.5 + adjustedCorner.xy0
                FaceLandmark(regionPosition / 192.0, screenPosition)
            }

            identityTensor.close()
            landmarks
        } ?: error("no session")
        return result
    }

    companion object {
        fun load(): FaceMesh {
            val bytes = fetchORMLModel(
                "blazeface-1.0",
                "8e4f04510214d3a734868ef682f75d7b3f1dc71be27ca528945ca5e555472887"
            )
            val g = Graph()
            g.importGraphDef(GraphDef.parseFrom(bytes))
            return FaceMesh(g)
        }
    }
}