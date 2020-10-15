import org.openrndr.math.Vector3
import org.openrndr.orml.facemesh.estimatePoseMatrix
import org.openrndr.orml.facemesh.loadReferenceMesh


fun main() {
    val points = loadReferenceMesh()
    println(points.size)

    val rotatedPoints = points.map { it + Vector3(1.0, 30.0, 1.0)}


    val m = estimatePoseMatrix(points, rotatedPoints, points.map { 1.0 } )
    println(m)

}