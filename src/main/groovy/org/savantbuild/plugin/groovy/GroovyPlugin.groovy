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

import org.savantbuild.dep.domain.ArtifactID
import org.savantbuild.domain.Project
import org.savantbuild.io.FileTools
import org.savantbuild.output.Output
import org.savantbuild.plugin.dep.DependencyPlugin
import org.savantbuild.plugin.file.FilePlugin

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.function.Function
import java.util.function.Predicate

/**
 * The Groovy plugin. The public methods on this class define the features of the plugin.
 */
class GroovyPlugin extends BaseGroovyPlugin {
  public static final String ERROR_MESSAGE = "You must create the file [~/.savant/plugins/org.savantbuild.plugin.groovy.properties] " +
      "that contains the system configuration for the Groovy plugin. This file should include the location of the GDK " +
      "(groovy and groovyc) by version. These properties look like this:\n\n" +
      "  2.1=/Library/Groovy/Versions/2.1/Home\n" +
      "  2.2=/Library/Groovy/Versions/2.2/Home\n"
  public static final String JAVA_ERROR_MESSAGE = "You must create the file [~/.savant/plugins/org.savantbuild.plugin.java.properties] " +
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
   * <p/>
   * Here is an example of calling this method:
   * <p/>
   * <pre>
   *   groovy.clean()
   * </pre>
   */
  void clean() {
    Path buildDir = project.directory.resolve(layout.buildDirectory)
    output.info "Cleaning [${buildDir}]"
    FileTools.prune(buildDir)
  }

  /**
   * Compiles the main and test Java files (src/main/groovy and src/test/groovy).
   * <p/>
   * Here is an example of calling this method:
   * <p/>
   * <pre>
   *   groovy.compile()
   * </pre>
   */
  void compile() {
    compileMain()
    compileTest()
  }

  /**
   * Compiles the main Groovy files (src/main/groovy by default).
   * <p/>
   * Here is an example of calling this method:
   * <p/>
   * <pre>
   *   groovy.compileMain()
   * </pre>
   */
  void compileMain() {
    initialize()
    compile(layout.mainSourceDirectory, layout.mainBuildDirectory, settings.mainDependencies, layout.mainBuildDirectory)
    copyResources(layout.mainResourceDirectory, layout.mainBuildDirectory)
  }

  /**
   * Compiles the test Groovy files (src/test/groovy by default).
   * <p/>
   * Here is an example of calling this method:
   * <p/>
   * <pre>
   *   groovy.compileTest()
   * </pre>
   */
  void compileTest() {
    initialize()
    compile(layout.testSourceDirectory, layout.testBuildDirectory, settings.testDependencies, layout.mainBuildDirectory, layout.testBuildDirectory)
    copyResources(layout.testResourceDirectory, layout.testBuildDirectory)
  }

  /**
   * Compiles an arbitrary source directory to an arbitrary build directory.
   * <p/>
   * Here is an example of calling this method:
   * <p/>
   * <pre>
   *   groovy.compile(Paths.get("src/foo"), Paths.get("build/bar"), [[group: "compile", transitive: false, fetchSource: false]], Paths.get("additionalClasspathDirectory"))
   * </pre>
   *
   * @param sourceDirectory The source directory that contains the groovy source files.
   * @param buildDirectory The build directory to compile the groovy files to.
   * @param dependencies The dependencies of the project to include in the compile classpath.
   */
  void compile(Path sourceDirectory, Path buildDirectory, List<Map<String, Object>> dependencies, Path... additionalClasspath) {
    Path resolvedSourceDir = project.directory.resolve(sourceDirectory)
    Path resolvedBuildDir = project.directory.resolve(buildDirectory)
    Files.createDirectories(resolvedBuildDir)

    output.debug("Looking for modified files to compile in [${resolvedSourceDir}] compared with [${resolvedBuildDir}]")

    Predicate<Path> filter = FileTools.extensionFilter(".groovy")
    Function<Path, Path> mapper = FileTools.extensionMapper(".groovy", ".class")
    List<Path> filesToCompile = FileTools.modifiedFiles(resolvedSourceDir, resolvedBuildDir, filter, mapper)
                                         .collect({ path -> sourceDirectory.resolve(path) })
    if (filesToCompile.isEmpty()) {
      output.info("Skipping compile for source directory [${sourceDirectory}]. No files need compiling")
      return
    }

    output.info "Compiling [${filesToCompile.size()}] Groovy classes from [${sourceDirectory}] to [${buildDirectory}]"

    String command = "${groovycPath} ${settings.indy ? '--indy' : ''} ${settings.compilerArguments} ${classpath(dependencies, additionalClasspath)} --sourcepath ${sourceDirectory} -d ${buildDirectory} ${filesToCompile.join(" ")}"
    output.debug("Executing [${command}]")

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
   * <p/>
   * Here is an example of calling this method:
   * <p/>
   * <pre>
   *   groovy.copyResources(Paths.get("src/some-resources"), Paths.get("build/output-dir"))
   * </pre>
   *
   * @param sourceDirectory The source directory that contains the files to copy.
   * @param buildDirectory The build directory to copy the files to.
   */
  void copyResources(Path sourceDirectory, Path buildDirectory) {
    if (!Files.isDirectory(project.directory.resolve(sourceDirectory))) {
      return
    }

    filePlugin.copy(to: buildDirectory) {
      fileSet(dir: sourceDirectory)
    }
  }

  /**
   * Creates the project's Jar files. This creates four Jar files. The main Jar, main source Jar, test Jar and test
   * source Jar.
   * <p/>
   * Here is an example of calling this method:
   * <p/>
   * <pre>
   *   groovy.jar()
   * </pre>
   */
  void jar() {
    initialize()

    jar(layout.jarOutputDirectory.resolve(project.toArtifact().getArtifactFile()), layout.mainBuildDirectory)
    jar(layout.jarOutputDirectory.resolve(project.toArtifact().getArtifactSourceFile()), layout.mainSourceDirectory, layout.mainResourceDirectory)
    jar(layout.jarOutputDirectory.resolve(project.toArtifact().getArtifactTestFile()), layout.testBuildDirectory)
    jar(layout.jarOutputDirectory.resolve(project.toArtifact().getArtifactTestSourceFile()), layout.testSourceDirectory, layout.testResourceDirectory)
  }

  /**
   * Creates a single Jar file by adding all of the files in the given directories.
   * <p/>
   * Here is an example of calling this method:
   * <p/>
   * <pre>
   *   groovy.jar(Paths.get("foo/bar.jar"), Paths.get("src/main/groovy"), Paths.get("some-other-dir"))
   * </pre>
   *
   * @param jarFile The Jar file to create.
   * @param directories The directories to include in the Jar file.
   */
  void jar(Path jarFile, Path... directories) {
    output.info("Creating JAR [${jarFile}]")

    filePlugin.jar(file: jarFile) {
      directories.each { dir ->
        optionalFileSet(dir: dir)
      }
    }
  }

  private String classpath(List<Map<String, Object>> dependenciesList, Path... additionalPaths) {
    return dependencyPlugin.classpath {
      dependenciesList.each { deps -> dependencies(deps) }
      additionalPaths.each { additionalPath -> path(location: additionalPath) }
    }.toString("-classpath ")
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
