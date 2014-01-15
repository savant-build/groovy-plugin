#!/bin/bash
mkdir -p ~/.savant/cache/org/savantbuild/plugin/groovy/0.1.0-\{integration\}/
cp build/jars/*.jar ~/.savant/cache/org/savantbuild/plugin/groovy/0.1.0-\{integration\}/
cp src/main/resources/amd.xml ~/.savant/cache/org/savantbuild/plugin/groovy/0.1.0-\{integration\}/groovy-0.1.0-\{integration\}.jar.amd
cd ~/.savant/cache/org/savantbuild/plugin/groovy/0.1.0-\{integration\}/
md5sum groovy-0.1.0-\{integration\}.jar > groovy-0.1.0-\{integration\}.jar.md5
md5sum groovy-0.1.0-\{integration\}.jar.amd > groovy-0.1.0-\{integration\}.jar.amd.md5
md5sum groovy-0.1.0-\{integration\}-src.jar > groovy-0.1.0-\{integration\}-src.jar.md5
