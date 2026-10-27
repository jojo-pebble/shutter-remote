// Библиотека связи с часами (PebbleKit 2) собрана Kotlin 2.4,
// поэтому поднимаем встроенный в плагин Android Kotlin до 2.4.0.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.0")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
}
