plugins {
    `java-library`
    `maven-publish`
}

dependencies {
    testImplementation("org.joml:joml:1.10.8")
}

tasks.compileJava {
    options.release.set(21)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "displaymotion-core"
            from(components["java"])
        }
    }
}
