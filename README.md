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

None
