plugins {
    kotlin("js") version "<pluginMarkerVersion>"
}

repositories {
    mavenLocal()
    jcenter()
}

dependencies {
    implementation(kotlin("stdlib-js"))
    implementation(npm("decamelize", "4.0.0", true))
}

kotlin {
    js(BOTH) {
        nodejs()
    }
}