import org.gradle.internal.os.OperatingSystem

object Configuration {
    val openrndrOS: String
    get() = when (OperatingSystem.current()) {
        org.gradle.internal.os.OperatingSystem.WINDOWS -> "windows"
        org.gradle.internal.os.OperatingSystem.MAC_OS -> "macos"
        org.gradle.internal.os.OperatingSystem.LINUX -> when (val h =
            org.gradle.nativeplatform.platform.internal.DefaultNativePlatform(
                "current"
            ).architecture.name) {
            "x86-64" -> "linux-x64"
            "aarch64" -> "linux-arm64"
            else -> error("architecture not supported: $h")
        }
        else -> error("os not supported")
    }
}