/*
 * Copyright (c) 2013, Inversoft Inc., All Rights Reserved
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */
package org.savantbuild.plugin.groovy

import org.savantbuild.dep.DependencyService.ResolveConfiguration
import org.savantbuild.dep.domain.ArtifactID
import org.savantbuild.domain.Project
import org.savantbuild.io.FileTools
import org.savantbuild.lang.Classpath
import org.savantbuild.output.Output

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * The Groovy plugin. The public methods on this class define the features of the plugin.
 */
class GroovyPlugin extends Plugin {
  public static final String ERROR_MESSAGE = "You must create the file [%s] " +
      "that contains the system configuration for the Groovy plugin. This file should include the location of the GDK " +
      "(groovy and groovyc) by version. These properties look like this:\n\n" +
      "  2.1=/Library/Groovy/Versions/2.1/Home\n" +
      "  2.2=/Library/Groovy/Versions/2.2/Home\n"
  public static final String JAVA_ERROR_MESSAGE = "You must create the file [%s] " +
      "that contains the system configuration for the Java system. This file should include the location of the JDK " +
      "(java and javac) by version. These properties look like this:\n\n" +
      "  1.6=/Library/Java/JavaVirtualMachines/1.6.0_65-b14-462.jdk/Contents/Home\n" +
      "  1.7=/Library/Java/JavaVirtualMachines/jdk1.7.0_10.jdk/Contents/Home\n" +
      "  1.8=/Library/Java/JavaVirtualMachines/jdk1.8.0.jdk/Contents/Home\n"
  GroovyLayout layout = new GroovyLayout()
  GroovySettings settings = new GroovySettings()
  Properties properties
  Properties javaProperties
  Path groovycPath
  String javaHome
  FilePlugin filePlugin
  DependencyPlugin dependencyPlugin

  GroovyPlugin(Project project, Output output) {
    super(project, output)
    filePlugin = new FilePlugin(project, output)
    dependencyPlugin = new DependencyPlugin(project, output)
    properties = loadConfiguration(new ArtifactID("org.savantbuild.plugin", "groovy", "groovy", "jar"), ERROR_MESSAGE)
    javaProperties = loadConfiguration(new ArtifactID("org.savantbuild.plugin", "java", "java", "jar"), JAVA_ERROR_MESSAGE)
  }

  /**
   * Cleans the build directory by completely deleting it.
   */
  void clean() {
    Path buildDir = project.directory.resolve(layout.buildDirectory)
    output.info "Cleaning [${buildDir}]"
    FileTools.prune(buildDir)
  }

  /**
   * Compiles the main Groovy files (src/main/groovy by default).
   */
  void compileMain() {
    initialize()
    compile(layout.mainSourceDirectory, layout.mainBuildDirectory, settings.mainDependencyResolveConfiguration)
    copyResources(layout.mainResourceDirectory, layout.mainBuildDirectory)
  }

  /**
   * Compiles the test Groovy files (src/test/groovy by default).
   */
  void compileTest() {
    initialize()
    compile(layout.testSourceDirectory, layout.testBuildDirectory, settings.testDependencyResolveConfiguration, layout.mainBuildDirectory)
    copyResources(layout.testResourceDirectory, layout.testBuildDirectory)
  }

  /**
   * Compiles an arbitrary source directory to an arbitrary build directory.
   *
   * @param sourceDirectory The source directory that contains the groovy source files.
   * @param buildDirectory The build directory to compile the groovy files to.
   * @param resolveConfiguration The ResolveConfiguration for building the classpath from the project's depenedencies.
   */
  void compile(Path sourceDirectory, Path buildDirectory, ResolveConfiguration resolveConfiguration, Path... additionalClasspath) {
    List<String> filesToCompile = FileTools.modifiedFiles(project.directory, sourceDirectory, buildDirectory, ".groovy")
    if (filesToCompile.isEmpty()) {
      output.info("Skipping compile. No files need compiling")
      return
    }

    output.info "Compiling [${filesToCompile.size()}] Groovy classes from [${sourceDirectory}] to [${buildDirectory}]"

    String command = "${groovycPath} ${settings.compilerArguments} ${classpath(resolveConfiguration, additionalClasspath)} --sourcepath ${sourceDirectory} -d ${buildDirectory} ${filesToCompile.join(" ")}"
    Files.createDirectories(project.directory.resolve(buildDirectory))
    Process process = command.execute(["JAVA_HOME=${javaHome}"], project.directory.toFile())
    process.consumeProcessOutput((Appendable) System.out, System.err)
    process.waitFor()

    int exitCode = process.exitValue()
    if (exitCode != 0) {
      fail("Compilation failed")
    }
  }

  /**
   * Copies the resource files from the source directory to the build directory. This copies all of the files
   * recursively to the build directory.
   *
   * @param sourceDirectory The source directory that contains the files to copy.
   * @param buildDirectory The build directory to copy the files to.
   */
  void copyResources(Path sourceDirectory, Path buildDirectory) {
    filePlugin.copy {
      to(buildDirectory)
      fileSet(sourceDirectory)
    }
  }

  void jar() {
    initialize()

    jar(project.toArtifact().getArtifactFile(), layout.mainBuildDirectory)
    jar(project.toArtifact().getArtifactSourceFile(), layout.mainSourceDirectory, layout.mainResourceDirectory)
    jar(project.toArtifact().getArtifactTestFile(), layout.testBuildDirectory)
    jar(project.toArtifact().getArtifactTestSourceFile(), layout.testSourceDirectory, layout.testResourceDirectory)
  }

  void jar(String jarFile, Path... directories) {
    Path jarFilePath = layout.jarOutputDirectory.resolve(jarFile)

    output.info "Creating JAR [${jarFile}]"

    filePlugin.jar(jarFilePath) {
      directories.each {dir -> fileSet(dir)}
    }
  }

  private String classpath(ResolveConfiguration resolveConfiguration, Path... paths) {
    Classpath classpath = dependencyPlugin.classpath(resolveConfiguration)
    classpath.addAll(paths)
    return classpath.toString("-classpath ")
  }

  private void initialize() {
    if (!settings.groovyVersion) {
      fail("You must configure the Groovy version to use with the settings object. It will look something like this:\n\n" +
          "  groovy.settings.groovyVersion=\"2.1\"")
    }

    String groovyHome = properties.getProperty(settings.groovyVersion)
    if (!groovyHome) {
      fail("No GDK is configured for version [${settings.groovyVersion}].\n\n" + ERROR_MESSAGE)
    }

    groovycPath = Paths.get(groovyHome, "bin/groovyc")
    if (!Files.isRegularFile(groovycPath)) {
      fail("The groovyc compiler [${groovycPath.toAbsolutePath()}] does not exist.")
    }
    if (!Files.isExecutable(groovycPath)) {
      fail("The groovyc compiler [${groovycPath.toAbsolutePath()}] is not executable.")
    }

    if (!settings.javaVersion) {
      fail("You must configure the Java version to use with the settings object. It will look something like this:\n\n" +
          "  groovy.settings.javaVersion=\"1.7\"")
    }

    javaHome = javaProperties.getProperty(settings.javaVersion)
    if (!javaHome) {
      fail("No JDK is configured for version [${settings.javaVersion}].\n\n" + JAVA_ERROR_MESSAGE)
    }
  }
}
