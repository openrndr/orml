package org.openrndr.orml.utils

import org.openrndr.draw.Filter
import org.openrndr.draw.filterShaderFromCode
import org.openrndr.math.Vector4

class MultiplyAdd : Filter(filterShaderFromCode(
        """
#version 330
in vec2 v_texCoord0;
uniform sampler2D tex0;
out vec4 o_output;
             
uniform vec4 scale;
uniform vec4 offset;             
             
void main() {
    o_output = texture(tex0, v_texCoord0) * scale + offset;
}             
""", "multiply-add")) {
    var scale: Vector4 by parameters
    var offset: Vector4 by parameters

    init {
        scale = Vector4.ONE
        offset = Vector4.ZERO
    }
}