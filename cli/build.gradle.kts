plugins {
    kotlin("jvm")
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(project(":doc"))
    implementation("com.github.cretz.kastree:kastree-ast-psi:0.4.0")
}