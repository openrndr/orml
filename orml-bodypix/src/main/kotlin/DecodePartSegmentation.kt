
package org.openrndr.orml.bodypix


import org.tensorflow.ndarray.Shape

private fun Shape.last(): Long {
    return size(this.numDimensions()-1)
}
