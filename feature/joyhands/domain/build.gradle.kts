plugins {
    id("joycon.kotlin.jvm")
}

dependencies {
    api(project(":core:model"))
    testImplementation(libs.junit)
}
