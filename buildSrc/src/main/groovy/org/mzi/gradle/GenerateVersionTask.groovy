package org.mzi.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

class GenerateVersionTask extends DefaultTask {
  @Input
  Object taskVersion = project.version
  @Input
  String basePackage = project.group
  @Input
  String className = "GeneratedVersion"
  @OutputDirectory
  File outputDir = project.file("src/main/java/org/mzi/prelude")

  @TaskAction
  def run() {
    def code = """\
      package ${basePackage}.prelude;
      import ${basePackage}.util.Version;
      import org.jetbrains.annotations.NotNull;
      public class $className {
        public static final @NotNull String VERSION_STRING = "$taskVersion";
        public static final @NotNull Version VERSION = Version.create(VERSION_STRING);
      }""".stripIndent()
    outputDir.mkdirs()
    def outFile = new File(outputDir, "${className}.java")
    if (!outFile.exists()) assert outFile.createNewFile()
    outFile.write(code)
  }
}
