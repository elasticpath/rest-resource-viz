(ns ^{:author "Andrea Richiardi"
      :doc "ExtractData Mojo for the resource visualization plugin"}
    rest-resources-viz.extract
  (:require [classlojure.core :as classlojure]
            [clojure.java.io :as io]
            [clojure.maven.mojo.defmojo :as mojo]
            [clojure.maven.mojo.log :as log]
            [clojure.pprint :as pp]
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

(defn run-extractor
  [maven-session project opts]
  (log/debugf "Options: %s" opts)

  (let [local-repo (or (.. maven-session getLocalRepository getBasedir)
                       (System/getProperty "maven.local-repo"))
        classpath-urls (into [] java-url->str
                             (.. (Thread/currentThread) getContextClassLoader getURLs))
        edn-target-file (.getCanonicalPath (io/file (:target-directory opts) (:target-name opts)))]
    (log/debugf "Local m2: %s" local-repo)
    (log/debugf "Output path: %s" (:target-directory opts))
    (log/debugf "Plugin Classpath: %s" (with-out-str (pp/pprint classpath-urls)))

    (classlojure/eval-in (classlojure/classlojure classpath-urls)
      `(do (require '[rest-resources-viz.extractor])
           (try
             ;; creating the target folder if it does not exist
             ~(io/make-parents (:target-directory opts))

             ;; extracting the data
             (rest-resources-viz.extractor/spit-graph-data-edn!
              ~edn-target-file
              {:pretty ~(:pretty? opts)})

             ;; copy the web assets
             ~(copy-web-assets! (:target-directory opts))
             (catch Exception ~'e
               (throw (ex-info "Cannot extract data, make sure you added the dependencies to your pom.xml."
                               ~opts
                               ~'e))))))))

(comment
  (defonce invoker (maven/make-invoker (java.lang.System/getenv "M2_HOME") (java.lang.System/getenv "PWD")))
  (def test-pom (-> "pom.xml" io/resource io/file))
  (def goals ["rest-viz-maven-plugin:extract"])
  (def res (maven/invoke-mojo invoker test-pom goals true)))

(mojo/defmojo ExtractMojo
  {:goal "extract"
   :requires-dependency-resolution "compile+runtime"}

  ;; parameters
  [maven-session {:defaultValue "${session}"
                  :readonly true}

   base-directory {:expression "${basedir}"
                   :description "Base dir"
                   :required true
                   :readonly true}

   project {:expression "${project}"
            :description "Project"
            :required true
            :readonly true}

   target-directory {:required true
                     :defaultValue "${project.build.directory}/rest-viz-assets"
                     :alias "restVizExtractTargetDirectory"
                     :description "Output Directory"}

   target-name {:defaultValue "graph-data.edn"
                :alias "restVizExtractTargetName"
                :description "Custom name for the extracted data output file"}

   pretty {:defaultValue "false"
           :alias "restVizExtractDataPrettyPrint"
           :description "Pretty print the extracted data"}]

  (run-extractor maven-session
                 project
                 {:target-directory target-directory
                  :target-name target-name
                  :pretty pretty}))
