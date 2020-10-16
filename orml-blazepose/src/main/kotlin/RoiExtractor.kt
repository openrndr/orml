import org.openrndr.color.ColorRGBa
import org.openrndr.draw.*
import org.openrndr.math.Vector3

class RoiExtractor {
    val vbo = vertexBuffer(vertexFormat {
        position(3)
        textureCoordinate(2)
    }, 6)

    fun extractRoi(drawer: Drawer, image: ColorBuffer, region: Region) {
        vbo.put {
            write(Vector3(0.0, 256.0, 0.0))
            write(region.roi_coord[0])

            write(Vector3(256.0, 256.0, 0.0))
            write(region.roi_coord[1])

            write(Vector3(256.0, 0.0, 0.0))
            write(region.roi_coord[2])

            write(Vector3(256.0, 0.0, 0.0))
            write(region.roi_coord[2])

            write(Vector3(0.0, 0.0, 0.0))
            write(region.roi_coord[3])

            write(Vector3(0.0, 256.0, 0.0))
            write(region.roi_coord[0])
        }
        drawer.isolated {
            drawer.clear(ColorRGBa.GRAY)
            drawer.defaults()
            drawer.ortho(RenderTarget.active)
            drawer.shadeStyle = shadeStyle {
                fragmentTransform = """
                    vec2 ts = textureSize(p_image, 0);
                    vec2 uv = va_texCoord0;
                    x_fill = texture(p_image, uv);
                    
                    if (uv.x < 0.0) {
                        x_fill = vec4(0.0);
                    }
                    if (uv.y < 0.0) {
                        x_fill = vec4(0.0);
                    }
                    if (uv.x > 1.0) {
                        x_fill = vec4(0.0);
                    }
                    if (uv.y > 1.0) {
                        x_fill = vec4(0.0);
                    }
                """.trimIndent()
                parameter("image", image)
            }
            drawer.vertexBuffer(vbo, DrawPrimitive.TRIANGLES)
        }
    }
}
