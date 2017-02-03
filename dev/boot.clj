(ns boot
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [boot.core :refer :all]
            [boot.util :as util]))

(defn set-system-properties!
  "Set a system property for each entry in the map m."
  [m]
  (doseq [kv m]
    (System/setProperty (key kv) (val kv))))

(defn apply-conf!
  "Calls boot.core/set-env! with the content of the :env key and
  System/setProperty for all the key/value pairs in the :props map."
  [conf]
  (util/dbug "Applying conf:\n%s\n" (util/pp-str conf))
  (let [env (:env conf)
        props (:props conf)]
    (apply set-env! (reduce #(into %2 %1) [] env))
    (assert (or (nil? props) (map? props)) "Option :props should be a map.")
    (set-system-properties! props)))

(defn calculate-resource-deps
  "Calculate the dependency vector for artifacts that share group-id and
  names

  Accepts the following map:

    {:module-edn-path \"resources/modules.edn\"
     :resources-group-id \"com.elasticpath.rest.definitions\"
     :resources-format \"%s/ep-resource-%s-api\"
     :resources-version \"0-SNAPSHOT\"
     :additional-deps '[[com.elasticpath.rest.definitions/ep-resource-collections-api \"0-SNAPSHOT\"]]}

  The vector will be calculated by using:

    [(format resources-format resources-group-id module-name) resources-version]

  For each entry in :module-edn-path."
  [{:keys [module-edn-path resources-group-id
           resources-format resources-version]
    :as res-conf}]
  (->> module-edn-path
       io/file
       slurp
       edn/read-string
       (map #(vector (-> (format resources-format resources-group-id %)
                         symbol)
                     resources-version))))
