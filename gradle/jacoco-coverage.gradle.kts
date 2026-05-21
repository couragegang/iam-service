import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport

extensions.findByType<JacocoPluginExtension>()?.toolVersion = "0.8.12"

@Suppress("UNCHECKED_CAST")
val jacocoCoverageExcludes: List<String> =
    (findProperty("jacocoCoverageExcludes") as? List<String>)
        ?: listOf(
            "**/api/dto/**",
            "**/repo/**",
            "**/Application.class",
            "**/config/JacksonFactory.class",
        )

tasks.withType<Test> {
    finalizedBy(tasks.named("jacocoTestReport"))
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.named<Test>("test"))
    val mainClasses = layout.buildDirectory.get().asFile.resolve("classes/java/main")
    classDirectories.setFrom(
        fileTree(mainClasses) {
            exclude(jacocoCoverageExcludes)
        },
    )
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.named<Test>("test"))
    val mainClasses = layout.buildDirectory.get().asFile.resolve("classes/java/main")
    classDirectories.setFrom(
        fileTree(mainClasses) {
            exclude(jacocoCoverageExcludes)
        },
    )
    violationRules {
        rule {
            limit {
                counter = "BRANCH"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

tasks.named("check") {
    dependsOn(tasks.named("jacocoTestCoverageVerification"))
}
