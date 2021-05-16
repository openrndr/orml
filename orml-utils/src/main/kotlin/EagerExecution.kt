package org.openrndr.orml.utils

import org.tensorflow.EagerSession
import org.tensorflow.op.Ops

fun <T> eager(f: Ops.() -> T): T {
    val session = EagerSession.options().async(false).build()
    val tf = Ops.create(session)
    val scope = tf.scope()
    val ops = Ops.create(scope.env())

    val retval = ops.f()


    session.close()
    return retval
}

