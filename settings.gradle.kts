rootProject.name = "transaction_manager"

// when running the assemble task, ignore the android & graalvm related subprojects
if (startParameter.taskRequests.find { it.args.contains("assemble") } == null) {
    include("protos", "stub", "server")
} else {
    include("protos", "stub", "server")
}

pluginManagement {
    repositories {
        gradlePluginPortal()
        jcenter()
        google()
    }
}
