(ns ^{:author "Andrea Richiardi"
      :doc "ExtractData Mojo for the resource visualization plugin"}
    rest-resources-viz.extract
  (:require [classlojure.core :as classlojure]
            [clojure.java.io :as io]
            [clojure.pprint :as pp]
            [clojure.spec :as s]
            [clojure.string :as str]
            [resauce.core :as res])
  (:import [java.io File InputStream]
           [java.nio.file Files Path Paths StandardCopyOption]))

(def ^:private web-assets-dir "web-assets/")
(def ^:private plugin-jar #"rest-viz-maven-plugin-.*\.jar")

(defn relativize
  "Return the relative path of file as string"
  [^String file-path ^String parent-path]
  (str/replace file-path (re-pattern parent-path) ""))

(def web-assets-xf
  (filter #(re-find plugin-jar %)))

(defn web-assets-resources
  []
  (let [seed (into #{}
                   web-assets-xf
                   (res/resource-dir web-assets-dir))]
    (loop [rs seed
           acc #{}]
      (if (seq rs)
        (recur (mapcat res/url-dir rs) (into acc rs))
        acc))))

(defn copy-web-assets!
  "Copy the web-assets from within our plugin to the output-dir"
  [^File output-dir]
  (let [output-nio-path (some-> output-dir io/file .toURI Paths/get)
        parent-path (-> web-assets-dir res/resources first .toExternalForm)]
    (if-let [web-assets-paths (seq (web-assets-resources))]
      (doseq [path web-assets-paths]
        (let [relative-path (relativize path parent-path)
              relative-resource-path (str web-assets-dir relative-path)
              output-path ^Path (.resolve output-nio-path relative-path)
              output-file ^File (.toFile output-path)]
          (when-not (.isDirectory output-file)
            (io/make-parents output-file)
            (log/debugf "Creating %s..." output-path)
            (with-open [is ^InputStream (.getResourceAsStream (.getContextClassLoader (Thread/currentThread)) relative-resource-path)]
              (Files/copy is output-path (into-array StandardCopyOption [StandardCopyOption/REPLACE_EXISTING])))))))))

(def ^:private java-url->str
  (map #(.toExternalForm %)))

(s/def :plugin.extractor/target-directory string?)
(s/def :plugin.extractor/pretty-print boolean?)
(s/def :plugin.extractor/data-target-name string?)

(s/def :plugin.extract/opts (s/keys :req-un [:plugin.extractor/target-directory]
                                    :opt-un [:plugin.extractor/pretty-print
                                             :plugin.extractor/data-target-name]))

(defn run-extractor
  "Run the extractor."
  [maven-session logger opts]
  (.debug logger (format "Options: %s" opts))
  (s/assert* :plugin.extract/opts opts)

  (let [target-dir-file (io/file (:target-directory opts))
        local-repo (or (.. maven-session getLocalRepository getBasedir)
                       (System/getProperty "maven.local-repo"))
        classpath-urls (into [] java-url->str
                             (.. (Thread/currentThread) getContextClassLoader getURLs))
        edn-target-file (.getCanonicalPath (io/file target-dir-file (:data-target-name opts)))]
    (.debug logger (format "Local m2: %s" local-repo))
    (.debug logger (format "Output path: %s" target-dir-file))
    (.debug logger (format "Plugin Classpath: %s" (with-out-str (pp/pprint classpath-urls))))

    (classlojure/eval-in (classlojure/classlojure classpath-urls)
      `(do (require '[rest-resources-viz.extractor])
           (try
             ;; creating the target folder if it does not exist
             ~(io/make-parents edn-target-file)

             ;; extracting the data
             (rest-resources-viz.extractor/spit-graph-data-edn!
              ~edn-target-file
              {:pretty ~(:pretty-print opts)})

             ;; copy the web assets
             ~(copy-web-assets! logger target-dir-file)
             (catch Exception ~'e
               (throw (ex-info "Cannot extract data, make sure you added the dependencies to your pom.xml."
                               ~opts
                               ~'e))))))))

(comment
  (defonce invoker (maven/make-invoker (java.lang.System/getenv "M2_HOME") (java.lang.System/getenv "PWD")))
  (def test-pom (-> "pom.xml" io/resource io/file))
  (def goals ["rest-viz-maven-plugin:extract"])
  (def res (maven/invoke-mojo invoker test-pom goals true)))
