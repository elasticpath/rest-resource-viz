(set-env! :source-paths #{"dev"})

(require '[boot.util :as util]
         '[clojure.java.io :as io]
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

(def extractor-project 'com.elasticpath.tools/rest-resources-viz)
(def extractor-version "0.1.0")

(def conf-ep-resources
  {:module-edn-path "resources/modules.edn"
   :resources-group-id "com.elasticpath.rest.definitions"
   :resources-format "%s/ep-resource-%s-api"
   :resources-version "0-SNAPSHOT"
   :additional-deps '[[com.elasticpath.rest.definitions/ep-resource-collections-api "0-SNAPSHOT"]
                      [com.elasticpath.rest.definitions/ep-resource-base-api "0-SNAPSHOT"]
                      [com.elasticpath.rest.definitions/ep-resource-controls-api "0-SNAPSHOT"]]})

(def env-extractor
  {:source-paths #{"src/task" "src/shared"}
   :resource-paths #{"src/task" "src/shared"}
   :dependencies '[[org.clojure/clojure "1.9.0-alpha14"]
                   [org.clojure/tools.cli "0.3.5"]
                   [org.clojure/data.xml "0.2.0-alpha1"]
                   [org.clojure/data.zip "0.1.2"]
                   [org.clojure/test.check "0.9.0"]
                   [cheshire "5.6.3"]
                   [org.clojure/java.classpath "0.2.3"]
                   [com.rpl/specter "0.13.3-SNAPSHOT"]
                   [fipp "0.6.8"]]})

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
         :scm         {:url "https://github.com/elasticpath/rest-resource-viz.git"}
         :license     {"Apache License, Version 2.0"
                       "http://www.apache.org/licenses/LICENSE-2.0"}}
   :jar {:project extractor-project}})

(boot/defedntask dev-extractor
  "Start the extractor interactive environment"
  []
  (add-resources-deps! conf-dev-extractor conf-ep-resources))

(boot/defedntask build-extractor
  "Build and package the extractor code"
  []
  conf-uber-extractor)

(boot/defedntask install-extractor
  "Build, package  and install the extractor code"
  []
  (-> conf-uber-extractor
      (assoc :pipeline '(comp (build-extractor)
                              (install))
             :install {:pom (str extractor-project)})))

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

(def env-web-prod {:resource-paths #{"resources"}
                   :source-paths #{"src/web" "src/shared"}
                   :dependencies '[[org.clojure/clojure "1.9.0-alpha14"]
                                   [org.clojure/clojurescript "1.9.473"  :scope "test"]
                                   [adzerk/boot-cljs "2.0.0-SNAPSHOT" :scope "test"]
                                   [org.clojure/test.check "0.9.0"] ;; AR - at the moment we need it, see http://dev.clojure.org/jira/browse/CLJS-1792
                                   [adzerk/env "0.4.0"]
                                   [binaryage/oops "0.5.2"]
                                   [cljsjs/d3 "4.3.0-3"]
                                   [cljsjs/intersections "1.0.0-0"]
                                   [reagent "0.6.0"]]})

(def conf-web-prod {:env env-web-prod
                    :pipeline '(adzerk.boot-cljs/cljs)
                    :cljs {:source-map true
                           :optimizations :advanced
                           :compiler-options {:closure-defines {"goog.DEBUG" false}
                                              ;; :elide-asserts true
                                              :pseudo-names true ;; TODO set to false for prod
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

(def conf-dev-plugin {:env {:source-paths #{"src/plugin" "src/web" "src/task" "src/shared"}
                            :dependencies '[[org.cloudhoist/clojure-maven-mojo "0.3.3"]
                                            [org.cloudhoist/clojure-maven-mojo-annotations "0.3.3"]
                                            [org.flatland/classlojure "0.7.1"]
                                            [org.apache.maven.shared/maven-invoker "3.0.0"]
                                            [resauce "0.1.0"]
                                            [org.slf4j/slf4j-simple "1.7.22"]
                                            [com.elasticpath.tools/rest-viz-maven-plugin "0.1.0"]]
                            :repositories [["maven-central" {:url  "https://repo1.maven.org/maven2/"
                                                             :snapshots false
                                                             :checksum :fail}]
                                           ["clojars" {:url "http://clojars.org/repo"
                                                       :snapshots true
                                                       :checksum :fail}]]}
                      :props {"maven.home" (java.lang.System/getenv "M2_HOME")
                              "maven.local-repo" (str (java.lang.System/getenv "HOME") "/.m2")}
                      :pipeline '(comp (repl)
                                       (wait))
                      :repl {:server true
                             :port 5099}})

(boot/defedntask dev-plugin
  "Start the extractor interactive environment"
  []
  (add-resources-deps! conf-dev-plugin conf-ep-resources))

;; (def conf-prod-plugin
;;   {:env {:dependencies '[[big-solutions/boot-mvn "0.1.6"]]}
;;    :pipeline '(comp (build-web)
;;                     (target)
;;                     (boot-mvn.core/mvn))
;;    :target {:dir #{"web-target"}}
;;    :mvn {:working-dir (.. (io/file ".") getCanonicalPath)
;;          :args "-Pboot-clj clean install"}})

;; (boot/defedntask install-plugin []
;;   "Build the plugin artifact"
;;   []
;;   conf-prod-plugin)

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
