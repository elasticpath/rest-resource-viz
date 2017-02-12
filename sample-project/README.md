# rest-viz-testbed

This project is a simple test bed for `com.elasticpath.tools/rest-viz-maven-plugin`.

It was generated with a simple:

`mvn archetype:generate -DgroupId=com.elasticpath.tools -DartifactId=rest-viz-testbed -Dversion=0.1.0-SNAPSHOT -DarchetypeGroupId=org.apache.maven.archetypes -DarchetypeArtifactId=maven-archetype-quickstart -DarchetypeVersion=1.1`

And it is mainly showing of to configure it properly (this is particularly important for `<dependencies>` containing rest resource definitions).

## Usage

A simple `mvn package` will generate the necessary files in `target/rest-viz-assets` (unless you change the configuration):

You can also execute the task directly:

     mvn com.elasticpath.tools:rest-viz-maven-plugin:extract

If it is too verbose, add the following in `$USER/.m2/settings.xml`:

    <pluginGroups>
      <pluginGroup>com.elasticpath.tools</pluginGroup>
    </pluginGroups>

And then run:

    mvn rest-viz:extract

If everything was successful you should be able to serve the `rest-viz-assets` folder directly, for example using:

    cd target/rest-viz-assets && python -m SimpleHTTPServer

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
