(def project 'rest-resources-viz)
(def version "0.1.0-SNAPSHOT")

(task-options!
 pom {:project     project
      :version     version
      :description "Visualizations for rest resources in Cortex"
      :url         "http://example/FIXME"
      :scm         {:url "https://github.com/yourname/rest-resources-viz"}
      :license     {}})

(require '[clojure.edn :as edn]
         '[boot.util :as util]
         '[boot.pod :as pod]
         '[clojure.java.io :as io])

;; A couple of helper functions

(defn set-system-properties!
  "Set a system property for each entry in the map m."
  [m]
  (doseq [kv m]
    (System/setProperty (key kv) (val kv))))

(defn apply-conf!
  "Calls boot.core/set-env! with the content of the :env key and
  System/setProperty for all the key/value pairs in the :props map."
  [conf]
  (let [env (:env conf)
        props (:props conf)]
    (apply set-env! (reduce #(into %2 %1) [] env))
    (assert (or (nil? props) (map? props)) "Option :props should be a map.")
    (set-system-properties! props)))

(def module-data-path "resources/modules.edn")
(def resources-group-id "com.elasticpath.rest.definitions")
(def resources-format "%s/ep-resource-%s-api") ;; need group and resource name
(def resources-version "0-SNAPSHOT") ;; TODO, read it from env vars?

(defn add-resources-deps!
  "Returns a vector containing the rest-resources coordinates given for
  the modules. It reads the modules collection names from a file called
  modules.edn."
  [conf]
  (let [coords (->> module-data-path
                    io/file
                    slurp
                    edn/read-string
                    (map #(vector (-> (format resources-format resources-group-id %)
                                      symbol)
                                  resources-version)))]
    (util/dbug "Rest resources coordinates %s\n" (vec coords))
    (update-in conf [:env :dependencies] #(-> % (concat coords) distinct vec))))

;;;;;;;;;;;;;;;
;; Extractor ;;
;;;;;;;;;;;;;;;

(def conf-extractor
  {:env {:resource-paths #{"resources"}
         :source-paths #{"src/task" "src/shared"}
         :dependencies '[[org.clojure/clojure "1.9.0-alpha14"]
                         [org.clojure/data.xml "0.2.0-alpha1"]
                         [org.clojure/data.zip "0.1.2"]
                         [org.clojure/test.check "0.9.0"]
                         [cheshire "5.6.3"]
                         [org.clojure/java.classpath "0.2.3"]]}})

(deftask resource-data
  "The task generates the data from the resource parsing.

  It does NOT add it to the fileset, but calls
  rest-resource-viz.generator/-main and dumps the data in src/cljs (hard
  coded). It should not be part of the build pipeline."
  []
  (let [env (:env conf-extractor)
        pod-env (update env :directories concat (:source-paths env) (:resource-paths env))
        pod (future (pod/make-pod pod-env))]
    (with-pass-thru fs
      (boot.util/info "Generating resource data...\n")
      (pod/with-eval-in @pod
        (require 'rest-resources-viz.extractor)
        (rest-resources-viz.extractor/-main)))))

(deftask build-extractor
  "Build and install the project locally."
  []

  (comp (pom) (jar) (install)))

(deftask init-extractor
  "Start the dev interactive environment."
  []
  (with-pass-thru fs
    (let [new-conf (add-resources-deps! conf-extractor)]
      (util/dbug "Current conf:\n%s\n" (util/pp-str new-conf))
      (apply-conf! new-conf))))

(deftask dev-extractor
  "Start the dev interactive environment."
  []
  (util/info "Starting interactive dev...\n")
  (comp (init-extractor)
        (repl :server true
              :port 5088)
        (wait)))

;;;;;;;;;
;; WEB ;;
;;;;;;;;;

(def conf-web
  {:env {:resource-paths #{"resources"}
         :source-paths #{"src/web"}
         :dependencies '[[org.clojure/clojure "1.9.0-alpha14"]
                         [adzerk/boot-cljs "2.0.0-SNAPSHOT" :scope "test"]
                         [adzerk/boot-reload "0.5.0-figwheel-SNAPSHOT" :scope "test"]

                         [adzerk/boot-cljs-repl "0.3.3" :scope "test"]
                         [com.cemerick/piggieback "0.2.1"  :scope "test"]
                         [weasel "0.7.0"  :scope "test"]
                         [org.clojure/tools.nrepl "0.2.12" :scope "test"]

                         [binaryage/devtools "0.8.3" :scope "test"]
                         [powerlaces/boot-cljs-devtools "0.1.3-SNAPSHOT" :scope "test"]
                         [pandeiro/boot-http "0.7.6" :scope "test"]
                         [crisptrutski/boot-cljs-test "0.2.2" :scope "test"]

                         ;; App deps
                         [org.clojure/clojurescript "1.9.293"  :scope "test"]
                         [adzerk/env "0.4.0"]
                         [prismatic/dommy "1.1.0" :scope "test"]
                         [binaryage/oops "0.5.2"]
                         [cljsjs/d3 "4.3.0-2"]]}})

(deftask init-web
  "Start the dev interactive environment."
  []
  (util/dbug "Current conf:\n%s\n" (util/pp-str conf-web))
  (apply-conf! conf-web)
  (require '[adzerk.boot-cljs]
           '[adzerk.boot-cljs-repl]
           '[adzerk.boot-reload]
           '[pandeiro.boot-http]
           '[adzerk.env]
           '[powerlaces.boot-cljs-devtools])
  (def cljs (resolve 'adzerk.boot-cljs/cljs))
  (def cljs-repl (resolve 'adzerk.boot-cljs-repl/cljs-repl))
  (def reload (resolve 'adzerk.boot-reload/reload))
  (def serve (resolve 'pandeiro.boot-http/serve))
  (def cljs-devtools (resolve 'powerlaces.boot-cljs-devtools/cljs-devtools))
  (def dirac (resolve 'powerlaces.boot-cljs-devtools/dirac)))

(deftask dev-web []
  (init-web)
  (comp (serve)
        (watch)
        (cljs-devtools)
        (reload :client-opts {:debug true})
        (cljs-repl :nrepl-opts {:port 5055})
        (cljs :source-map true
              :optimizations :none
              :compiler-options {:external-config
                                 {:devtools/config {:features-to-install [:formatters :hints]
                                                    :fn-symbol "λ"
                                                    :print-config-overrides true}}})
        (notify :audible true)))

#_(deftask build-web []
    (cljs :optimizations :advanced))
