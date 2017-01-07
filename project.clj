(defproject rest-resources-viz/extractor "0.1.0-SNAPSHOT"
  :description "Transformations and visualizations for Cortex Rest resources"
  :resource-paths #{"resources"}
  :source-paths #{"src/task" "src/shared"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha14"]
                 [org.clojure/data.xml "0.2.0-alpha1"]
                 [org.clojure/data.zip "0.1.2"]
                 [org.clojure/test.check "0.9.0"]
                 [cheshire "5.6.3"]
                 [org.clojure/java.classpath "0.2.3"]]
  :main rest-resources-viz.extractor
  :profiles {:dev {:dependencies [[proto-repl "0.3.1"]]
                   :repl-options {:init (set! *print-length* 50)}}})
