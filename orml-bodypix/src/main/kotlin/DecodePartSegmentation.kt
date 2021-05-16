
package org.openrndr.orml.bodypix

import org.tensorflow.*
import org.tensorflow.ndarray.Shape
import org.tensorflow.op.Ops
import org.tensorflow.types.TFloat32
import org.tensorflow.types.TInt32
import org.tensorflow.types.TInt64

lateinit var globalGraph: Graph
lateinit var globalSession: Session

val toFlattenedOneHotMapFunction by lazy {
    val tf = Ops.create(globalGraph)
    val partHeatmapScores = tf.placeholder(TFloat32::class.java)
    val partMapLocations = tf.math.argMax(partHeatmapScores, tf.constant(2))
    val partMapFlattened = tf.reshape(partMapLocations, tf.constant(-1L))
    val oneHot = tf.oneHot(partMapFlattened, tf.constant(-1), tf.constant(1.0f), tf.constant(0.0f))
    val signature = Signature.builder()
        .input("x", partHeatmapScores)
        .output("y", oneHot).build()

    ConcreteFunction.create(signature, globalSession)
}


fun toFlattenedOneHotMap(partHeatmapScores: TFloat32) : TFloat32 {
    return toFlattenedOneHotMapFunction.call(partHeatmapScores) as TFloat32
}

val toMaskTensorFunction by lazy {
    val tf = Ops.create(globalGraph)
    val input = tf.placeholder(TFloat32::class.java)
    val threshold = tf.placeholder(TFloat32::class.java)
    val output = tf.math.greater(input, threshold)
    val outputInt32 = tf.dtypes.cast(output, TInt32::class.java)
    val signature = Signature.builder()
        .input("x", input)
        .input("threshold", threshold)
        .output("y", outputInt32).build()

    ConcreteFunction.create(signature, globalSession)
}

fun toMaskTensor(segmentScores: TFloat32, threshold: TFloat32) : TInt32 {
    val function = toMaskTensorFunction
    val result = function.call(mapOf("x" to segmentScores, "threshold" to threshold)) as TInt32
    return result
}

val decodePartSegmentationFunction by lazy {
    val tf = Ops.create(globalGraph)

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

    ConcreteFunction.create(signature, globalSession)
}

fun decodePartSegmentation(segmentationMask: TInt32,
                           partHeatmapScores: TFloat32) : TInt32 {
    val (_ , partMapHeight, partMapWidth, numParts) = partHeatmapScores.shape().asArray()
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

private fun Shape.last(): Long {
    return size(this.numDimensions()-1)
}
