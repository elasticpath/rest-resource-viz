# rest-resources-viz

Manipulate and visualize Cortex rest resources and relationships.

## Usage

The build tool of choice for this project is [boot](http://boot-clj.com/)<img width="24px" height="24px" src="https://github.com/boot-clj/boot-clj.github.io/blob/master/assets/images/logos/boot-logo-3.png" alt="Boot Logo"/>.
Boot enables us to use Clojure code directly, and therefore repl-driven development, when defining our build tasks.

In order to explore the available tasks just run `boot -h`. The tasks prefixed with `dev-` tipically start in-context repl. Every task also has its own `-h/--help` switch.

The project contains three main deliverables: the extractor, the website and the plugin. The extractor is the piece of code which scans and creates the data file used by the website.  The maven plugin just wraps them in a convenient way for consumption.

## Extractor

The extractor is a direct dependency of the plugin and needs to be installed:

    boot install-extractor

Additionally, it can dump data using the `extract` task:

    boot [ extract -- -g data/graph-data2.edn -p ]

The above command dumps the graph data to `data/graph-data2.edn` with pretty print enabled. The brackets are necessary in order to send positional arguments to the task.

## Plugin

This is at the moment a three-step process. The extractor and the web assets needs to be compiled first and positioned in `web-target`:

    boot install-extractor

    boot build-web target --dir web-target

Then Maven needs to be invoked for compiling and installing the actual plugin:

    mvn -Pboot-clj clean install

This will generate a `mvn-target` folder containing the artifact and install it `$USER/.m2/repository`.

The plugin is less interactive in its nature but a repl can still be launched with:

    boot dev-plugin

None of the Maven plugin classes will be available at the repl. Another useful trick is to repeatedly generate and install the plugin by watching the source files:

    boot -B --source-paths "." watch  ".*\.clj?"  -i "pom.xml" -e "^mvn-target" sift -i "src" -i "pom.xml" mvn -W `pwd` -A "-Pboot-clj clean install"


## License

Copyright 2017 Elastic Path

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
