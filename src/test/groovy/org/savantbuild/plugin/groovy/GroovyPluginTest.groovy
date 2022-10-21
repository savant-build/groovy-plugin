/*
 * Copyright (c) 2014-2018, Inversoft Inc., All Rights Reserved
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

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.jar.JarFile

import org.savantbuild.dep.domain.Artifact
import org.savantbuild.dep.domain.Dependencies
import org.savantbuild.dep.domain.DependencyGroup
import org.savantbuild.dep.domain.License
import org.savantbuild.dep.workflow.FetchWorkflow
import org.savantbuild.dep.workflow.PublishWorkflow
import org.savantbuild.dep.workflow.Workflow
import org.savantbuild.dep.workflow.process.CacheProcess
import org.savantbuild.domain.Project
import org.savantbuild.domain.Version
import org.savantbuild.io.FileTools
import org.savantbuild.output.Output
import org.savantbuild.output.SystemOutOutput
import org.savantbuild.runtime.RuntimeConfiguration
import org.testng.annotations.BeforeSuite
import org.testng.annotations.Test

import static org.testng.Assert.assertFalse
import static org.testng.Assert.assertNotNull
import static org.testng.Assert.assertTrue

/**
 * Tests the groovy plugin.
 *
 * @author Brian Pontarelli
 */
class GroovyPluginTest {
  public static Path projectDir

  @BeforeSuite
  void beforeSuite() {
    projectDir = Paths.get("")
    if (!Files.isRegularFile(projectDir.resolve("LICENSE"))) {
      projectDir = Paths.get("../groovy-plugin")
    }
  }

  @Test
  void all() throws Exception {
    FileTools.prune(projectDir.resolve("build/cache"))

    Output output = new SystemOutOutput(true)
    output.enableDebug()

    Project project = new Project(projectDir.resolve("test-project"), output)
    project.group = "org.savantbuild.test"
    project.name = "test-project"
    project.version = new Version("1.0")
    project.licenses.add(License.parse("ApacheV2_0", null))

    def cache = new CacheProcess(output, null)
    project.dependencies = new Dependencies(new DependencyGroup("test-compile", false, new Artifact("org.testng:testng:6.8.7:jar", false)))
    project.workflow = new Workflow(
        new FetchWorkflow(output, cache),
        new PublishWorkflow(cache)
    )

    GroovyPlugin plugin = new GroovyPlugin(project, new RuntimeConfiguration(), output)
    plugin.settings.groovyVersion = "2.4"
    plugin.settings.javaVersion = "1.8"

    plugin.clean()
    assertFalse(Files.isDirectory(projectDir.resolve("test-project/build")))

    plugin.compileMain()
    assertTrue(Files.isRegularFile(projectDir.resolve("test-project/build/classes/main/MyClass.class")))
    assertTrue(Files.isRegularFile(projectDir.resolve("test-project/build/classes/main/main.txt")))

    plugin.compileTest()
    assertTrue(Files.isRegularFile(projectDir.resolve("test-project/build/classes/test/MyClassTest.class")))
    assertTrue(Files.isRegularFile(projectDir.resolve("test-project/build/classes/test/test.txt")))

    plugin.jar()
    assertTrue(Files.isRegularFile(projectDir.resolve("test-project/build/jars/test-project-1.0.0.jar")))
    assertJarContains(projectDir.resolve("test-project/build/jars/test-project-1.0.0.jar"), "MyClass.class", "main.txt")
    assertTrue(Files.isRegularFile(projectDir.resolve("test-project/build/jars/test-project-1.0.0-src.jar")))
    assertJarContains(projectDir.resolve("test-project/build/jars/test-project-1.0.0-src.jar"), "MyClass.groovy", "main.txt")
    assertTrue(Files.isRegularFile(projectDir.resolve("test-project/build/jars/test-project-test-1.0.0.jar")))
    assertJarContains(projectDir.resolve("test-project/build/jars/test-project-test-1.0.0.jar"), "MyClassTest.class", "test.txt")
    assertTrue(Files.isRegularFile(projectDir.resolve("test-project/build/jars/test-project-test-1.0.0-src.jar")))
    assertJarContains(projectDir.resolve("test-project/build/jars/test-project-test-1.0.0-src.jar"), "MyClassTest.groovy", "test.txt")

    plugin.document()
    assertTrue(Files.isRegularFile(projectDir.resolve("test-project/build/doc/index.html")))
  }

  private static void assertJarContains(Path jarFile, String... entries) {
    JarFile jf = new JarFile(jarFile.toFile())
    entries.each({ entry -> assertNotNull(jf.getEntry(entry), "Jar [${jarFile}] is missing entry [${entry}]") })
    jf.close()
  }
}
