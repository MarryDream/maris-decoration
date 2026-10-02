import dev.kikugie.stonecutter.controller.flag.StonecutterFlag

plugins { id("dev.kikugie.stonecutter") }

// Source ownership is explicit in gradle/stonecutter-sources.gradle.
stonecutter.flags[StonecutterFlag.APPEND_SOURCES_AFTER_EVAL] = false
stonecutter active "1.20.1-fabric"

stonecutter parameters {
    val loader = current.project.substringAfterLast('-')
    constants { match(loader, "fabric", "forge", "neoforge") }
}

tasks.register("buildActive") {
    group = "build"
    description = "Build only the active Stonecutter node."
    dependsOn(stonecutter.tasks.named("build") { metadata.isActive })
}
tasks.register("buildAll") {
    group = "build"
    description = "Build every target; fails explicitly while scaffold nodes remain."
    dependsOn(stonecutter.tasks.named("build"))
}
// Keep the existing root commands focused on the active target.
for (name in listOf("build", "runClient", "runServer", "runDatagen", "checkDatagenFresh",
        "compileJava", "compileClientJava", "runTeakTest", "runTeakClientCheck")) {
    tasks.register(name) {
        group = if (name.startsWith("run")) "application" else "build"
        dependsOn(stonecutter.tasks.named(name) { metadata.isActive })
    }
}
