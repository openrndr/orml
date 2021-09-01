import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.*
import org.openrndr.extras.meshgenerators.boxMesh
import org.openrndr.ffmpeg.loadVideoDevice
import org.openrndr.math.Matrix44
import org.openrndr.math.Vector3
import org.openrndr.math.Vector4
import org.openrndr.math.transforms.rotateY
import org.openrndr.math.transforms.translate
import org.openrndr.orml.facemesh.*
import org.openrndr.shape.IntRectangle
import org.openrndr.shape.Rectangle

fun main() {
    application {
        configure {
            width = 640
            height = 640
        }
        program {
            val video = loadVideoDevice(width = 640, height = 480)
            video.play()

            val squareImage = colorBuffer(640, 640)
            val effectLayer = renderTarget(squareImage.width, squareImage.height) {
                colorBuffer()
                depthBuffer()
            }
            video.newFrame.listen {
                it.frame.copyTo(
                    squareImage,
                    sourceRectangle = IntRectangle(0, 0, it.frame.width, it.frame.height),
                    targetRectangle = IntRectangle(
                        0,
                        squareImage.height - (squareImage.height - 480) / 2,
                        it.frame.width,
                        -it.frame.height
                    )
                )
            }
            val blazeFace = BlazeFace.load()
            val faceMesh = FaceMesh.load()

            val box = boxMesh()

            var poseSmooth = Matrix44.IDENTITY

            extend {
                video.draw(drawer, blind = true)
                val rectangles = blazeFace.detectFaces(squareImage)
                drawer.image(
                    squareImage,
                    squareImage.width * 1.0,
                    0.0,
                    -squareImage.width * 1.0,
                    squareImage.height * 1.0
                )
                for (rectangle in rectangles) {
                    val s = 640.0
                    val r = Rectangle(rectangle.area.corner * s, rectangle.area.width * s, rectangle.area.height * s)
                    drawer.fill = null
                    drawer.stroke = ColorRGBa.PINK
                    val landmarks = faceMesh.extractLandmarks(squareImage, rectangle)
                    val pose = faceMesh.extractPose(landmarks)

                    poseSmooth = poseSmooth * 0.9 + pose * 0.1

                    drawer.lineSegment(landmarks[1].position.xy, landmarks[1].position.xy + pose[0].xy * 10.0)
                    drawer.lineSegment(landmarks[1].position.xy, landmarks[1].position.xy + pose[1].xy * 10.0)

                    drawer.isolatedWithTarget(effectLayer) {
                        drawer.clear(ColorRGBa.TRANSPARENT)
                        drawer.defaults()
                        drawer.depthWrite = true
                        drawer.depthTestPass = DepthTestPass.LESS_OR_EQUAL

                        val pose = poseSmooth
                        val t = pose[3].xy.xy0 / (squareImage.width.toDouble() / 2.0) - Vector3(1.0, 1.0, 0.0)
                        val persp =
                            Matrix44.translate(t * Vector3(-1.0, -1.0, 1.0)) * org.openrndr.math.transforms.perspective(
                                90.0,
                                1.0,
                                0.1,
                                100.0
                            )
                        drawer.projection = persp
                        drawer.model = Matrix44.translate(0.0, 0.0, -20.0) *
                                Matrix44.fromColumnVectors(
                                    Vector4.UNIT_X * 1.0,
                                    Vector4.UNIT_Y,
                                    Vector4.UNIT_Z,
                                    Vector4.UNIT_W
                                ) * Matrix44.fromColumnVectors(pose[0], pose[1], pose[2], Vector4.UNIT_W) *
                                Matrix44.rotateY(45.0)

                        drawer.shadeStyle = shadeStyle {
                            fragmentTransform = """
                                x_fill.rgb = vec3(normalize(v_viewNormal).z);
                            """.trimIndent()
                        }
                        drawer.vertexBuffer(box, DrawPrimitive.TRIANGLES)

                        drawer.translate(2.0, 0.0, 0.0)
                        drawer.vertexBuffer(box, DrawPrimitive.TRIANGLES)

                    }
                    drawer.image(effectLayer.colorBuffer(0))
                    drawer.contour(landmarks.contourOf(faceSilhouette))
                }
            }
        }
    }
}