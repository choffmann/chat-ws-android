plugins {
    id("com.android.library") version "8.13.0"
    kotlin("android") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    `maven-publish`
}

group = "de.hsfl.mobilecomputing"
version = "0.1.0"

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
        create<MavenPublication>("release") {
            pom {
                name = "chat-ws-android"
                description = "Ktor WebSocket client for the chat-room server"
                url = "http://github.com/choffmann/chat-ws-android"
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
                    connection = "scm:git:git://github.com/choffmann/chat-ws-android.git"
                    developerConnection = "scm:git:ssh://github.com/choffmann/chat-ws-android.git"
                    url = "http://github.com/choffmann/chat-ws-android"
                }
            }
        }
    }
    repositories {
        maven {
            name = "OSSRH"
            url = uri("https://oss.sonatype.org/service/local/staging/deploy/maven2/")
            credentials {
                username = System.getenv("MAVEN_USERNAME")
                password = System.getenv("MAVEN_PASSWORD")
            }
        }

        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/choffmann/chat-ws-android")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
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

