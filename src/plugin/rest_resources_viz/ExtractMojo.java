package rest_resources_viz;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import org.apache.maven.execution.MavenSession;

import clojure.java.api.Clojure;
import clojure.lang.IFn;
import clojure.lang.Keyword;
import clojure.lang.PersistentHashMap;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Goal which generates the web visualization files.
 *
 * @since "0.1.0"
 */
@Mojo(name = "extract", defaultPhase = LifecyclePhase.GENERATE_RESOURCES, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, threadSafe = true)
public class ExtractMojo extends AbstractMojo {

    @Parameter(required = true, readonly = true, property = "session")
    private MavenSession session;

    /**
     * Output Directory.
     */
    @Parameter(required = true, defaultValue = "${project.build.directory}/rest-viz-assets", property = "rest-viz-maven-plugin.targetDirectory")
    public File targetDirectory;

    /**
     * Pretty print the extracted data.
     */
    @Parameter(defaultValue = "graph-data.edn", property = "rest-viz-maven-plugin.dataTargetName")
    public String dataTargetName;

    @Parameter(defaultValue = "false", property = "rest-viz-maven-plugin.prettyPrint")
    public Boolean prettyPrint;

    public void execute() throws MojoExecutionException {
        try {
            Map<Keyword, Object> opts = new HashMap<>();
            opts.put(Keyword.intern("target-directory"), targetDirectory.getCanonicalPath());
            opts.put(Keyword.intern("data-target-name"), dataTargetName);
            opts.put(Keyword.intern("pretty-print"), prettyPrint);

            IFn require = Clojure.var("clojure.core", "require");
            require.invoke(Clojure.read("rest-resources-viz.extract"));

            IFn run = Clojure.var("rest-resources-viz.extract", "run-extractor");
            run.invoke(session, getLog(), PersistentHashMap.create(opts));
        } catch (Exception e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }
}
