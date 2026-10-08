import java.util.zip.ZipFile

plugins {
    java
    id("com.gradleup.shadow") version "9.4.3"
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    implementation("org.apache.poi:poi-ooxml:5.5.1")
    implementation("org.apache.poi:poi-scratchpad:5.5.1")
    implementation("com.fasterxml:aalto-xml:1.3.3")
    implementation("stax:stax-api:1.0.1")
    implementation("org.apache.logging.log4j:log4j-api:2.26.1")
}

// Android lacks StAX and the AWT types referenced by POI's model API. Relocate
// those references together with the small upstream compatibility adapters.
tasks.shadowJar {
    archiveClassifier.set("android")
    mergeServiceFiles()
    // Downloader dependencies also use Commons; keep POI's required versions isolated.
    relocate("org.apache.commons", "com.amaankhan.office.shaded.commons")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    // Service descriptors must reach the transformer; EXCLUDE otherwise drops
    // the HSSF provider when the OOXML jar declares the same service first.
    filesMatching("META-INF/services/**") { duplicatesStrategy = DuplicatesStrategy.INCLUDE }
    exclude("META-INF/versions/**", "module-info.class")
    exclude("META-INF/services/javax.xml.stream.*")
    relocate("javax.xml.namespace", "org.apache.poi.javax.xml.namespace")
    relocate("javax.xml.stream", "org.apache.poi.javax.xml.stream")
    relocate("javax.xml.XMLConstants", "org.apache.poi.javax.xml.XMLConstants")
    listOf("font.FontRenderContext", "font.TextLayout", "geom.AffineTransform", "geom.Dimension2D", "geom.Rectangle2D", "image.BufferedImage", "Color", "Dimension").forEach {
        relocate("java.awt.$it", "org.apache.poi.java.awt.$it")
    }
    listOf("ImageIO", "ImageReader", "stream.ImageInputStream", "metadata.IIOMetadata").forEach {
        relocate("javax.imageio.$it", "org.apache.poi.javax.imageio.$it")
    }
}

val androidRuntime by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}
artifacts { add(androidRuntime.name, tasks.shadowJar) }

val generateOfficeNotices by tasks.registering {
    val runtime = configurations.runtimeClasspath
    val destination = layout.buildDirectory.file("generated-notices/office-notices.txt")
    inputs.files(runtime)
    outputs.file(destination)
    doLast {
        val text = buildString {
            appendLine("Office runtime: Apache POI and poi-on-android compatibility adapters")
            appendLine("poi-on-android: Copyright 2015-2024 Dominik Stadler, Apache License 2.0.")
            appendLine("Source: https://github.com/centic9/poi-on-android/tree/8e3cf66813feca0eef8283f0865145b4a80c02ee")
            appendLine("Color and size value adapters are Toolkit implementations. Other adapters retain their source under office-runtime/src.")
            runtime.get().files.sortedBy { it.name }.forEach { artifact ->
                appendLine("\n--- ${artifact.name} ---")
                ZipFile(artifact).use { zip ->
                    zip.entries().asSequence().filter { entry ->
                        val leaf = entry.name.substringAfterLast('/').uppercase()
                        !entry.isDirectory && listOf("LICENSE", "NOTICE", "COPYING").any { leaf.startsWith(it) }
                    }.forEach { entry -> appendLine(entry.name); appendLine(zip.getInputStream(entry).bufferedReader().use { it.readText() }) }
                }
            }
        }
        destination.get().asFile.apply { parentFile.mkdirs(); writeText(text) }
    }
}
