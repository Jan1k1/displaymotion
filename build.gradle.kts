plugins {
    id("com.gradleup.shadow") version "9.6.1" apply false
}

allprojects {
    // jitpack serves the modules as com.github.Jan1k1.displaymotion:<module>
    group = if (System.getenv("JITPACK") == "true") "com.github.Jan1k1.displaymotion" else "org.jan1k"
    version = if (System.getenv("JITPACK") == "true") System.getenv("VERSION") ?: "0.1.0-SNAPSHOT" else "0.1.0-SNAPSHOT"
}

subprojects {
    repositories {
        mavenCentral()
        maven(url = "https://repo.papermc.io/repository/maven-public/") {
            name = "papermc"
        }
        maven(url = "https://repo.codemc.io/repository/maven-releases/") {
            name = "codemc"
        }
    }

    plugins.withType<JavaPlugin> {
        extensions.configure<JavaPluginExtension> {
            toolchain.languageVersion.set(JavaLanguageVersion.of(25))
        }

        dependencies {
            "testImplementation"(platform("org.junit:junit-bom:5.10.0"))
            "testImplementation"("org.junit.jupiter:junit-jupiter")
            "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
        }

        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }
    }
}
