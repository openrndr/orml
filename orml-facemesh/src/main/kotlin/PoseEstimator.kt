package org.openrndr.orml.facemesh

import org.ejml.simple.SimpleMatrix
import org.openrndr.extra.objloader.loadOBJ
import org.openrndr.extra.objloader.loadOBJEx
import org.openrndr.math.Matrix33
import org.openrndr.math.Matrix44
import org.openrndr.math.Vector3
import org.openrndr.resourceUrl
import java.net.URL
import kotlin.math.sqrt


// based on https://github.com/google/mediapipe/blob/master/mediapipe/modules/face_geometry/libs/procrustes_solver.cc

private fun multiplyTransposed(a: List<Vector3>, bt: List<Vector3>): Matrix33 {
    val size = a.size

    val c0r0 = (0 until size).sumByDouble { a[it].x * bt[it].x }
    val c1r0 = (0 until size).sumByDouble { a[it].x * bt[it].y }
    val c2r0 = (0 until size).sumByDouble { a[it].x * bt[it].z }

    val c0r1 = (0 until size).sumByDouble { a[it].y * bt[it].x }
    val c1r1 = (0 until size).sumByDouble { a[it].y * bt[it].y }
    val c2r1 = (0 until size).sumByDouble { a[it].y * bt[it].z }

    val c0r2 = (0 until size).sumByDouble { a[it].z * bt[it].x }
    val c1r2 = (0 until size).sumByDouble { a[it].z * bt[it].y }
    val c2r2 = (0 until size).sumByDouble { a[it].z * bt[it].z }

    return Matrix33(c0r0, c1r0, c2r0,
            c0r1, c1r1, c2r1,
            c0r2, c1r2, c2r2)
}

private fun Matrix33.toSimpleMatrix(): SimpleMatrix {
    val sm = SimpleMatrix(3, 3)
    sm.set(0, 0, c0r0)
    sm.set(0, 1, c1r0)
    sm.set(0, 2, c2r0)
    sm.set(1, 0, c0r1)
    sm.set(1, 1, c1r1)
    sm.set(1, 2, c2r1)
    sm.set(2, 0, c0r2)
    sm.set(2, 1, c1r2)
    sm.set(2, 2, c2r2)
    return sm
}

private fun SimpleMatrix.toMatrix33(): Matrix33 = Matrix33(
        get(0, 0), get(0, 1), get(0, 2),
        get(1, 0), get(1, 1), get(1, 2),
        get(2, 0), get(2, 1), get(2, 2)
)

private fun cwiseProdSum(a: List<Vector3>, b: List<Vector3>): Double = (a zip b).sumByDouble { (i, j) ->
    i.x * j.x + i.y * j.y + i.z * j.z
}


fun estimatePoseMatrix(sourcePoints: List<Vector3>, targetPoints: List<Vector3>, pointWeights: List<Double>): Matrix44 {
    val sqrtWeights = pointWeights.map { sqrt(it) }
    return internalSolveWeightedOrthogonalProblem(sourcePoints, targetPoints, sqrtWeights)
}

fun internalSolveWeightedOrthogonalProblem(sourcePoints: List<Vector3>, targetPoints: List<Vector3>, sqrtWeights: List<Double>): Matrix44 {
    val weightedSources = sourcePoints.mapIndexed { index, it -> it * sqrtWeights[index] }
    val weightedTarget = targetPoints.mapIndexed { index, it -> it * sqrtWeights[index] }
    val totalWeight = sqrtWeights.sumByDouble { it * it }
    val twiceWeightedSources = weightedSources.mapIndexed { index, it -> it * sqrtWeights[index] }
    val sourceCenterOfMass = twiceWeightedSources.reduce { a, b -> a + b } / totalWeight
    val centeredWeightedSources = weightedSources.mapIndexed { index, it -> it - (sourceCenterOfMass * sqrtWeights[index]) }

    val rotation = computeOptimalRotation(multiplyTransposed(weightedTarget, centeredWeightedSources))
    val scale = computeOptimalScale(centeredWeightedSources, weightedSources, weightedTarget, rotation)

    val rotationAndScale = rotation * scale
    val pointWiseDiffs = weightedTarget.mapIndexed { index, it -> it - rotationAndScale * weightedSources[index] }
    val weightedPointDiffs = pointWiseDiffs.mapIndexed { index, it -> it * sqrtWeights[index] }
    val translation = weightedPointDiffs.reduce { a, b -> a + b } / totalWeight
    return Matrix44.fromColumnVectors(rotationAndScale[0].xyz0, rotationAndScale[1].xyz0, rotationAndScale[2].xyz0, translation.xyz1)
}

private fun computeOptimalRotation(designMatrix: Matrix33): Matrix33 {
    val svd = designMatrix.toSimpleMatrix().svd()
    val postRotation = svd.u
    val preRotation = svd.v.transpose()
    if (postRotation.determinant() * preRotation.determinant() < 0.0) {
        postRotation.set(0, 2, postRotation.get(0, 2) * -1.0)
        postRotation.set(1, 2, postRotation.get(1, 2) * -1.0)
        postRotation.set(2, 2, postRotation.get(2, 2) * -1.0)
    }
    return postRotation.mult(preRotation).toMatrix33()
}

private fun computeOptimalScale(centeredWeightedSources: List<Vector3>, weightedSources: List<Vector3>, weightedTargets: List<Vector3>, rotation: Matrix33): Double {
    val rotatedCenteredWeightedSources = centeredWeightedSources.map { rotation * it }
    val numerator = cwiseProdSum(rotatedCenteredWeightedSources, weightedTargets)
    val denominator = cwiseProdSum(centeredWeightedSources, weightedSources)
    return numerator / denominator
}

fun loadReferenceMesh() : List<Vector3> {
    val triangles = loadOBJEx(URL(resourceUrl("/meshes/canonical_face_model.obj")))
    return triangles.first.positions
}