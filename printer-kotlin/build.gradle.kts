plugins {
    kotlin("jvm")
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    compile(kotlin("compiler-embeddable"))
    api("com.github.cretz.kastree:kastree-ast-psi:0.4.0")
    api(project(":doc"))
}