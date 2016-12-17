(def project 'rest-resources-viz)
(def version "0.1.0-SNAPSHOT")

(task-options!
 pom {:project     project
      :version     version
      :description "Visualizations for rest resources in Cortex"
      :url         "http://example/FIXME"
      :scm         {:url "https://github.com/yourname/rest-resources-viz"}
      :license     {}})

(def conf
  {:env {:resource-paths #{"resources" "src"}
         :source-paths #{"test"}
         :dependencies '[[org.clojure/clojure "1.9.0-alpha14"]
                         [org.clojure/data.xml "0.0.8"]
                         [org.clojure/data.zip "0.1.2"]
                         [adzerk/boot-test "RELEASE" :scope "test"] ]}})


(require '[clojure.edn :as edn]
         '[boot.util :as util])

(def resources-group-id "com.elasticpath.rest.definitions")
(def resources-format "%s/ep-resource-%s-api") ;; need group and resource name
(def resources-version "0-SNAPSHOT") ;; TODO, read it from env vars?

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

(defn add-resources-deps!
  "Returns a vector containing the rest-resources coordinates given for
  the modules. It reads the modules collection names from a file called
  modules.edn."
  [conf]
  (let [coords (->> "modules.edn"
                    slurp
                    edn/read-string
                    (map #(vector (-> (format resources-format resources-group-id %)
                                      symbol)
                                  resources-version)))]
    (util/dbug "Rest resources coordinates %s\n" (vec coords))
    (update-in conf [:env :dependencies] #(-> % (concat coords) distinct vec))))

;;;;;;;;;;;
;; Tasks ;;
;;;;;;;;;;;

(deftask build
  "Build and install the project locally."
  []

  (comp (pom) (jar) (install)))

(deftask dev
  "Start the dev interactive environment."
  []
  (util/info "Starting interactive dev...\n")
  (let [new-conf (add-resources-deps! conf)]
    (util/dbug "Current conf:\n%s\n" (util/pp-str new-conf))
    (apply-conf! new-conf)
    (comp (repl :server true)
          (wait))))
