plugins {
    id("joycon.kotlin.jvm")
}

dependencies {
    implementation(project(":feature:joyhands:domain"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}
