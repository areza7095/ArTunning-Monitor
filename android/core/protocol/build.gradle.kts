import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Library protokol murni JVM — dikonsumsi modul Android, jadi bytecode harus kompatibel D8.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

tasks.test {
    useJUnit()
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = false
    }
}

// ---------------------------------------------------------------------------
// Codegen: docs/opcodes.json -> build/generated/opcodes/.../Opcodes.kt
// Sumber kebenaran = docs/opcodes.json (monorepo). Jangan edit hasil generate.
// ---------------------------------------------------------------------------
val opcodesJson: File = rootProject.layout.projectDirectory.file("../docs/opcodes.json").asFile
val genOpcodesScript: File = file("scripts/gen-opcodes.ps1")
val genOpcodesOutDir: Directory = layout.buildDirectory.dir("generated/opcodes").get()

val generateOpcodes: TaskProvider<Exec> = tasks.register<Exec>("generateOpcodes") {
    group = "build"
    description = "Generate Opcodes.kt from docs/opcodes.json"
    inputs.files(opcodesJson, genOpcodesScript)
    outputs.dir(genOpcodesOutDir)
    commandLine(
        "powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
        "-File", genOpcodesScript.absolutePath,
        "-JsonPath", opcodesJson.absolutePath,
        "-OutDir", genOpcodesOutDir.asFile.absolutePath
    )
}

sourceSets {
    main {
        kotlin.srcDir(generateOpcodes)
    }
}

dependencies {
    testImplementation(libs.junit)
}
