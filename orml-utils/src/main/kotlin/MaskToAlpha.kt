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
out vec4 o_output;
             
void main() {
    float mask = texture(tex1, v_texCoord0).r;
    mask = invert? (1.0 - mask) : mask;
    o_output = texture(tex0, v_texCoord0) * mask;
}             
""", "mask-to-alpha")) {

    var invert: Boolean by parameters
    init {
        invert = false
    }
}