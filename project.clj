(defproject rest-resources-viz/extractor "0.1.0-SNAPSHOT"
  :description "Visualizations for rest resources in Cortex"
  :resource-paths #{"resources"}
  :source-paths #{"src/task" "src/shared"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha14"]]
         [org.clojure/data.xml "0.2.0-alpha1"]
         [org.clojure/data.zip "0.1.2"]
         [org.clojure/test.check "0.9.0"]
         [cheshire "5.6.3"]
         [org.clojure/java.classpath "0.2.3"]
         [proto-repl "0.3.1"]
  :main rest-resources-viz.extractor)
