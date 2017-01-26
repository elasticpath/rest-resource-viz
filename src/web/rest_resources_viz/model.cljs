(ns ^{:doc "The namespace containing state and model-related functions"
      :author "Andrea Richiardi"}
    rest-resources-viz.model
  (:require [clojure.spec :as s]
            [reagent.core :as r]
            [rest-resources-viz.xform :as xform])
  (:require-macros [rest-resources-viz.logging :as log]))

(def init-state {:attrs {:graph {:margin {:top 20 :right 20 :bottom 20 :left 20}
                                 :width 960
                                 :height 840}
                         :node {:colors ["#000000" "#0cc402" "#fc0a18" "#aea7a5" "#5dafbd" "#d99f07" "#11a5fe" "#037e43" "#ba4455" "#d10aff" "#9354a6" "#7b6d2b" "#08bbbb" "#95b42d" "#b54e04" "#ee74ff" "#2d7593" "#e19772" "#fa7fbe" "#62bd33" "#aea0db" "#905e76" "#92b27a" "#03c262" "#878aff" "#4a7662" "#ff6757" "#fe8504" "#9340e1" "#2a8602" "#07b6e5" "#d21170" "#526ab3" "#015eff" "#bb2ea7" "#09bf91" "#90624c" "#bba94a" "#a26c05"]
                                :base-radius 6
                                :default-color "steelblue"
                                :strength -100}
                         :link {:distance 32}
                         :tooltip {:width 100 :height 20
                                   :padding 10 :stroke-width 2
                                   :rx 5 :ry 5
                                   :dx 2 :dy 4}}
                 :hlighted-node-id nil})

(defonce app-state (r/atom init-state))
(defonce graph-data-state (r/cursor app-state [:graph-data]))
(defonce attrs-state (r/cursor app-state [:attrs]))
(defonce hlighted-node-id-state (r/cursor app-state [:hlighted-node-id]))

(defn trash-graph-data! []
  (swap! app-state assoc :graph-data nil))

(defn reset-state! []
  (reset! app-state init-state))

(defn get-in-and-assign-kind
  "Similarly to get-in, tries to get from ks and also assoc the last
  selector as :kind.

  It assumes you can map over the retrieved thing, does not handle
  literal values (for now)."
  [m ks]
  (->> (get-in m ks)
       (mapv #(assoc % :kind (last ks)))))

(defn get-relationships []
  (let [rels (->> (concat (get-in-and-assign-kind @graph-data-state [:relationship])
                          (get-in-and-assign-kind @graph-data-state [:list-of-relationship])
                          (get-in-and-assign-kind @graph-data-state [:pagination-relationship])
                          (get-in-and-assign-kind @graph-data-state [:alias-relationship]))
                  (xform/unfold-relationships))]
    (log/debug "Relationships" (sort-by :name rels))
    (log/debug "Relationships count" (count rels))
    (log/debug "Relationships sample" (second rels))
    rels))

(defn get-relationships-by-target []
  (group-by :target @(r/track get-relationships)))

(defn get-relationships-by-source []
  (group-by :source @(r/track get-relationships)))

(defn node-radius
  "Return the radius for a node"
  [base-radius rel-count rel-total]
  ;; AR - arbitrary multiplier here, we are also good with failing in case the
  ;; total is zero
  (let [multiplier (* 3 (/ rel-count rel-total))]
    (+ base-radius (* base-radius multiplier))))

(defn relationship-bounds
  "Calculate lower and upper bounds for relationships

  Return a data structure like so:
    {:target-rel-lower-bound _
     :target-rel-upper-bound _
     :source-rel-lower-bound _
     :source-rel-upper-bound _}

  It requires the presence of :target-rel-count and :source-rel-count"
  [resources]
  (reduce
   (fn [{:keys [target-rel-lower-bound target-rel-upper-bound source-rel-lower-bound source-rel-upper-bound] :as totals}
        {:keys [target-rel-count source-rel-count]}]
     (cond
       (and (not source-rel-count) (not target-rel-count)) totals
       (and target-rel-count (< target-rel-count target-rel-lower-bound)) (assoc totals :target-rel-lower-bound target-rel-count)
       (and target-rel-count (> target-rel-count target-rel-upper-bound)) (assoc totals :target-rel-upper-bound target-rel-count)
       (and source-rel-count (< source-rel-count source-rel-lower-bound)) (assoc totals :source-rel-lower-bound source-rel-count)
       (and source-rel-count (> source-rel-count source-rel-upper-bound)) (assoc totals :source-rel-upper-bound source-rel-count)
       :else totals))
   {:target-rel-lower-bound 0
    :target-rel-upper-bound 0
    :source-rel-lower-bound 0
    :source-rel-upper-bound 0}
   resources))

(defn get-resources []
  (let [rels-by-target @(r/track get-relationships-by-target)
        rels-by-source @(r/track get-relationships-by-source)
        base-radius (get-in @attrs-state [:node :base-radius])
        resources (get-in-and-assign-kind @graph-data-state [:resource])
        resources (map #(merge %
                               {:target-rel-count (or (some->> % :id (get rels-by-target) count) 0)
                                :source-rel-count (or (some->> % :id (get rels-by-source) count) 0)})
                       resources)
        rel-bounds (relationship-bounds resources)
        resources (map #(assoc % :radius (node-radius base-radius
                                                      (or (:source-rel-count %) 0)
                                                      (:source-rel-upper-bound rel-bounds))) resources)]
    (log/debug "Resources" (sort-by :name resources))
    (log/debug "Resource count" (count resources))
    (log/debug "Resource sample" (first resources))
    resources))

(defn get-families []
  (let [families (get-in-and-assign-kind @graph-data-state [:family])]
    (log/debug "Families" (sort-by :name families))
    (log/debug "Family count" (count families))
    (log/debug "Family sample" (second families))
    families))

(defn get-indexed-families []
  (let [families @(r/track get-families)]
    (->> families
         (map-indexed (fn [i v] [(:name v) i]))
         (into {}))))

(defn get-resource-neighbors-by-id []
  (let [resources @(r/track get-resources)
        rels-by-target @(r/track get-relationships-by-target)
        rels-by-source @(r/track get-relationships-by-source)]
    (transduce (map :id)
               (completing (fn [m res-id]
                             (assoc m res-id
                                    (into (or (->> (get rels-by-source res-id)
                                                   (map :target)
                                                   (set))
                                              #{})
                                          (map :source (get rels-by-target res-id))))))
               {}
               resources)))

(defn get-js-nodes []
  (clj->js @(r/track! get-resources)))

(defn get-js-links []
  (clj->js @(r/track! get-relationships)))

(s/fdef get-node-color
  :args (s/cat :colors :graph/colors
               :family-by-name :graph/family->index
               :family-id :graph/family-id)
  :ret string?)

(defn get-node-color [colors family-by-name family-id]
  (get colors (get family-by-name family-id)))
