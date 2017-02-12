(def project 'rest-resources-viz)
(def version "0.1.0-SNAPSHOT")

(set-env! :source-paths #{"dev"})

(task-options!
 pom {:project     project
      :version     version
      :description "Transformations and visualizations for Cortex Rest resources"
      :url         "https://github.com/elasticpath/rest-resource-viz"
      :scm         {:url "https://github.com/elasticpath/rest-resource-viz.git"}
      :license     {"Apache License, Version 2.0"
                    "http://www.apache.org/licenses/LICENSE-2.0"}})

(require '[boot.util :as util]
         '[boot.pod :as pod]
         '[clojure.walk :as walk]
         '[boot])

(defn add-resources-deps!
  "Returns a vector containing the rest-resources coordinates given for
  the modules. It reads the modules collection names from a file called
  modules.edn."
  [conf ep-res-conf]
  (let [coords (-> #{}
                   (into (:additional-deps ep-res-conf))
                   (into (boot/calculate-resource-deps ep-res-conf))
                   vec)]
    (update-in conf [:env :dependencies] #(-> % (concat coords) distinct vec))))

(comment
  (def deps (boot/calculate-resource-deps conf-ep-resources))
  (run! #(let [g (-> % first namespace)
               a (-> % first name)
               v (-> % second)]
           (println (str "<dependency>\n  <groupId>" g "</groupId>\n  <artifactId>" a "</artifactId>\n  <version>" v "</version>\n</dependency>")))
        deps))

;;;;;;;;;;;;;;;
;; Extractor ;;
;;;;;;;;;;;;;;;

(def conf-ep-resources
  {:module-edn-path "resources/modules.edn"
   :resources-group-id "com.elasticpath.rest.definitions"
   :resources-format "%s/ep-resource-%s-api"
   :resources-version "0-SNAPSHOT"
   :additional-deps '[[com.elasticpath.rest.definitions/ep-resource-collections-api "0-SNAPSHOT"]
                      [com.elasticpath.rest.definitions/ep-resource-base-api "0-SNAPSHOT"]
                      [com.elasticpath.rest.definitions/ep-resource-controls-api "0-SNAPSHOT"]]})

(def env-extractor
  {:resource-paths #{"resources"}
   :source-paths #{"src/task" "src/shared"}
   :dependencies '[[org.clojure/clojure "1.9.0-alpha14"]
                   [org.clojure/tools.cli "0.3.5"]
                   [org.clojure/data.xml "0.2.0-alpha1"]
                   [org.clojure/data.zip "0.1.2"]
                   [org.clojure/test.check "0.9.0"]
                   [cheshire "5.6.3"]
                   [org.clojure/java.classpath "0.2.3"]
                   [com.rpl/specter "0.13.3-SNAPSHOT"]]})

(def conf-dev-extractor
  {:env env-extractor
   :pipeline '(comp (repl)
                    (wait))
   :repl {:server true
          :port 5055}})

(boot/defedntask dev-extractor
  "Start the extractor interactive environment"
  []
  (add-resources-deps! conf-dev-extractor conf-ep-resources))

(deftask extract
  "Run the extractor"
  []
  (boot/apply-conf! (add-resources-deps! conf-dev-extractor conf-ep-resources))
  (with-pass-thru _
    (let [main-ns 'rest-resources-viz.extractor]
      (require main-ns)
      (if-let [f (ns-resolve main-ns '-main)]
        (f *args*)
        (throw (ex-info "No -main method found" {:main-ns main-ns}))))))

;;;;;;;;;
;; WEB ;;
;;;;;;;;;

(def env-web-prod {:resource-paths #{"web-assets"}
                   :source-paths #{"src/web" "src/shared"}
                   :dependencies '[[org.clojure/clojure "1.9.0-alpha14"]
                                   [adzerk/boot-cljs "2.0.0-SNAPSHOT" :scope "test"]
                                   [org.clojure/clojurescript "1.9.473"  :scope "test"]
                                   [org.clojure/test.check "0.9.0"] ;; AR - at the moment we need it, see http://dev.clojure.org/jira/browse/CLJS-1792
                                   [adzerk/env "0.4.0"]
                                   [binaryage/oops "0.5.2"]
                                   [cljsjs/d3 "4.3.0-3"]
                                   [reagent "0.6.0"]]})

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
                         '[[powerlaces/boot-figreload "0.1.0-SNAPSHOT" :scope "test"]
                           [adzerk/boot-cljs-repl "0.3.3" :scope "test"]
                           [com.cemerick/piggieback "0.2.1"  :scope "test"]
                           [weasel "0.7.0"  :scope "test"]
                           [org.clojure/tools.nrepl "0.2.12" :scope "test"]
                           [binaryage/devtools "0.8.3" :scope "test"]
                           [powerlaces/boot-cljs-devtools "0.1.3-SNAPSHOT" :scope "test"]
                           [pandeiro/boot-http "0.7.6" :scope "test"]
                           [crisptrutski/boot-cljs-test "0.2.2" :scope "test"]]))

(def conf-web-dev
  {:env env-web-dev
   :pipeline '(comp (pandeiro.boot-http/serve)
                    (watch)
                    (powerlaces.boot-cljs-devtools/cljs-devtools)
                    (powerlaces.boot-figreload/reload)
                    (adzerk.boot-cljs-repl/cljs-repl)
                    (adzerk.boot-cljs/cljs))
   :reload {:client-opts {:debug true}}
   :cljs-repl {:nrepl-opts {:port 5088}}
   :cljs {:source-map true
          :optimizations :none
          :compiler-options {:external-config
                             {:devtools/config {:features-to-install [:formatters :hints]
                                                :fn-symbol "λ"
                                                :print-config-overrides true}}}}})


(boot/defedntask build-web
  "Build the web artifact with production configuration"
  []
  conf-web-prod)

(boot/defedntask dev-web
  "Start the web interactive environment"
  []
  conf-web-dev)

;;;;;;;;;;;;;;;;;;
;; MAVEN PLUGIN ;;
;;;;;;;;;;;;;;;;;;

(def conf-plugin {:env {:source-paths #{"src/plugin" "src/web" "src/task" "src/shared"}
                        :dependencies '[[org.cloudhoist/clojure-maven-mojo "0.3.3"]
                                        [org.cloudhoist/clojure-maven-mojo-annotations "0.3.3"]
                                        [com.cemerick/pomegranate "0.4.0-SNAPSHOT"]
                                        [org.flatland/classlojure "0.7.1"]
                                        ]
                        :repositories [["maven-central" {:url  "https://repo1.maven.org/maven2/"
                                                         :snapshots false
                                                         :checksum :fail}]
                                       ["clojars" {:url "http://clojars.org/repo"
                                                   :snapshots true
                                                   :checksum :fail}]]}
                  :pipeline '(comp (show)
                                   (pom)
                                   (uberjar))
                  :show {:fileset true}})

(def conf-dev-plugin
  (merge (-> conf-plugin
             (update-in [:env :dependencies]
                        into
                        '[[org.apache.maven.shared/maven-invoker "3.0.0"]
                          [org.slf4j/slf4j-simple "1.7.22"]])
             (update-in [:props] assoc
                        "maven.home" (java.lang.System/getenv "M2_HOME")
                        "maven.local-repo" (str (java.lang.System/getenv "HOME") "/.m2")))
         {:pipeline '(comp (repl)
                           (wait))
          :repl {:server true
                 :port 5055}}))

(boot/defedntask dev-plugin
  "Start the extractor interactive environment"
  []
  (add-resources-deps! conf-dev-plugin conf-ep-resources))

(boot/defedntask build-plugin []
  "Build the plugin artifact"
  []
  conf-plugin)

;;;;;;;;;;
;; TEST ;;
;;;;;;;;;;

(def conf-tests
  {:env {:resource-paths #{"resources"}
         :source-paths #{"src/task" "test/task" "src/shared" "test/shared"}
         :dependencies (into (get-in conf-dev-extractor [:env :dependencies])
                             '[[org.clojure/tools.namespace "0.3.0-alpha3"]
                               [metosin/boot-alt-test "0.3.0"]])}
   :pipeline '(metosin.boot-alt-test/alt-test)})

(ns-unmap *ns* 'test)

(boot/defedntask test
  "Testing once (dev profile)"
  []
  conf-tests)
