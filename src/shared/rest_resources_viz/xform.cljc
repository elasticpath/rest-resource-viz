(ns rest-resources-viz.xform
  (:require [clojure.set :as set]))

(defn unfold-relationships
  "Transform relationships and enrich with forward/backward ones

  It returns a set of maps in the form
  twice."
  [relationships]
  (let [xf (map #(set/rename-keys % {:from :source
                                     :to :target
                                     :rel :label}))
        redc (fn [rel-set rel]
               (into rel-set (remove nil? (if-let [rev-label (:rev rel)]
                                            [(dissoc rel :rev)
                                             (-> rel
                                                 (dissoc :rev)
                                                 (assoc :source (:target rel)
                                                        :target (:source rel)
                                                        :label rev-label))]
                                            [rel]))))]
    (transduce xf (completing redc) #{} relationships)))
