(ns ^{:doc "Generate the data file"
      :author "Andrea Richiardi"}
    rest-resource-viz.generate
  (:require [clojure.java.io :as io]
            [zi.mojo :as mojo]
            [zi.core :as core]
            [classlojure.core :as classlojure]
            [clojure.java.io :as io]
            [rest-resources-viz.extractor :as extr]))

(defn run-extractor
  [repo-system repo-system-session project classpath-elements
   source-paths target-path]
  (let [cl (core/classloader-for
            (concat classpath-elements marginalia-deps))]
    #_(classlojure/eval-in
     cl
     `(do
        (require 'marginalia.core)
        (require 'marginalia.html)
        (marginalia.core/ensure-directory! ~target-path)
        (binding [marginalia.html/*resources* ""]
          (marginalia.core/uberdoc!
           ~(.getPath (io/file target-path "uberdoc.html"))
           [~@(map #(.getPath %) (mapcat core/clj-files source-paths))]
           {:name ~(.getName project)
            :version ~(.getVersion project)
            :description ~(core/unindent-description (.getDescription project))
            :dependencies [~@(formatDependencies
                              (filter
                               #(= Artifact/SCOPE_COMPILE (.getScope %))
                               (.getDependencyArtifacts project)))]
            :dev-dependencies [~@(formatDependencies
                                  (filter
                                   #(= Artifact/SCOPE_TEST (.getScope %))

(mojo/defmojo GraphData
  {Goal "graph-data"
   RequiresDependencyResolution "test"}
  [^{Component {:role "org.sonatype.aether.RepositorySystem"}}
   repoSystem

   ^{Parameter {:defaultValue "${repositorySystemSession}" :readonly true}}
   repoSystemSession

   ^{Parameter
     {:defaultValue "${project.build.directory}"
      :alias "graphDatatargetDirectory"
      :description "Where to write the output"}}
   ^String graph-data-target-directory

   ^{Parameter
     {:expression "${project}"
      :description "Project"}}
   project]

  (run-extractor
   repoSystem
   repoSystemSession
   project
   (vec classpath-elements)
   (core/clojure-source-paths source-directory)
   graph-data-target-directory))
