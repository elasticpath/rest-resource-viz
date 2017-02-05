(ns ^{:doc "ExtractData Mojo for the resource visualization plugin"
      :author "Andrea Richiardi"}
    rest-resources-viz.extract
  (:require [clojure.java.io :as io]
            [clojure.pprint :as pprint :refer [pprint]]
            [clojure.maven.mojo.defmojo :as mojo]
            [clojure.maven.mojo.log :as log]
            [cemerick.pomegranate.aether :as aether]
            [classlojure.core :as classlojure]
            [rest-resources-viz.maven :as maven]))

(defn pp-str
  [data]
  (with-out-str (pprint/pprint data)))

(def ^:private deps '[[org.clojure/clojure "1.9.0-alpha14"]
                      [org.clojure/tools.cli "0.3.5"]
                      [org.clojure/data.xml "0.2.0-alpha1"]
                      [org.clojure/data.zip "0.1.2"]
                      [org.clojure/test.check "0.9.0"]
                      [cheshire "5.6.3"]
                      [org.clojure/java.classpath "0.2.3"]
                      [com.rpl/specter "0.13.3-SNAPSHOT"]
                      [com.elasticpath.tools/rest-viz-maven-plugin "0.1.0" :classifier "sources"]])

(def ^:private clojure-paths ["main/clojure"])

(defn run-extractor
  [maven-session project classpath-paths opts]
  (log/debugf "Options: %s" opts)

  (let [local-repo (or (.. maven-session getLocalRepository getBasedir)
                       (System/getProperty "maven.local-repo") )
        resolved-deps (aether/resolve-dependencies :repositories maven/default-repositories
                                                   :coordinates deps
                                                   :local-repo local-repo)
        classpath-urls (concat (into [] maven/string->url classpath-paths)
                               (into [] maven/dependency->url resolved-deps))
        output-path (.getCanonicalPath (io/file (:target-directory opts) (:target-name opts)))]
    (log/debugf "Local m2: %s" local-repo)
    (log/debugf "Resolved deps: %s" (pp-str resolved-deps))
    (log/debugf "Plugin classpath: %s" (pp-str classpath-urls))
    (log/debugf "Output path: %s" output-path)

    (classlojure/eval-in (classlojure/classlojure classpath-urls)
      `(do (require '[clojure.java.io]
                    '[clojure.pprint]
                    '[rest-resources-viz.extractor])
           (rest-resources-viz.extractor/instrument-all)

           (try
             (rest-resources-viz.extractor/spit-graph-data-edn!
              ~output-path
              {:pretty ~(:pretty? opts)})
             (catch Exception ~'e
               (throw (ex-info "Cannot extract data, make sure you added the dependencies to your pom.xml"
                               ~opts
                               ~'e))))))))

(comment
  (defonce invoker (maven/make-invoker (java.lang.System/getenv "M2_HOME") (java.lang.System/getenv "PWD")))
  (def test-pom (-> "pom.xml" io/resource io/file))
  (def goals ["rest-resources-viz:extract"])
  (def res (maven/invoke-mojo invoker test-pom goals true))
  (run-extractor nil nil nil nil nil nil))

(mojo/defmojo ExtractMojo
  {:goal "extract"
   :requires-dependency-resolution "test"}

  ;; parameters
  [maven-session {:defaultValue "${session}"
                  :readonly true}

   base-directory {:expression "${basedir}"
                   :description "Base dir"
                   :required true
                   :readonly true}

   classpath-paths {:required true
                    :readonly true
                    :description "Compile classpath"
                    :defaultValue "${project.compileClasspathElements}" }

   project {:expression "${project}"
            :description "Project"
            :required true
            :readonly true}

   target-directory {:required true
                     :defaultValue "${project.build.outputDirectory}"
                     :alias "extractDataTargetDirectory"
                     :description "Output Directory"}

   target-name {:defaultValue "graph-data.edn"
                :alias "extractDataTargetName"
                :description "Custom name for the output file"}

   pretty {:defaultValue "false"
           :alias "extractDataPrettyPrint"
           :description "Pretty print the extracted data"}]

  (run-extractor maven-session
                 project
                 classpath-paths
                 {:target-directory target-directory
                  :target-name target-name
                  :pretty? pretty}))
