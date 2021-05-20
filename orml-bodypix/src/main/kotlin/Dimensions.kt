package org.openrndr.orml.bodypix

fun toInputResolutionHeightAndWidth(
    internalResolution: Double,
    outputStride: Int,
    inputHeight: Int,
    inputWidth: Int
): Pair<Int, Int> {
    val internalResolutionPercentage = toInternalResolutionPercentage(internalResolution)

    return Pair(
        toValidInputResolution(inputHeight * internalResolutionPercentage, outputStride),
        toValidInputResolution(inputWidth * internalResolutionPercentage, outputStride)
    )
}

fun toInternalResolutionPercentage(
    internalResolution: Double
): Double {
    require(internalResolution in 0.0..2.0) {
        "internal resolution out of range 0.0..2.0"
    }
    return internalResolution
}


fun toValidInputResolution(
    inputResolution: Double, outputStride: Int
): Int {
    if (isValidInputResolution(inputResolution, outputStride)) {
        return inputResolution.toInt();
    }

    return (Math.floor(inputResolution / outputStride) * outputStride + 1).toInt()
}

fun isValidInputResolution(
    resolution: Double, outputStride: Int
): Boolean {
    return (resolution - 1) % outputStride == 0.0
}
