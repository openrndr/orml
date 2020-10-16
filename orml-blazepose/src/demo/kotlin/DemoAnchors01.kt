fun main() {

    val options = AnchorOptions()
    val anchors = generateAnchors(options)
    anchors.forEach {
        println(it)
    }
    println("a total of ${anchors.size} anchors")

}