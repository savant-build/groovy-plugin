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

import org.savantbuild.dep.DefaultDependencyService
import org.savantbuild.dep.DependencyService
import org.savantbuild.dep.domain.ArtifactID
import org.savantbuild.dep.graph.ResolvedArtifactGraph
import org.savantbuild.io.FileTools
import org.savantbuild.plugin.AbstractPlugin

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths


/**
 * Layout class that defines the directories used by the Groovy plugin.
 */
class GroovyLayout {
  def buildDirectory = Paths.get("build")
  def mainSourceDirectory = Paths.get("src/main/groovy")
  def mainResourceDirectory = Paths.get("src/main/resources")
  def mainBuildDirectory = buildDirectory.resolve("classes/main")
  def testSourceDirectory = Paths.get("src/test/groovy")
  def testResourceDirectory = Paths.get("src/test/resources")
  def testBuildDirectory = buildDirectory.resolve("classes/test")
}

/**
 * Settings class that defines the settings used by the Groovy plugin.
 */
class GroovySettings {
  def groovyVersion
  def javaVersion
  def compilerArguments = ""
  def mainDependencyResolveConfiguration = new DependencyService.ResolveConfiguration()
      .with("compile", new DependencyService.ResolveConfiguration.TypeResolveConfiguration(true, false))
  def testDependencyResolveConfiguration = new DependencyService.ResolveConfiguration()
      .with("test-compile", new DependencyService.ResolveConfiguration.TypeResolveConfiguration(true, false))
}

/**
 * The Groovy plugin. The public methods on this class define the features of the plugin.
 */
class GroovyPlugin extends AbstractPlugin {
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
  def layout = new GroovyLayout()
  def settings = new GroovySettings()
  def properties
  def javaProperties
  def groovyHome
  def groovycPath
  def javaHome
  def javacPath
  def jarPath

  def GroovyPlugin(project, output) {
    super(project, output)

    properties = loadConfiguration(new ArtifactID("org.savantbuild.plugin", "groovy", "groovy", "jar"), ERROR_MESSAGE)
    javaProperties = loadConfiguration(new ArtifactID("org.savantbuild.plugin", "java", "java", "jar"), JAVA_ERROR_MESSAGE)
  }

  /**
   * Cleans the build directory by completely deleting it.
   */
  def clean() {
    Path buildDir = project.directory.resolve(layout.buildDirectory)
    output.info "Cleaning [${buildDir}]"
    FileTools.prune(buildDir)
  }

  /**
   * Compiles the main Groovy files (src/main/groovy by default).
   */
  def compileMain() {
    initialize()
    compile(layout.mainSourceDirectory, layout.mainBuildDirectory, settings.mainDependencyResolveConfiguration)
    copyResources(layout.mainResourceDirectory, layout.mainBuildDirectory)
  }

  /**
   * Compiles the test Groovy files (src/test/groovy by default).
   */
  def compileTest() {
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
  def compile(sourceDirectory, buildDirectory, resolveConfiguration, Path... additionalClasspath) {
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
  def copyResources(sourceDirectory, buildDirectory) {
    FileTools.copyRecursive(Files.list(project.directory.resolve(sourceDirectory)), project.directory.resolve(buildDirectory))
  }

  def jar() {
    initialize()

    Path jarDir = layout.buildDirectory.resolve("jars")
    Path jarFile = jarDir.resolve(project.toArtifact().getArtifactFile())
    jar(jarFile, layout.mainBuildDirectory)
    Path sourceJarFile = jarDir.resolve(project.toArtifact().getArtifactSourceFile())
    jar(sourceJarFile, layout.mainSourceDirectory)

    Path testJarFile = jarDir.resolve(project.toArtifact().getArtifactTestFile())
    jar(testJarFile, layout.testBuildDirectory)
    Path testSourceJarFile = jarDir.resolve(project.toArtifact().getArtifactTestSourceFile())
    jar(testSourceJarFile, layout.testSourceDirectory)
  }

  def jar(jarFile, directory) {
    output.info "Creating JAR [${jarFile}] from build directory [${directory}]"

    Files.createDirectories(project.directory.resolve(jarFile).getParent())

    String command = "${jarPath} cf ${jarFile} ${directory}"
    println command
    Process process = command.execute(["JAVA_HOME=${javaHome}"], project.directory.toFile())
    process.consumeProcessOutput((Appendable) System.out, System.err)
    process.waitFor()

    int exitCode = process.exitValue()
    if (exitCode != 0) {
      fail("Build failed")
    }
  }

  private String classpath(DependencyService.ResolveConfiguration resolveConfiguration, Path... paths) {
    if (!project.dependencies) {
      return ""
    }

    DependencyService service = new DefaultDependencyService(output)
    if (!project.artifactGraph) {
      def dependencyGraph = service.buildGraph(project.toArtifact(), project.dependencies, project.workflow)
      project.artifactGraph = service.reduce(dependencyGraph)
    }

    ResolvedArtifactGraph resolvedArtifactGraph = service.resolve(project.artifactGraph, project.workflow, resolveConfiguration)
    if (resolvedArtifactGraph.size() == 0) {
      return ""
    }

    String classpath = "-classpath ${resolvedArtifactGraph.toClasspath()}"
    if (paths.length > 0) {
      classpath += File.pathSeparator + paths.join(File.pathSeparator)
    }

    return classpath
  }

  private void initialize() {
    if (!settings.groovyVersion) {
      fail("You must configure the Groovy version to use with the settings object. It will look something like this:\n\n" +
          "  groovy.settings.groovyVersion=\"2.1\"")
    }

    groovyHome = properties.getProperty(settings.groovyVersion)
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

    javacPath = Paths.get(javaHome, "bin/javac")
    if (!Files.isRegularFile(javacPath)) {
      fail("The javac compiler [${javacPath.toAbsolutePath()}] does not exist.")
    }
    if (!Files.isExecutable(javacPath)) {
      fail("The javac compiler [${javacPath.toAbsolutePath()}] is not executable.")
    }

    jarPath = Paths.get(javaHome, "bin/jar")
    if (!Files.isRegularFile(jarPath)) {
      fail("The jar utility [${jarPath.toAbsolutePath()}] does not exist.")
    }
    if (!Files.isExecutable(jarPath)) {
      fail("The jar utility [${jarPath.toAbsolutePath()}] is not executable.")
    }
  }
}
