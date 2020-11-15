package org.openrndr.orml.utils

import org.openrndr.draw.Filter
import org.openrndr.draw.filterShaderFromCode
import org.openrndr.draw.glsl
import org.openrndr.math.Vector4

class MaskToAlpha : Filter(filterShaderFromCode(
        """
#version 330
in vec2 v_texCoord0;
uniform sampler2D tex0;
uniform sampler2D tex1;
uniform bool invert;
uniform bool useThreshold;
uniform bool useSmoothstep;
uniform float threshold;
uniform float low;
uniform float high;
out vec4 o_output;
             
void main() {
    float mask = texture(tex1, v_texCoord0).r;
    mask = useSmoothstep? smoothstep(low, high, mask) : mask;
    mask = useThreshold? step(threshold, mask) : mask;
    mask = invert? (1.0 - mask) : mask;
    o_output = texture(tex0, v_texCoord0) * mask;
}             
""", "mask-to-alpha")) {

    var invert: Boolean by parameters
    var useThreshold : Boolean by parameters
    var useSmoothstep : Boolean by parameters
    var threshold : Double by parameters
    var low: Double by parameters
    var high: Double by parameters
    init {
        invert = false
        useThreshold = false
        useSmoothstep = true
        threshold = 0.9
        low = 0.49
        high = 0.5
    }
}