plugins {
    java
    id("com.gradleup.shadow")
}

dependencies {
    implementation(project(":paper"))
    compileOnly("io.papermc.paper:paper-api:26.3.build.180-stable")
    compileOnly("com.github.retrooper:packetevents-spigot:2.14.0")
}

tasks.jar {
    archiveClassifier.set("plain")
}

tasks.shadowJar {
    archiveBaseName.set("displaymotion-example")
    archiveClassifier.set("")
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}
