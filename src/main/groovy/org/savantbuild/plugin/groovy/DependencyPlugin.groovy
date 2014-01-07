/*
 * Copyright (c) 2014, Inversoft Inc., All Rights Reserved
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
import org.savantbuild.dep.graph.DependencyGraph
import org.savantbuild.dep.graph.ResolvedArtifactGraph
import org.savantbuild.domain.Project
import org.savantbuild.lang.Classpath
import org.savantbuild.output.Output

/**
 * Dependency plugin.
 *
 * @author Brian Pontarelli
 */
class DependencyPlugin extends BaseGroovyPlugin {
  DependencyService dependencyService = new DefaultDependencyService(output)

  DependencyPlugin(Project project, Output output) {
    super(project, output)

    if (!project.dependencies) {
      return
    }

    if (!project.artifactGraph) {
      DependencyGraph dependencyGraph = dependencyService.buildGraph(project.toArtifact(), project.dependencies, project.workflow)
      project.artifactGraph = dependencyService.reduce(dependencyGraph)
    }
  }

  Classpath classpath(DependencyService.ResolveConfiguration resolveConfiguration) {
    ResolvedArtifactGraph resolvedArtifactGraph = dependencyService.resolve(project.artifactGraph, project.workflow, resolveConfiguration)
    if (resolvedArtifactGraph.size() == 0) {
      return new Classpath()
    }

    return resolvedArtifactGraph.toClasspath()
  }
}
