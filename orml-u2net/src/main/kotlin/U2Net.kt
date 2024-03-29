package org.openrndr.orml.u2net

import org.openrndr.draw.*
import org.openrndr.extra.tensorflow.copyTo
import org.openrndr.math.Vector4
import org.openrndr.orml.utils.MaskToAlpha
import org.openrndr.orml.utils.MultiplyAdd
import org.openrndr.orml.utils.fetchORMLModel
import org.openrndr.resourceUrl
import org.openrndr.shape.IntRectangle
import org.tensorflow.Graph
import org.tensorflow.Session
import org.tensorflow.Tensor
import org.tensorflow.ndarray.Shape
import org.tensorflow.proto.framework.GraphDef
import org.tensorflow.types.TFloat32
import java.net.URL

class U2Net(private val graph: Graph, val modelWidth: Int, val modelHeight: Int) {
    private val inputTensor =  TFloat32.tensorOf(Shape.of(1, modelHeight.toLong(), modelWidth.toLong(), 3))
    private val inputImage = colorBuffer(modelWidth, modelHeight, format = ColorFormat.RGB, type = ColorType.FLOAT32)
    private val outputImage = colorBuffer(modelWidth, modelHeight, format = ColorFormat.R, type = ColorType.FLOAT32)
    private val multiplyAdd = MultiplyAdd()
    private val maskToAlpha by lazy { MaskToAlpha() }

    var session: Session? = null
    fun start() {
        session = Session(graph)
    }

    fun close() {
        session?.close()
    }

    /**
     * Calculates a matte image (mask) for the given input image
     */
    fun matte(input: ColorBuffer, output: ColorBuffer? = null): ColorBuffer {
        val result = output ?: input.createEquivalent()
        infer(input)
        outputImage.copyTo(result, sourceRectangle = IntRectangle(0, 0, outputImage.width, outputImage.height), targetRectangle = IntRectangle(0, result.height, result.width, -result.height))
        return result
    }

    /**
     * Removes the background from the input image
     */
    fun removeBackground(input: ColorBuffer, output: ColorBuffer? = null): ColorBuffer {
        val result = output ?: input.createEquivalent(format = ColorFormat.RGBa)
        infer(input)
        outputImage.copyTo(result,
            sourceRectangle = IntRectangle(0, 0, outputImage.width, outputImage.height),
            targetRectangle = IntRectangle(0, result.height, result.width, -result.height))
        maskToAlpha.invert = false
        maskToAlpha.apply(arrayOf(input, result), result)
        return result
    }

    /**
     * Removes the foreground from the input image
     */
    fun removeForeground(input: ColorBuffer, output: ColorBuffer? = null): ColorBuffer {
        val result = output ?: input.createEquivalent(format = ColorFormat.RGBa)
        infer(input)
        outputImage.copyTo(result, sourceRectangle = IntRectangle(0, 0, outputImage.width, outputImage.height), targetRectangle = IntRectangle(0, result.height, result.width, -result.height), filter = MagnifyingFilter.LINEAR)
        maskToAlpha.invert = true
        maskToAlpha.apply(arrayOf(input, result), result)
        return result
    }

    private fun infer(image: ColorBuffer): ColorBuffer {
        if (session == null) {
            start()
        }
        image.copyTo(inputImage, sourceRectangle = IntRectangle(0, 0, image.width, image.height), targetRectangle = IntRectangle(0, inputImage.height, inputImage.width, -inputImage.height), filter = MagnifyingFilter.LINEAR)
        multiplyAdd.scale = Vector4.ONE * 2.0
        multiplyAdd.offset = Vector4.ONE * -1.0
        multiplyAdd.apply(inputImage, inputImage)
        inputImage.copyTo(inputTensor)

        session?.let {
            val runner = it.runner()
            val tensors = runner.feed("inputs", inputTensor)
                    .fetch("functional_1/tf_op_layer_Sigmoid_6/Sigmoid_6")
                    .run()
            val identityTensor = tensors[0] as TFloat32
            identityTensor.copyTo(outputImage)
            identityTensor.close()
        } ?: error("no session")
        return outputImage
    }

    companion object {
        fun load(): U2Net {
            val bytes = fetchORMLModel(
                "u2netp-320x320-float32-1.0",
                "79e8757f7c5342c4f2b0dab4c66c4bc128b077c9541db5c6a5b363ff75c8b54a"
            )
            val g = Graph()
            g.importGraphDef(GraphDef.parseFrom(bytes))
            return U2Net(g, 320, 320)
        }
    }
}