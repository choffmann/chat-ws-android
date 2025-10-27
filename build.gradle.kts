import java.util.Base64

plugins {
    id("com.android.library") version "8.13.0"
    kotlin("android") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    `maven-publish`
    signing
}

group = "io.github.choffmann"
version = "0.1.0"

val libraryArtifactId = "chat-ws-android"
val projectUrl = "https://github.com/choffmann/chat-ws-android"

android {
    namespace = "de.hsfl.mobilecomputing.chatws"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("proguard-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    publishing { singleVariant("release") { withSourcesJar(); withJavadocJar() } }
}

publishing {
    publications {
        register<MavenPublication>("release") {
            afterEvaluate { from(components["release"]) }
            artifactId = libraryArtifactId
            pom {
                name = libraryArtifactId
                description = "Ktor WebSocket client for the chat-room server"
                url = projectUrl
                licenses {
                    license {
                        name = "MIT"
                        url = "https://opensource.org/license/mit"
                    }
                }
                developers {
                    developer {
                        id = "choffmann"
                        name = "Cedrik Hoffmann"
                        email = "cedrik.hoffmann@hs-flensburg.de"
                    }
                }
                scm {
                    connection = "scm:git:$projectUrl.git"
                    developerConnection = "scm:git:ssh://git@github.com/choffmann/chat-ws-android.git"
                    url = projectUrl
                }
            }
        }
    }
    repositories {
        maven {
            name = "OSSRH"
            url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            mavenContent { releasesOnly() }
            credentials {
                username = System.getenv("OSSRH_USERNAME") ?: findProperty("ossrhUsername") as String?
                password = System.getenv("OSSRH_PASSWORD") ?: findProperty("ossrhPassword") as String?
            }
        }
        maven {
            name = "OSSRHSnapshots"
            url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
            mavenContent { snapshotsOnly() }
            credentials {
                username = System.getenv("OSSRH_USERNAME") ?: findProperty("ossrhUsername") as String?
                password = System.getenv("OSSRH_PASSWORD") ?: findProperty("ossrhPassword") as String?
            }
        }
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/choffmann/chat-ws-android")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: System.getenv("GITHUB_USERNAME")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

signing {
    val signingKey = System.getenv("SIGNING_KEY") ?: findProperty("signing.key") as String?
    val signingPassword = System.getenv("SIGNING_PASSWORD") ?: findProperty("signing.password") as String?
    val resolvedSigningKey = signingKey
        ?.trim()
        ?.let { encoded ->
            when {
                encoded.contains("BEGIN PGP PRIVATE KEY BLOCK") -> encoded
                else -> runCatching { String(Base64.getDecoder().decode(encoded)) }.getOrNull()
            }
        }

    if (!resolvedSigningKey.isNullOrBlank() && !signingPassword.isNullOrBlank()) {
        useInMemoryPgpKeys(resolvedSigningKey, signingPassword)
        sign(publishing.publications["release"])
    } else {
        logger.warn("Signing disabled: GPG key or password missing.")
    }
}

val ktor = "3.3.1"
dependencies {
    api("io.ktor:ktor-client-core:$ktor")
    api("io.ktor:ktor-client-okhttp:$ktor")
    api("io.ktor:ktor-client-websockets:$ktor")
    api("io.ktor:ktor-client-content-negotiation:$ktor")
    api("io.ktor:ktor-serialization-kotlinx-json:$ktor")
    api("io.ktor:ktor-client-logging:$ktor")

    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    api("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
}
