enum class BlasePoseUpperBodyJoint {
    NOSE,               // 0
    RIGHT_EYE_INNER,    // 1
    RIGHT_EYE,          // 2
    RIGHT_EYE_OUTER,    // 3
    LEFT_EYE_INNER,     // 4
    LEFT_EYE,           // 5
    LEFT_EYE_OUTER,     // 6
    RIGHT_EAR,          // 7
    LEFT_EAR,           // 8
    RIGHT_MOUTH,        // 9
    LEFT_MOUTH,         // 10
    RIGHT_SHOULDER,     // 11
    LEFT_SHOULDER,      // 12
    RIGHT_ELBOW,        // 13
    LEFT_ELBOW,         // 14
    RIGHT_WRIST,        // 15
    LEFT_WRIST,         // 16
    RIGHT_PINKY_1,      // 17
    LEFT_PINKY_1,       // 18
    RIGHT_INDEX_1,      // 19
    LEFT_INDEX_1,       // 20
    RIGHT_THUMB_2,      // 21
    LEFT_THUM_2,        // 22
    RIGHT_HIP,          // 23
    LEFT_HIP,           // 24
}

operator fun <T> List<T>.get(joint : BlasePoseUpperBodyJoint): T = this[joint.ordinal]