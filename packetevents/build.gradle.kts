plugins {
    `java-library`
    `maven-publish`
}

dependencies {
    api(project(":core"))
    compileOnly("io.papermc.paper:paper-api:26.3.build.180-stable")
    compileOnly("com.github.retrooper:packetevents-spigot:2.14.0")
    testImplementation("com.github.retrooper:packetevents-spigot:2.14.0")
    testImplementation("io.netty:netty-all:4.1.72.Final")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "displaymotion-packetevents"
            from(components["java"])
        }
    }
}
