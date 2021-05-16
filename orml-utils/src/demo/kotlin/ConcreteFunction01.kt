import org.tensorflow.ConcreteFunction
import org.tensorflow.Graph
import org.tensorflow.Session
import org.tensorflow.Signature
import org.tensorflow.op.Ops
import org.tensorflow.types.TFloat32

fun main() {

    val g = Graph()
    val tf = Ops.create(g)
    val input = tf.placeholder(TFloat32::class.java)
    val output = tf.math.add(input, tf.constant(2.0f))
    val signature = Signature.builder()
        .input("x", input).output("y", output).build()

    val session = Session(g)


    val cf = ConcreteFunction.create(signature, session)

    val x = TFloat32.scalarOf(2.0f)

    val res = cf.call(x) as TFloat32
    println(res.getFloat())
    val res2 = cf.call(res) as TFloat32
    println(res2.getFloat())

}