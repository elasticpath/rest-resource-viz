# rest-resources-viz

Manipulate and visualize Cortex rest resources and relationships.

## Usage

Rest-resources-viz extracts the graph data from Elastic Path's resource definitions in your `.m2` and therefore needs access to the Maven artifacts that contain them.

Usually you would add the `com.elasticpath/rest-viz-maven-plugin` to your `pom.xml` this way:

```xml
<plugins>
  <plugin>
    <groupId>com.elasticpath</groupId>
    <artifactId>rest-viz-maven-plugin</artifactId>
    <version>X.Y.Z</version>
    <executions>
      <execution>
        <id>extract-viz-data</id>
        <phase>package</phase> <!-- set yours -->
        <goals>
          <goal>extract</goal>
        </goals>
      </execution>
    </executions>
    <configuration>
      <!-- Output Directory - defaults to ${project.build.directory}/rest-viz-assets -->
      <!-- <targetDirectory>${project.build.directory}/my-assets</targetDirectory> -->

      <!-- Custom name for the extracted data output file - defaults to graph-data.edn -->
      <!-- <dataTargetName>my-name.edn</dataTargetName> -->

      <!-- Pretty print the extracted data - defaults to false -->
      <!-- <prettyPrint>true</prettyPrint> -->
    </configuration>

    <dependencies>
      <!-- list of Maven artifacts containing the resource definitions -->
    </dependencies>
  </plugin>
</plugins>
```

The above generates all the necessary web assets to the specified output directory (or `rest-viz-assets`) and can be served directly.
The sample project in the repository can help with setting up your project. If artifacts cannot be found, make also sure that the correct repository has been specified in `.m2/settings.xml` and/or the correct profile has been activated.

If you want to try it at the command line, you can also call the goal explicitly (check [Plugin Prefix Resolution](https://maven.apache.org/guides/introduction/introduction-to-plugin-prefix-mapping.html) to type less):

    mvn com.elasticpath:rest-viz-maven-plugin:extract

### Simple(r) Visualization

There is another way to visualize the resource graph. It requires cloning this repository and familiarizing yourself with the Clojure world. First of all the JavaScript app and assets needs to be materialized in `target`:

```
boot build-web target
```

The conf file passed in is necessary in order to configure the Maven repository and coordinates (assuming they have a similar name) for the resource artifacts. For a sample configuration see `conf-sample.edn` in the repository root.
In case of missing keys a `clojure.spec` error will tell you what was expected. The error message is a bit cryptic and will be improved, bear with us.

Once the command finishes, you can serve the `target` folder. We use `python` because available in any major Linux/OSX distribution:

```
cd target && python -m SimpleHTTPServer; cd..
```

Now you can open your browser and connect to `localhost:8000`. You will see an empty frame. This is because the graph data is missing. That can be easily generated with the `extract` task (see `boot extract -h` as well) and needs to be saved in `target/graph-data.edn`:

```
boot extract --conf my-conf.edn --graph-edn target/graph-data.edn --pretty
```

Note that we are creating the file in `target` now - the app should pick up the changes (React is your friend). If not, reload the page.

## Details

The project contains three main deliverables: the extractor, the website and the plugin. The extractor is the piece of code which scans and creates the data file used by the website.  The maven plugin just wraps them in a convenient way for consumption.

For all the available tasks run `boot -h`. The tasks prefixed with `dev-` tipically start in-context repl. Every task also has its own `-h/--help` switch.

### Extractor

The extractor is a direct dependency of the plugin and needs to be installed:

    boot install-extractor

Additionally, it can read the resource xmls from Maven and dump the graph data using the `extract` task:

    boot extract -- -g data/graph-data2.edn -p

The above command dumps the graph data to `data/graph-data2.edn` with pretty print enabled. The brackets are necessary in order to send positional arguments to the task.

### Plugin

This is at the moment a three-step process. The extractor and the web assets needs to be compiled first and positioned in `web-target`:

    boot install-extractor

    boot build-web target --dir web-target

Then Maven needs to be invoked for compiling and installing the actual plugin:

    mvn -Pboot-clj clean install

The above will generate a `mvn-target` folder containing the artifact and install it `$USER/.m2/repository`.

The plugin is less interactive in its nature but a repl can still be launched with:

    boot dev-plugin

None of the Maven plugin classes will be available at the repl. Another useful trick is to repeatedly generate and install the plugin by watching the source files:

    boot -B --source-paths "." watch  ".*\.clj?"  -i "pom.xml" -e "^mvn-target" sift -i "src" -i "pom.xml" mvn -W `pwd` -A "-Pboot-clj clean install"

## Deploying to Sonatype

In order to deploy a staging/release artifact, the steps are almost the same as above:

    boot -P deploy-extractor -u ... -p ...          # Sonatype credentials here
    boot -P build-web target --dir web-target
    mvn -Pboot-clj -Psonatype-release clean deploy  # Credentials come from settings.xml

The `-P` avoids pulling in packages from you global `profile.boot`.

In order to use Maven gpg signing, add the following to `settings.xml`:

    <profile>
      <id>default-profile</id>
      <activation>
        <activeByDefault>true</activeByDefault>
      </activation>
      <properties>
        <gpg.executable>/usr/bin/gpg2</gpg.executable>
        <gpg.useagent>true</gpg.useagent>
        <gpg.keyname>...</gpg.keyname>
        <gpg.passphrase>...</gpg.passphrase>
      </properties>
    </profile>

## Boot

The build tool of choice for this project is [boot](http://boot-clj.com/)<img width="24px" height="24px" src="https://github.com/boot-clj/boot-clj.github.io/blob/master/assets/images/logos/boot-logo-3.png" alt="Boot Logo"/>.
Boot enables us to use Clojure code directly, and therefore repl-driven development, when defining our build tasks.

## License

Copyright 2018 Elastic Path

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
