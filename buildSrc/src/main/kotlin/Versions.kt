object Versions {
    var orxUseSnapshot = true
    var openrndrUseSnapshot = true
    var orx = if (orxUseSnapshot) "0.5.1-SNAPSHOT" else "0.4.0-rc.8"
    var openrndr = if (openrndrUseSnapshot) "0.5.1-SNAPSHOT" else "0.4.0-rc.7"
    val kotlin = "1.6.10"
}