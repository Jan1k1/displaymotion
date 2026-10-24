plugins {
    java
    id("com.gradleup.shadow")
}

dependencies {
    implementation(project(":paper"))
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("com.github.retrooper:packetevents-spigot:2.14.0")
    testImplementation("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    testImplementation("com.github.retrooper:packetevents-spigot:2.14.0")
}

tasks.jar {
    archiveClassifier.set("plain")
}

tasks.shadowJar {
    archiveBaseName.set("displaymotion-example")
    archiveClassifier.set("")
}

tasks.processResources {
    inputs.property("version", project.version)
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
