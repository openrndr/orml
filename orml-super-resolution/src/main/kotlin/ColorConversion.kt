import org.openrndr.draw.Filter
import org.openrndr.draw.filterShaderFromCode

class RgbToYpbpr : Filter(filterShaderFromCode("""
#version 330

uniform sampler2D tex0;
layout(location=0) out vec4 o_y;
layout(location=1) out vec4 o_prpb;
in vec2 v_texCoord0;

void main() {
    vec4 c = texture(tex0,  v_texCoord0);
    float y = 0.2126 * c.r + 0.7152 * c.g + 0.0722 * c.b;
    
    float pb = 1/2.0 * (c.b - y) / (1.0 - 0.0722); 
    float pr = 1/2.0 * (c.r - y) / (1.0 - 0.2126);
    o_y = vec4(y, y, y, 1.0);
    o_prpb = vec4(pb, pr, 0.0, 1.0);
}
    
""".trimIndent(), "rgb-to-ypbr")) {

}