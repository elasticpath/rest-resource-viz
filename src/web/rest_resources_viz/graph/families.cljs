(ns ^{:doc "The graph that deals with families resides here."
      :author "Andrea Richiardi"}
    rest-resources-viz.graph.families
  (:require [oops.core :as o]
            [rest-resources-viz.model :as model]
            [rest-resources-viz.util :as util])
  (:require-macros [rest-resources-viz.logging :as log]))

(defn family-widget-font
  [attrs line-height]
  (->> (/ line-height 1.618)
       (max (:font-size-min attrs))
       (min (:font-size-max attrs))))

(defn family-highligthed?
  [neighbor-families-of-clicked hovered clicked family-name]
  (let [family-of-hovered (some-> hovered (o/oget "family-id"))
        family-of-clicked (some-> clicked (o/oget "family-id"))]
    (cond
      (and clicked
           (or (= family-name family-of-clicked)
               (contains? neighbor-families-of-clicked family-name))) true
      (and (not clicked)
           hovered
           (= family-name family-of-hovered)) true
      :else false)))

(defn family-widget [attrs graph-data]
  (let [margin (get-in attrs [:graph :margin])
        width (get-in attrs [:graph :width])
        height (get-in attrs [:graph :height])
        panel-attrs (:family-widget attrs)
        family-colors (:family-colors attrs)
        family-index-by-name (:family-index-by-name graph-data)
        line-height (/ (- height (:top margin) (:bottom margin)) (count family-index-by-name))
        clicked-js-node @model/clicked-js-node-state
        hovered-js-node @model/hovered-js-node-state
        resources-by-id (:resources-by-id graph-data)
        resource-neighbors-by-id (:resource-neighbors-by-id graph-data)
        neighbor-families-of-clicked (some->> clicked-js-node
                                              (model/node-neighbors resource-neighbors-by-id)
                                              (map #(->> %
                                                         (get resources-by-id)
                                                         :family-id
                                                         clj->js))
                                              set)]
    (log/debug "Clicked node" clicked-js-node)
    (log/debug "Hovered node" hovered-js-node)
    (log/debug "Families of clicked node" neighbor-families-of-clicked)
    [:g {:id "family-widget-container"
         :style {:opacity 1}}
     (for [[i family-name] (->> family-index-by-name
                                keys
                                sort
                                (map-indexed vector))
           :let [highlighted? (family-highligthed? neighbor-families-of-clicked
                                                   hovered-js-node
                                                   clicked-js-node
                                                   family-name)]]
       ^{:key family-name}
       [:text {:x (:left margin)
               :y (+ (:top margin) (:bottom margin) (* i line-height))
               :fill (model/get-node-color family-colors family-index-by-name family-name)
               :font-size (* (cond
                               (and (not hovered-js-node) (not clicked-js-node)) 1
                               highlighted? 1.1
                               :else 1)
                             (family-widget-font panel-attrs line-height))
               :fill-opacity (cond
                               (and (not hovered-js-node) (not clicked-js-node)) 1
                               highlighted? 1
                               :else 0.1)}
        family-name])]))
