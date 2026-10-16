plugins {
    `java-library`
    `maven-publish`
}

dependencies {
    api(project(":core"))
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("com.github.retrooper:packetevents-spigot:2.14.0")
    compileOnly("io.netty:netty-transport:4.1.72.Final")
    testImplementation("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    testImplementation("com.github.retrooper:packetevents-spigot:2.14.0")
    testImplementation("io.netty:netty-all:4.1.72.Final")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "displaymotion-paper"
            from(components["java"])
        }
    }
}

tasks.compileJava {
    options.release.set(21)
}
