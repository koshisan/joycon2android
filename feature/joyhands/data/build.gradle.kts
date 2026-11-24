plugins {
    id("joycon.kotlin.jvm")
}

dependencies {
    implementation(project(":feature:joyhands:domain"))
    testImplementation(libs.junit)
}
