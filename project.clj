(defproject com.elasticpath.rest.definitions/rest-resources-viz "0-SNAPSHOT"
  :description "Visualizations for rest resources in Cortex"
  :url "https://github.com/technomancy/leiningen"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :source-paths ["resources" "src"]
  :dependencies [[org.clojure/clojure "1.9.0-alpha14"]
                 [adzerk/boot-test "RELEASE" :scope "test"]
                 [com.elasticpath.rest.definitions/rest-resources-api "0-SNAPSHOT"]]
  :repositories [["elasticpath-psi" {:url  "http://maven.elasticpath.com/nexus/content/groups/psi"
                                     ;; If a repository contains releases only setting
                                     ;; :snapshots to false will speed up dependencies.
                                     :snapshots false
                                     ;; You can also set the policies for how to handle
                                     ;; :checksum failures to :fail, :warn, or :ignore.
                                     :checksum :fail
                                     ;; How often should this repository be checked for
                                     ;; snapshot updates? (:daily, :always, or :never)
                                     :update :never
                                     ;; You can also apply them to releases only:
                                     :releases {:update :never}}]]
  )
