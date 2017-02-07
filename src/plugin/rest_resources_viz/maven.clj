(ns rest-resources-viz.maven
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [classlojure.core :as classlojure])
  (:import (java.io File)
           #_(org.apache.maven.shared.invoker InvocationRequest DefaultInvocationRequest
                                              InvocationResult Invoker DefaultInvoker)))

(def default-repositories {"clojars" "https://repo.clojars.org/"
                           "maven-central" "https://repo1.maven.org/maven2/"})

(defn dependency-jar [dep] (some->> dep meta :file))

(defn file->url
  "Convert a java.io.File to URL"
  [^File file]
  (-> file .getCanonicalFile .toURL str))

(def dependency->url
  (comp (map (fn [[x _]] {:dep x :jar (dependency-jar x)}))
        (map :jar)
        (map file->url)
        (map str)))

(def resource->url
  (comp (map io/resource)
        (map str)))

(def string->url
  (comp (map io/file)
        (map file->url)
        (map str)))

;; (defn make-invoker
;;   "Create a Maven Invoker"
;;   ^Invoker [maven-home working-dir]
;;   (doto (DefaultInvoker.)
;;     (.setMavenHome (io/file maven-home))
;;     (.setWorkingDirectory (io/file working-dir))))

;; (defn invoke-mojo
;;   "Invoke the goals on the input pom and return an InvocationResult

;;   The property maven.home will need to point to the location of the
;;   Maven folder that contains the Maven executable."
;;   ^InvocationResult [^Invoker invoker pom goals & [debug?]]
;;   (let [request ^InvocationRequest (doto (DefaultInvocationRequest.)
;;                                      (.setPomFile (io/file pom))
;;                                      (.setGoals goals)
;;                                      (.setDebug (or debug? false))
;;                                      (.setShellEnvironmentInherited true))
;;         result ^InvocationResult (.execute invoker request)]
;;     (.execute invoker request)))



(defn eval-clojure
  "Evaluate a closure form in a classloader with the specified paths"
  [cl form & args]
  (apply classlojure/eval-in
         cl
         form
         args))

;; (ns rest-resources-viz.maven
;;   "General functions for working with maven, from pallet/zi"
;;   (:require [clojure.string :as str])
;;   (:import org.apache.maven.project.DefaultProjectBuildingRequest
;;            org.eclipse.aether.collection.CollectRequest
;;            [org.eclipse.aether.graph Dependency Exclusion DependencyNode]
;;            org.eclipse.aether.resolution.DependencyRequest
;;            org.eclipse.aether.util.artifact.DefaultArtifact))

;; (defn clojure-source-paths
;;   ([source-directory language]
;;    (if (.endsWith source-directory "/java")
;;      [(str/replace source-directory #"/java$" (str "/" language)) source-directory]
;;      [source-directory]))
;;   ([source-directory]
;;    (clojure-source-paths source-directory "clojure")))

;; (defn paths-for-checkout
;;   "Return the source paths for a given pom file"
;;   [repo-system repo-system-session project-builder pom-file]
;;   (let [project (.getProject
;;                  (.build
;;                   project-builder pom-file
;;                   (doto (DefaultProjectBuildingRequest.)
;;                     (.setRepositorySession repo-system-session))))]
;;     (concat
;;      (clojure-source-paths (.. project getBuild getSourceDirectory))
;;      [(.. project getBuild getOutputDirectory)]
;;      (map #(.getDirectory %) (.. project getBuild getResources)))))

;; ;;; Based on code from pomegranate
;; (defn- exclusion
;;   [[group-id artifact-id & {:as opts}]]
;;   (Exclusion. group-id artifact-id (:classifier opts "*") (:extension opts "*")))

;; (defn- coordinate-string
;;   "Produces a coordinate string with a format of
;;    <groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>>
;;    given a lein-style dependency spec.  :extension defaults to jar."
;;   [group-id artifact-id version
;;    {:keys [classifier extension] :or {extension "jar"}}]
;;   (->>
;;    [group-id artifact-id extension classifier version]
;;    (remove nil?)
;;    (interpose \:)
;;    (apply str)))

;; (defn- dependency
;;   [group-id artifact-id version {:keys [scope optional exclusions classifier]
;;                                  :as opts
;;                                  :or {scope "compile" optional false}}]
;;   (Dependency.
;;    (DefaultArtifact. (coordinate-string group-id artifact-id version opts))
;;    scope
;;    optional
;;    (map exclusion exclusions)))

;; (defn dependency-file
;;   [^Dependency dependency]
;;   (.. dependency getArtifact getFile getCanonicalPath))

;; (defn- dependency-files
;;   [node]
;;   (reduce
;;    (fn [files ^DependencyNode n]
;;      (if-let [dependency (.getDependency n)]
;;        (concat
;;         files
;;         [(dependency-file dependency)]
;;         (->> (.getChildren n) (map #(.getDependency %)) (map dependency-file)))
;;        files))
;;    []
;;    (tree-seq (constantly true) #(seq (.getChildren %)) node)))

;; (defn resolve-dependency
;;   [repo-system repo-system-session repositories
;;    group-id artifact version {:keys [scope exclusions classifier] :as opts}]
;;   (let [dependency (dependency group-id artifact version opts)
;;         request (CollectRequest. [dependency] nil repositories)]
;;     (.setRequestContext request "runtime")
;;     (->
;;      (.resolveDependencies
;;       repo-system repo-system-session (DependencyRequest. request nil))
;;      .getRoot
;;      dependency-files)))
