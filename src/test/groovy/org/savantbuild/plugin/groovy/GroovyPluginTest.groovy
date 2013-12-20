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

import org.savantbuild.domain.Project
import org.savantbuild.output.SystemOutOutput
import org.testng.annotations.Test

import java.nio.file.Files
import java.nio.file.Paths

import static org.testng.Assert.assertFalse
import static org.testng.Assert.assertTrue

/**
 * Tests the groovy plugin.
 *
 * @author Brian Pontarelli
 */
class GroovyPluginTest {
  @Test
  public void all() throws Exception {
    Project project = new Project(Paths.get("groovy-plugin/test-project"))
    GroovyPlugin plugin = new GroovyPlugin(project, new SystemOutOutput(System.out, true))
    plugin.settings.groovyVersion = "2.1"
    plugin.settings.javaVersion = "1.6"

    plugin.clean()
    assertFalse(Files.isDirectory(Paths.get("groovy-plugin/test-project/build")))

    plugin.compileMain()
    assertTrue(Files.isRegularFile(Paths.get("groovy-plugin/test-project/build/classes/main/MyClass.class")))
  }
}
