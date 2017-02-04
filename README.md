# rest-resources-viz

Manipulate and visualize Cortex rest resources and relationships.

## Usage

The build tool of choice for this project is [boot](http://boot-clj.com/)<img width="24px" height="24px" src="https://github.com/boot-clj/boot-clj.github.io/blob/master/assets/images/logos/boot-logo-3.png" alt="Boot Logo"/>.
Boot enables us to use Clojure code directly, and therefore repl-driven development, when defining our build tasks.

In order to explore the available tasks just run `boot -h`. The tasks prefixed with `dev-` tipically start in-context repl. Every task also has its own `-h/--help` switch.

In order to dump data, there is an `extract` task:

`boot [ extract -- -g data/graph-data2.edn -p ]`

The above command dumps the graph data to `data/graph-data2.edn` with pretty print enabled. The brackets are necessary in order to send positional arguments to the task.

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
