package org.openrndr.orml.bodypix

import org.openrndr.resourceUrl
import org.tensorflow.ConcreteFunction
import org.tensorflow.Graph
import org.tensorflow.Session
import org.tensorflow.Signature
import org.tensorflow.op.Ops
import org.tensorflow.proto.framework.ConfigProto
import org.tensorflow.proto.framework.ConfigProtoOrBuilder
import org.tensorflow.proto.framework.GraphDef
import org.tensorflow.types.TFloat32
import java.net.URL

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

    val session = Session(graph, configBuilder.build()).apply {
        globalSession = this
        globalGraph = graph

    }



    val adjustFunction by lazy {
        val adjustG = Graph()
        val adjustSession = Session(adjustG)
        val tf = Ops.create(adjustG)
        val inputData = tf.placeholder(TFloat32::class.java)


        val scaled = tf.math.mul(inputData, tf.constant(255.0f))
        val biased = tf.math.add(scaled, tf.constant(floatArrayOf(-123.15f, -115.90f, -103.06f)))

        val signature = Signature.builder()
            .input("inputData", inputData)
            .output("y", biased).build()

        ConcreteFunction.create(signature, adjustSession)
    }

    fun infer(inputData: TFloat32): BodyPixInference {
        println("adjusting")
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

    companion object {
        fun load(): BodyPix {
            val bytes = URL(resourceUrl("/tfmodels/bodypix.pb")).readBytes()
            val g = Graph()
            g.importGraphDef(GraphDef.parseFrom(bytes))
            return BodyPix(g)
        }
    }
}