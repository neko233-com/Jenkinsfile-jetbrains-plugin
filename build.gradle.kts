import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    java
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "com.neko233"
version = "1.0.5"

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    intellijPlatform {
        val localPlatform = providers.gradleProperty("platformPath")
        if (localPlatform.isPresent) {
            local(localPlatform.get())
        } else {
            create("GO", "2026.2.1")
        }
        pluginVerifier()
        testFramework(TestFrameworkType.Platform)
    }
    implementation("org.apache.groovy:groovy:4.0.33")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.opentest4j:opentest4j:1.3.0")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
    options.encoding = "UTF-8"
}

intellijPlatform {
    pluginConfiguration {
        version.set(project.version.toString())
        ideaVersion {
            sinceBuild.set("243")
            untilBuild.set("262.*")
        }
    }
    pluginVerification {
        ides {
            if (providers.gradleProperty("platformPath").isPresent) {
                local(file(providers.gradleProperty("platformPath").get()))
            } else {
                create(org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.GoLand, "2026.2.1.1")
            }
            if (!providers.gradleProperty("skipIdeaVerifier").isPresent) {
                create(org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.IntellijIdea, "2026.2.1")
            }
        }
    }
}
