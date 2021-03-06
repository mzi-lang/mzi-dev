// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
dependencies {
  api(project(":api"))
  implementation(project(":pretty"))
  implementation(project(":parser"))
  testImplementation(project(":tester"))
}

val genDir = file("src/main/gen")
val generateVersion = tasks.register<org.aya.gradle.GenerateVersionTask>("generateVersion") {
  outputDir = genDir.resolve("${project.group.toString().split(".").joinToString("/")}/prelude")
}

idea {
  module.generatedSourceDirs.add(genDir)
}

sourceSets.main {
  java.srcDirs(genDir)
}

tasks.compileJava {
  dependsOn(generateVersion)
}

val cleanGenerated = tasks.register("cleanGenerated") {
  group = "build"
  genDir.deleteRecursively()
}

tasks.named("clean").configure { dependsOn(cleanGenerated) }
