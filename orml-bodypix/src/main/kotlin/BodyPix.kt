package org.openrndr.orml.bodypix

import org.openrndr.draw.ColorBuffer
import org.openrndr.extra.tensorflow.copyTo
import org.openrndr.orml.utils.fetchORMLModel
import org.openrndr.resourceUrl
import org.tensorflow.*
import org.tensorflow.ndarray.NdArray
import org.tensorflow.ndarray.NdArrays
import org.tensorflow.ndarray.Shape
import org.tensorflow.op.Ops
import org.tensorflow.proto.framework.ConfigProto
import org.tensorflow.proto.framework.ConfigProtoOrBuilder
import org.tensorflow.proto.framework.GraphDef
import org.tensorflow.types.TFloat32
import org.tensorflow.types.TInt32
import org.tensorflow.types.TInt64
import java.net.URL
import kotlin.math.roundToInt

enum class BodyPixArchitecture {
    RESNET,
    MOBILENET
}

class BodyPixInference(
    val heatmapScores: TFloat32,
    val offsets: TFloat32,
    val displacementFwd: TFloat32,
    val displacementBwd: TFloat32,
    val segmentation: TFloat32,
    val partHeatmaps: TFloat32,
    val longOffsets: TFloat32,
    val partOffsets: TFloat32
) {
    fun close() {
        heatmapScores.close()
        offsets.close()
        displacementFwd.close()
        displacementBwd.close()
        segmentation.close()
        partHeatmaps.close()
        longOffsets.close()
        partOffsets.close()
    }
}

class BodyPix(val graph: Graph, val architecture: BodyPixArchitecture = BodyPixArchitecture.RESNET) {
    val configBuilder = ConfigProto.newBuilder().apply {
        setGpuOptions(this.gpuOptionsBuilder.setAllowGrowth(true).build())
    }
    val session = Session(graph, configBuilder.build())
    val adjustFunction by lazy {
        val adjustG = Graph()
        val adjustSession = Session(adjustG)
        val tf = Ops.create(adjustG)
        val inputData = tf.placeholder(TFloat32::class.java)

        val scaled = when(architecture) {
            BodyPixArchitecture.RESNET -> tf.math.mul(inputData, tf.constant(255.0f))
            BodyPixArchitecture.MOBILENET -> tf.math.mul(inputData, tf.constant(2.0f))
        }
        val biased = when(architecture) {
            BodyPixArchitecture.RESNET -> tf.math.add(scaled, tf.constant(floatArrayOf(-123.15f, -115.90f, -103.06f)))
            BodyPixArchitecture.MOBILENET -> tf.math.add(scaled, tf.constant(floatArrayOf(-1.0f, -1.0f, -1.0f)))
        }
        val signature = Signature.builder()
            .input("inputData", inputData)
            .output("y", biased).build()

        ConcreteFunction.create(signature, adjustSession)
    }

    fun infer(inputData: TFloat32): BodyPixInference {
        val adjusted = adjustFunction.call(
            mapOf("inputData" to inputData)
        )["y"] as TFloat32
        val result = justInfer(adjusted)
        adjusted.close()
        return result
    }

    fun justInfer(inputData: TFloat32): BodyPixInference {
        val r = session.runner()
        val displacementFwdName: String
        val displacementBwdName: String
        when (architecture) {
            BodyPixArchitecture.RESNET -> {
                displacementBwdName = "resnet_v1_50/displacement_bwd_2/BiasAdd"
                displacementFwdName = "resnet_v1_50/displacement_fwd_2/BiasAdd"
            }
            BodyPixArchitecture.MOBILENET -> {
                displacementBwdName = "MobilenetV1/displacement_bwd_2/BiasAdd"
                displacementFwdName = "MobilenetV1/displacement_fwd_2/BiasAdd"
            }
        }
        val result = r
            .feed("sub_2", 0, inputData)
            .fetch("float_segments", 0)
            .fetch("float_part_offsets", 0)
            .fetch("float_part_heatmaps", 0)
            .fetch("float_short_offsets", 0)
            .fetch("float_long_offsets", 0)
            .fetch("float_heatmaps", 0)
            .fetch(displacementFwdName, 0)
            .fetch(displacementBwdName, 0)
            .run()

        val heatmapScores = result[5] as TFloat32
        val offsets = result[3] as TFloat32
        val displacementFwd = result[6] as TFloat32
        val displacementBwd = result[7] as TFloat32
        val segmentation = result[0] as TFloat32
        val partHeatmaps = result[2] as TFloat32
        val longOffsets = result[4] as TFloat32
        val partOffsets = result[1] as TFloat32

        return BodyPixInference(
            heatmapScores, offsets, displacementFwd, displacementBwd,
            segmentation, partHeatmaps, longOffsets, partOffsets
        )
    }

    val toFlattenedOneHotMapFunction by lazy {
        val adjustG = Graph()
        val adjustSession = Session(adjustG)
        val tf = Ops.create(adjustG)
        val partHeatmapScores = tf.placeholder(TFloat32::class.java)
        val partMapLocations = tf.math.argMax(partHeatmapScores, tf.constant(2))
        val partMapFlattened = tf.reshape(partMapLocations, tf.constant(-1L))
        val oneHot = tf.oneHot(partMapFlattened, tf.constant(-1), tf.constant(1.0f), tf.constant(0.0f))
        val signature = Signature.builder()
            .input("x", partHeatmapScores)
            .output("y", oneHot).build()

        ConcreteFunction.create(signature, session)
    }

    fun toFlattenedOneHotMap(partHeatmapScores: TFloat32): TFloat32 {
        return toFlattenedOneHotMapFunction.call(partHeatmapScores) as TFloat32
    }

    val toMaskTensorFunction by lazy {
        val adjustG = Graph()
        val adjustSession = Session(adjustG)
        val tf = Ops.create(adjustG)
        val input = tf.placeholder(TFloat32::class.java)
        val threshold = tf.placeholder(TFloat32::class.java)
        val output = tf.math.greater(input, threshold)
        val outputInt32 = tf.dtypes.cast(output, TInt32::class.java)
        val signature = Signature.builder()
            .input("x", input)
            .input("threshold", threshold)
            .output("y", outputInt32).build()

        ConcreteFunction.create(signature, adjustSession)
    }

    fun toMaskTensor(segmentScores: TFloat32, threshold: TFloat32): TInt32 {
        val function = toMaskTensorFunction
        val result = function.call(mapOf("x" to segmentScores, "threshold" to threshold)) as TInt32
        return result
    }

    val decodePartSegmentationFunction by lazy {
        val adjustG = Graph()
        val adjustSession = Session(adjustG)
        val tf = Ops.create(adjustG)
        val segmentationMask = tf.placeholder(TInt32::class.java)
        val flattenedMap = tf.placeholder(TInt32::class.java)
        val numParts = tf.placeholder(TInt32::class.java)
        val partMapSize = tf.placeholder(TInt64::class.java)

        val partNumbersUnexpanded = tf.range(tf.constant(0), numParts, tf.constant(1))
        val partNumbers = tf.expandDims(partNumbersUnexpanded, tf.constant(1))
        val partMapFlattened = tf.linalg.matMul(flattenedMap, partNumbers)

        val partMap = tf.reshape(partMapFlattened, partMapSize)
        val partMapShiftedUpForClipping = tf.math.add(partMap, tf.constant(1))
        val clippedMul = tf.math.mul(partMapShiftedUpForClipping, segmentationMask)
        val clippedSub = tf.math.sub(clippedMul, tf.constant(-1))

        val signature = Signature.builder()
            .input("segmentationMask", segmentationMask)
            .input("flattenedMap", flattenedMap)
            .input("numParts", numParts)
            .input("partMapSize", partMapSize)
            .output("decoded", clippedSub)
            .build()

        ConcreteFunction.create(signature, adjustSession)
    }

    fun decodePartSegmentation(
        segmentationMask: TInt32,
        partHeatmapScores: TFloat32
    ): TInt32 {
        val (_, partMapHeight, partMapWidth, numParts) = partHeatmapScores.shape().asArray()
        val flattenedMap = toFlattenedOneHotMap(partHeatmapScores)
        val partMapSize = TInt64.vectorOf(partMapHeight, partMapWidth)
        val partCount = TInt32.scalarOf(numParts.toInt())

        val result = decodePartSegmentationFunction.call(
            mapOf(
                "flattenedMap" to flattenedMap,
                "numParts" to partCount,
                "flattenedMap" to flattenedMap,
                "segmentationMask" to segmentationMask
            )
        ) as TInt32
        partMapSize.close()
        flattenedMap.close()
        return result
    }

    // -------------------

    val padAndResizeToFunction by lazy {
        val adjustG = Graph()
        val adjustSession = Session(adjustG)
        val tf = Ops.create(adjustG)

        val input = tf.placeholder(TFloat32::class.java)
        val size = tf.placeholder(TInt32::class.java)
        val padding = tf.placeholder(TInt64::class.java)
        val padValue = tf.constant(0.0f)
        val padded = tf.pad(input, padding, padValue)
        val resized = tf.image.resizeBilinear(padded, size)

        val signature = Signature.builder()
            .input("input", input)
            .input("padding", padding)
            .input("size", size)
            .output("resized", resized).build()

        ConcreteFunction.create(signature, adjustSession)
    }

    data class Padding(val top: Int, val bottom: Int, val left: Int, val right: Int) {
        fun toArray(): NdArray<Long> {
            val nd = NdArrays.ofLongs(Shape.of(2, 2))
            nd.setLong(top.toLong(), 0, 0)
            nd.setLong(bottom.toLong(), 0, 1)
            nd.setLong(left.toLong(), 1, 0)
            nd.setLong(right.toLong(), 1, 1)
            return nd
        }
    }

    class PaddedImage(val image: Tensor, val padding: Padding)

    fun padAndResizeTo(input: ColorBuffer, targetHeight: Int, targetWidth: Int): PaddedImage {
        val imageTensor = TFloat32.tensorOf(Shape.of(1L, input.height.toLong(), input.width.toLong(), 3L))
        input.copyTo(imageTensor)
        val aspect = input.width.toDouble() / input.height
        val targetAspect = targetHeight.toDouble() / targetWidth.toDouble()
        val padding =
            if (aspect < targetAspect) {
                Padding(
                    0, 0,
                    (0.5 * (targetAspect * input.height - input.width)).roundToInt(),
                    (0.5 * (targetAspect * input.height - input.width)).roundToInt()
                )
            } else {
                Padding(
                    (0.5 * ((1.0 / targetAspect) * input.width - input.height)).roundToInt(),
                    (0.5 * ((1.0 / targetAspect) * input.width - input.height)).roundToInt(),
                    0, 0
                )
            }

        val resized = padAndResizeToFunction.call(
            mapOf(
                "input" to imageTensor,
                "padding" to TInt64.tensorOf(padding.toArray()),
                "size" to TInt32.vectorOf(targetHeight, targetWidth),
            )
        ) as TFloat32

        return PaddedImage(resized, padding)
    }


    val removePaddingAndResizeBackFunction by lazy {
        val adjustG = Graph()
        val adjustSession = Session(adjustG)
        val tf = Ops.create(adjustG)
        val inputImage = tf.placeholder(TFloat32::class.java)
        var batchedImage = tf.expandDims(inputImage, tf.constant(2))
        batchedImage = tf.expandDims(batchedImage, tf.constant(0))

        val padding = tf.placeholder(TFloat32::class.java)
        val size = tf.placeholder(TInt32::class.java)

        val result = tf.image.cropAndResize(batchedImage, padding, tf.constant(0), size)

        val signature = Signature.builder()
            .input("padding", padding)
            .input("size", size)
            .input("inputImage", inputImage)
            .output("result", result).build()

        ConcreteFunction.create(signature, adjustSession)
    }

    fun removePaddingAndResizeBack(
        resizedAndPadded: TFloat32,
        originalHeight: Int,
        originalWidth: Int,
        padding: Padding
    ): TFloat32 {
        val padT = padding.top.toFloat()
        val padB = padding.bottom.toFloat()
        val padL = padding.left.toFloat()
        val padR = padding.right.toFloat()

        val padding = arrayOf(
            floatArrayOf(
                padT / (originalHeight + padT + padB - 1.0f),
                padL / (originalWidth + padL + padR - 1.0f),
                (padT + originalHeight - 1.0f) /
                        (originalHeight + padT + padB - 1.0f),
                (padL + originalWidth - 1.0f) / (originalWidth + padL + padR - 1.0f)
            )
        )
        val paddingND = NdArrays.ofFloats(Shape.of(1, 4))
        paddingND.setFloat(padding[0][0], 0, 0)
        paddingND.setFloat(padding[0][1], 0, 1)
        paddingND.setFloat(padding[0][2], 0, 2)
        paddingND.setFloat(padding[0][3], 0, 3)

        return removePaddingAndResizeBackFunction.call(
            mapOf(
                "padding" to TFloat32.tensorOf(paddingND),
                "size" to TInt32.vectorOf(originalHeight, originalWidth),
                "inputImage" to resizedAndPadded
            )
        )["result"] as TFloat32

    }


    companion object {
        fun load(architecture: BodyPixArchitecture = BodyPixArchitecture.RESNET): BodyPix {
            val bytes =
                when (architecture) {
                    BodyPixArchitecture.RESNET -> fetchORMLModel("bodypix-resnet-1.0", "dc10ddb57ccf8b1604f8f34a2fed2d01520270120230e699156ba0910613ed4a")
                    BodyPixArchitecture.MOBILENET -> fetchORMLModel("bodypix-mobilenet-1.0", "c64d6f3252217f9bd0ba790ac2a6ac8b45fc002767c97379cb2e8a3ce7317b56")
                }

            val g = Graph()
            g.importGraphDef(GraphDef.parseFrom(bytes))
            return BodyPix(g, architecture)
        }
    }
}