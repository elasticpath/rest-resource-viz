(set-env! :source-paths #{"dev"})

(require '[boot.util :as util]
         '[clojure.java.io :as io]
         '[clojure.string :as str]
         '[boot])

(def repositories [["maven-central" {:url  "https://repo1.maven.org/maven2/"
                                     :snapshots false
                                     :checksum :fail}]
                   ["clojars" {:url "https://clojars.org/repo"
                               :snapshots true
                               :checksum :fail}]])

;;;;;;;;;;;;;;;
;; Extractor ;;
;;;;;;;;;;;;;;;

(def extractor-version "0.1.1-SNAPSHOT")
(def extractor-group "com.elasticpath")
(def extractor-artifact "rest-resources-viz")
(def extractor-project (symbol (str extractor-group "/" extractor-artifact)))

(def env-extractor
  {:repositories repositories
   :source-paths #{"src/task" "src/shared" "test/shared" "test/task"}
   :resource-paths #{"src/task" "src/shared"}
   :dependencies '[[org.clojure/clojure "1.9.0"]
                   [org.clojure/spec.alpha "0.2.176"]
                   [org.clojure/tools.cli "0.3.5"]
                   [org.clojure/data.xml "0.2.0-alpha1"]
                   [org.clojure/data.zip "0.1.2"]
                   [org.clojure/test.check "0.9.0"]
                   [cheshire "5.6.3"]
                   [org.clojure/java.classpath "0.2.3"]
                   [com.rpl/specter "1.0.5"]
                   [fipp "0.6.8"]
                   [pjstadig/humane-test-output "0.10.0" :scope "test"]
                   [expound "0.8.4" :scope "test"]]})

(def env-extractor-sources-only
  {:repositories repositories
   :resource-paths #{"src/task" "src/shared"}})

(def conf-dev-extractor
  {:env env-extractor
   :pipeline '(comp (repl)
                    (wait))
   :repl {:server true
          :port 5055}})

(def conf-uber-extractor
  {:env env-extractor
   :pipeline '(comp (pom)
                    (uber)
                    (jar))
   :pom {:project     extractor-project
         :version     extractor-version
         :description "Transformations and visualizations for Cortex Rest resources"
         :url         "https://github.com/elasticpath/rest-resource-viz"
         :scm         {:url "https://github.com/elasticpath/rest-resource-viz.git"
                       :connection "scm:git:git://github.com/elasticpath/rest-resource-viz.git"
                       :developerConnection "scm:git:ssh://github.com/elasticpath/rest-resource-viz.git"}
         :license     {"Apache License, Version 2.0"
                       "http://www.apache.org/licenses/LICENSE-2.0"}
         :developers {"Andrea Richiardi" "andrea.richiardi@elasticpath.com"
                      "Matt Bishop" "matt.bishop@elasticpath.com"}}
   :jar {:project extractor-project}})

(boot/defedntask dev-extractor
  "Start the extractor interactive environment"
  [c conf STR str "Path to conf.edn, default is dev/conf.edn"]
  (assert conf "No required conf.edn passed in, see conf-sample.edn for an example.")
  (boot/add-file-conf! conf-dev-extractor conf))

(boot/defedntask build-extractor
  "Build and package the extractor code"
  []
  conf-uber-extractor)

;;
;; Building up javadoc and sources to please Sonatype/Maven Central
;;
(boot/defedntask sources-extractor
  "Build and package the extractor code"
  []
  (-> conf-uber-extractor
      (assoc-in [:pom :classifier] "sources")
      (assoc :env env-extractor-sources-only
             :jar {:project extractor-project
                   :file (str/join "-" [extractor-artifact extractor-version "sources.jar"])})))

(boot/defedntask javadoc-extractor
  "Build and package a fake javadoc jar"
  []
  (-> conf-uber-extractor
      (assoc-in [:pom :classifier] "javadoc")
      (assoc :env env-extractor-sources-only
             :jar {:project extractor-project
                   :file (str/join "-" [extractor-artifact extractor-version "javadoc.jar"])})))

(boot/defedntask install-extractor
  "Build, package and install the extractor code"
  []
  (-> conf-uber-extractor
      (assoc :pipeline '(comp (javadoc-extractor)
                              (sources-extractor)
                              (build-extractor)
                              (install))
             :install {:pom (str extractor-project)})))

(def snapshot? #(.endsWith extractor-version "-SNAPSHOT"))

(boot/defedntask deploy-extractor
  "Build, package and deploy the extractor"
  [u username    STR str "The repository username"
   p password    STR str "The repository password"]
  (assert (and username password) "You need to provide your Sonatype credentials in order to deploy.")
  (-> conf-uber-extractor
      (assoc :pipeline '(comp (javadoc-extractor)
                              (sources-extractor)
                              (build-extractor)
                              (push)))
      (merge (if (snapshot?)
               {:push {:pom (str extractor-project)
                       :ensure-snapshot true
                       :repo-map {:url "https://oss.sonatype.org/content/repositories/snapshots"
                                  :username username
                                  :password password}}}
               {:push {:pom (str extractor-project)
                       :gpg-sign true
                       :ensure-release true
                       :ensure-version extractor-version
                       :repo-map {:url "https://oss.sonatype.org/service/local/staging/deploy/maven2"
                                  :username username
                                  :password password}}}))))

(deftask extract
  "Run the extractor"
  [c conf       STR str  "Path to conf.edn, default is dev/conf.edn"
   f family-xml STR str  "Dumps an xml with all the families defined."
   g graph-edn  STR str  "Dumps an edn containing the graph data."
   p pretty         bool "Enable pretty printing of the data."]
  (assert conf "No required conf.edn passed in, see conf-sample.edn for an example.")
  (boot/apply-conf! (boot/add-file-conf! conf-dev-extractor conf))
  (with-pass-thru _
    (let [main-ns 'rest-resources-viz.extractor]
      (require main-ns)
      (if-let [f (ns-resolve main-ns '-main)]
        (f (concat (when family-xml ["--family-xml" family-xml])
                   (when graph-edn ["--graph-edn" graph-edn])
                   (when pretty ["--pretty"])))
        (throw (ex-info "No -main method found" {:main-ns main-ns}))))))

;;;;;;;;;
;; WEB ;;
;;;;;;;;;

(def env-web-prod {:resource-paths #{"web-assets"}
                   :source-paths #{"src/web" "src/shared"}
                   :dependencies '[[org.clojure/clojure "1.9.0"]
                                   [org.clojure/spec.alpha "0.2.176"]
                                   [org.clojure/clojurescript "1.10.597"  :scope "test"]
                                   [adzerk/boot-cljs "2.1.4" :scope "test"]
                                   [org.clojure/test.check "0.9.0"] ;; AR - at the moment we need it, see http://dev.clojure.org/jira/browse/CLJS-1792
                                   [adzerk/env "0.4.0"]
                                   [binaryage/oops "0.5.5"]
                                   [cljsjs/d3 "4.3.0-3"]
                                   [reagent "0.6.2"]]})

(def conf-web-prod {:env env-web-prod
                    :pipeline '(comp (adzerk.boot-cljs/cljs)
                                     (sift))
                    :sift {:include #{#"^\w+\.out"}
                           :invert true}
                    :cljs {:source-map true
                           :optimizations :advanced
                           :compiler-options {:closure-defines {"goog.DEBUG" false}
                                              :pseudo-names false
                                              :pretty-print false
                                              :source-map-timestamp true
                                              :parallel-build true
                                              :verbose true
                                              :compiler-stats true}}})

(def env-web-dev (update env-web-prod :dependencies
                         into
                         '[[powerlaces/boot-figreload "0.5.13" :scope "test"]
                           [adzerk/boot-cljs-repl "0.3.3" :scope "test"]
                           [com.cemerick/piggieback "0.2.1"  :scope "test"]
                           [weasel "0.7.0"  :scope "test"]
                           [org.clojure/tools.nrepl "0.2.13" :scope "test"]
                           [binaryage/dirac "RELEASE" :scope "test"]
                           [binaryage/devtools "RELEASE" :scope "test"]
                           [powerlaces/boot-cljs-devtools "0.2.0" :scope "test"]
                           [pandeiro/boot-http "0.7.6" :scope "test"]
                           [crisptrutski/boot-cljs-test "0.2.2" :scope "test"]]))

(def nrepl-port 5088)

(def conf-web-dev
  {:env env-web-dev
   :pipeline '(comp (pandeiro.boot-http/serve)
                    (watch)
                    (powerlaces.boot-cljs-devtools/cljs-devtools)
                    (powerlaces.boot-figreload/reload)
                    (adzerk.boot-cljs-repl/cljs-repl)
                    (adzerk.boot-cljs/cljs))
   :reload {:client-opts {:debug true}}
   :cljs-repl {:nrepl-opts {:port nrepl-port}}
   :cljs {:source-map true
          :optimizations :none
          :compiler-options {:external-config
                             {:devtools/config {:features-to-install [:formatters :hints]
                                                :fn-symbol "λ"
                                                :print-config-overrides true}}}}})

(def conf-web-dev-dirac
  (-> conf-web-dev
      (assoc :pipeline '(comp (pandeiro.boot-http/serve)
                              (watch)
                              (powerlaces.boot-cljs-devtools/cljs-devtools)
                              (powerlaces.boot-figreload/reload)
                              (powerlaces.boot-cljs-devtools/dirac)
                              (adzerk.boot-cljs/cljs))
             :dirac {:nrepl-opts {:port nrepl-port}})))


(boot/defedntask build-web
  "Build the web artifact with production configuration"
  []
  conf-web-prod)

(boot/defedntask dev-web
  "Start the web interactive environment"
  [d dirac bool "Enable Dirac Devtools"]
  (if-not dirac
    conf-web-dev
    conf-web-dev-dirac))

;;;;;;;;;;;;;;;;;;
;; MAVEN PLUGIN ;;
;;;;;;;;;;;;;;;;;;

(def conf-dev-plugin {:env {:source-paths #{"src/plugin" "src/web" "src/task" "src/shared"}
                            :dependencies '[[org.flatland/classlojure "0.7.1"]
                                            [org.apache.maven.shared/maven-invoker "3.0.0"]
                                            [resauce "0.1.0"]
                                            [org.slf4j/slf4j-simple "1.7.22"]
                                            [com.elasticpath/rest-viz-maven-plugin "0.1.1-SNAPSHOT"]]
                            :repositories repositories}
                      :props {"maven.home" (java.lang.System/getenv "M2_HOME")
                              "maven.local-repo" (str (java.lang.System/getenv "HOME") "/.m2")}
                      :pipeline '(comp (repl)
                                       (wait))
                      :repl {:server true
                             :port 5099}})

(boot/defedntask dev-plugin
  "Start the plugin interactive environment"
  [c conf STR str  "Path to conf.edn, default is dev/conf.edn"]
  (assert conf "No required conf.edn passed in, see conf-sample.edn for an example.")
  (boot/add-file-conf! conf-dev-plugin conf))

;;;;;;;;;;
;; TEST ;;
;;;;;;;;;;

(def conf-tests
  {:env {:resource-paths #{"web-assets"}
         :source-paths #{"src/task" "test/task" "src/shared" "test/shared"}
         :dependencies (into (get-in conf-dev-extractor [:env :dependencies])
                             '[[org.clojure/tools.namespace "0.3.0-alpha4"]
                               [adzerk/boot-test "1.2.0"]
                               [pjstadig/humane-test-output "0.10.0"]
                               [expound "0.8.4"]])}})

(ns-unmap *ns* 'test)

(boot/defedntask test
  "Testing once (dev profile)"
  [w watch bool "Enable watching folders and test behavior."]
  (if watch
    (assoc conf-tests :pipeline '(comp (watch)
                                       (adzerk.boot-test/test)))
    (assoc conf-tests :pipeline '(adzerk.boot-test/test))))
