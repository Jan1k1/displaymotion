plugins {
    java
    id("com.gradleup.shadow")
}

dependencies {
    implementation(project(":paper"))
    implementation("com.github.retrooper:packetevents-spigot:2.14.0") {
        // paper ships adventure
        exclude(group = "net.kyori")
    }
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
}

tasks.jar {
    archiveClassifier.set("plain")
}

tasks.shadowJar {
    archiveBaseName.set("DisplayMotion")
    archiveClassifier.set("")
    // our plugin.yml comes first, the one inside packetevents is dropped
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    relocate("com.github.retrooper.packetevents", "org.jan1k.displaymotion.libs.packetevents.api")
    relocate("io.github.retrooper.packetevents", "org.jan1k.displaymotion.libs.packetevents.impl")
}

tasks.shadowJar {
    from("LICENSE") {
        into("META-INF")
    }
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

tasks.compileJava {
    options.release.set(21)
}
