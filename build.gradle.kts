import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.models.ProductRelease
import org.jetbrains.intellij.platform.gradle.tasks.BuildPluginTask
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask.VerificationReportsFormats
import java.time.Duration

plugins {
    kotlin("jvm")
    id("org.jetbrains.intellij.platform")
}

kotlin {
    jvmToolchain(21)
}

val integrationTestSourceSet = sourceSets.create("integrationTest") {
    compileClasspath += sourceSets.main.get().output + configurations.named("intellijPlatformClasspath").get()
    runtimeClasspath += sourceSets.main.get().output + configurations.named("intellijPlatformClasspath").get()
}

configurations.named("integrationTestImplementation") {
    extendsFrom(configurations.named("testImplementation").get())
}
configurations.named("integrationTestRuntimeOnly") {
    extendsFrom(configurations.named("testRuntimeOnly").get())
}

dependencies {
    implementation("com.github.weisj:jsvg:2.2.0")
    testImplementation("junit:junit:4.13.2")
    intellijPlatform {
        intellijIdeaCommunity("2024.3.7.1")
        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.Starter, configurationName = "integrationTestImplementation")
    }
    "integrationTestImplementation"("org.junit.jupiter:junit-jupiter:5.11.4")
    "integrationTestRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}

val verifierChannel = providers.gradleProperty("brandbar.verifierChannel").orElse("stable")

intellijPlatform {
    pluginConfiguration {
        id = "dev.willc.brandbar"
        name = "Brand Bar"
        version = providers.gradleProperty("version")
        ideaVersion {
            sinceBuild = "243"
            untilBuild = provider { null }
        }
    }
    pluginVerification {
        ides {
            when (verifierChannel.get()) {
                "eap" -> latest {
                    types.set(
                        listOf(
                            IntelliJPlatformType.IntellijIdeaCommunity,
                            IntelliJPlatformType.IntellijIdeaUltimate,
                        ),
                    )
                    channels.set(listOf(ProductRelease.Channel.EAP))
                    sinceBuild.set("243")
                }
                "current" -> current()
                else -> {
                    current()
                    latest {
                        types.set(
                            listOf(
                                IntelliJPlatformType.IntellijIdeaCommunity,
                                IntelliJPlatformType.IntellijIdeaUltimate,
                                IntelliJPlatformType.PyCharm,
                                IntelliJPlatformType.WebStorm,
                            ),
                        )
                        channels.set(listOf(ProductRelease.Channel.RELEASE))
                        sinceBuild.set("243")
                    }
                }
            }
        }
        verificationReportsFormats.set(
            listOf(
                VerificationReportsFormats.PLAIN,
                VerificationReportsFormats.HTML,
                VerificationReportsFormats.MARKDOWN,
            ),
        )
    }
}

tasks.named<Test>("test") {
    useJUnit()
    systemProperty("java.awt.headless", "true")
    systemProperty(
        "brandbar.screenshots.dir",
        layout.buildDirectory.dir("reports/screenshots").get().asFile.absolutePath,
    )
    outputs.dir(layout.buildDirectory.dir("reports/screenshots"))
}

val renderScreenshots = tasks.register<JavaExec>("renderScreenshots") {
    group = "verification"
    description = "Renders headless Java2D PNG screenshots of Brand Bar across determinate and indeterminate states."
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("dev.willc.brandbar.ScreenshotsCli")
    jvmArgs("-Djava.awt.headless=true")
    val outputDir = layout.buildDirectory.dir("reports/screenshots")
    outputs.dir(outputDir)
    argumentProviders.add(
        CommandLineArgumentProvider {
            listOf(outputDir.get().asFile.absolutePath)
        },
    )
}

intellijPlatformTesting {
    testIdeUi.register("integrationTest") {
        task {
            testClassesDirs = integrationTestSourceSet.output.classesDirs
            classpath = files(
                integrationTestSourceSet.runtimeClasspath,
                configurations.named("intellijPlatformClasspath"),
            )
            useJUnitPlatform()
            jvmArgs("-Xmx2g")
            systemProperty(
                "brandbar.ide.screenshots.dir",
                layout.buildDirectory.dir("reports/ide-screenshots").get().asFile.absolutePath,
            )
            timeout.set(Duration.ofMinutes(25))
            archiveFile.convention(tasks.named<BuildPluginTask>("buildPlugin").flatMap { it.archiveFile })
        }
    }
}

tasks.matching { it.name == "integrationTest" }.configureEach {
    group = "verification"
    description = "Boots a real IDE under Xvfb with Brand Bar installed, asserts ProgressBarUI registration, and captures an in-IDE screenshot."
}
