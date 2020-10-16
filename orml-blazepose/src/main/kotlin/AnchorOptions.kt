import org.openrndr.shape.Rectangle
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt

class Anchor {
    var width = 0.0
    var height = 0.0
    var xCenter = 0.0
    var yCenter = 0.0
    override fun toString(): String {
        return "Anchor(w=$width, h=$height, xCenter=$xCenter, yCenter=$yCenter)"
    }

    fun scaled(sourceRect: Rectangle) : Anchor {
        val longest = max(sourceRect.width, sourceRect.height)
        val scaled = Anchor()
        scaled.width = width * longest
        scaled.height = height * longest
        scaled.xCenter = xCenter * longest
        scaled.yCenter = yCenter * longest
        return scaled
    }
}

data class WeightedAnchor(val weight: Double, val anchor:Anchor)

class AnchorOptions {
    var strides = mutableListOf<Int>(8, 16, 16, 16)
    var aspectRatios = mutableListOf<Double>(1.0)
    var featureMapHeight = mutableListOf<Int>()
    var featureMapWidth = mutableListOf<Int>()

    var numLayers = 4
    var minScale = 0.1484375
    var maxScale = 0.75;
    var inputSizeHeight = 128;
    var inputSizeWidth = 128;
    var anchorOffsetX = 0.5;
    var anchorOffsetY = 0.5;
    var reduceBoxesInLowerLayer = false
    var interpolatedScaleAspectRatio = 1.0
    var fixedAnchorSize = true

}

fun generateAnchors(options: AnchorOptions): List<Anchor> {
    val anchors = mutableListOf<Anchor>()
    var layerId = 0
    while (layerId < options.strides.size) {
        val anchorHeight = mutableListOf<Double>()
        val anchorWidth = mutableListOf<Double>()
        val aspectRatios = mutableListOf<Double>()
        val scales = mutableListOf<Double>()

        // For same strides, we merge the anchors in the same order.
        var lastSameStrideLayer = layerId;
        while (lastSameStrideLayer < options.strides.size &&
                options.strides[lastSameStrideLayer] == options.strides[layerId]) {
            val scale =
                    CalculateScale(options.minScale, options.maxScale,
                            lastSameStrideLayer, options.strides.size);
            if (lastSameStrideLayer == 0 && options.reduceBoxesInLowerLayer) {
                // For first layer, it can be specified to use predefined anchors.
                aspectRatios.add(1.0);
                aspectRatios.add(2.0);
                aspectRatios.add(0.5);
                scales.add(0.1);
                scales.add(scale);
                scales.add(scale);
            } else {
                for (aspect_ratio_id in 0 until options.aspectRatios.size) {

                    aspectRatios.add(options.aspectRatios[aspect_ratio_id]);
                    scales.add(scale);
                }
                if (options.interpolatedScaleAspectRatio > 0.0) {
                    val scale_next =
                            if (lastSameStrideLayer == options.strides.size - 1)
                                1.0 else
                                CalculateScale(options.minScale, options.maxScale,
                                        lastSameStrideLayer + 1,
                                        options.strides.size);
                    scales.add(sqrt(scale * scale_next));
                    aspectRatios.add(options.interpolatedScaleAspectRatio);
                }
            }
            lastSameStrideLayer++;
        }

        for (i in 0 until aspectRatios.size) {
            val ratioSqrts = sqrt(aspectRatios[i]);
            anchorHeight.add(scales[i] / ratioSqrts);
            anchorWidth.add(scales[i] * ratioSqrts);
        }

        var featureMapHeight = 0;
        var featureMapWidth = 0;
        if (options.featureMapHeight.isNotEmpty()) {
            featureMapHeight = options.featureMapHeight[layerId];
            featureMapWidth = options.featureMapWidth[layerId];
        } else {
            val stride = options.strides[layerId];
            featureMapHeight = ceil(1.0 * options.inputSizeHeight / stride).toInt();
            featureMapWidth = ceil(1.0 * options.inputSizeWidth / stride).toInt();
        }

        for (y in 0 until featureMapHeight) {
            for (x in 0 until featureMapWidth) {
                for (anchor_id in 0 until anchorHeight.size) {
                    // TODO: Support specifying anchor_offset_x, anchor_offset_y.
                    val xCenter = (x + options.anchorOffsetX) * 1.0 / featureMapWidth;
                    val yCenter = (y + options.anchorOffsetY) * 1.0 / featureMapHeight;

                    val newAnchor = Anchor()
                    newAnchor.xCenter = xCenter;
                    newAnchor.yCenter = yCenter;

                    if (options.fixedAnchorSize) {
                        newAnchor.width = 1.0;
                        newAnchor.height = 1.0;
                    } else {
                        newAnchor.width = anchorWidth[anchor_id];
                        newAnchor.height = anchorHeight[anchor_id];
                    }
                    anchors.add(newAnchor);
                }
            }
        }
        layerId = lastSameStrideLayer;
    }
    return anchors
}


fun CalculateScale(minScale: Double, maxScale: Double, strideIndex: Int, numStrides: Int): Double {
    return if (numStrides == 1)
        (minScale + maxScale) * 0.5;
    else
        minScale + (maxScale - minScale) * 1.0 * strideIndex / (numStrides - 1.0);
}
