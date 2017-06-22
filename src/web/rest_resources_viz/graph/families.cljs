(ns ^{:doc "The graph that deals with families resides here."
      :author "Andrea Richiardi"}
    rest-resources-viz.graph.families
  (:require [clojure.spec.alpha :as s]
            [oops.core :as o]
            [rest-resources-viz.model :as model]
            [rest-resources-viz.util :as util])
  (:require-macros [rest-resources-viz.logging :as log]))

(defn family-widget-font
  [attrs line-height]
  (->> (/ line-height 1.618)
       (max (:font-size-min attrs))
       (min (:font-size-max attrs))))

(s/fdef highlighted-family?
  :args (s/cat :hovered-node ::model/clicked-node
               :highlighted-nodes ::model/highlighted-nodes-or-nil
               :family-name string?)
  :ret boolean?)

(defn highlighted-family?
  "True if the family needs to be highlighted, false otherwise."
  [hovered-node highlighted-nodes family-name]
  (let [family-of-hovered (some-> hovered-node (o/oget "family-id"))
        ;; I can take the first here because the spec guarantees that only
        ;; nodes with the same family can be selected.
        highlighted-family (some-> highlighted-nodes
                                   first
                                   (o/oget "family-id"))
        highlighted-nodes? (seq highlighted-nodes)]
    (cond
      (and highlighted-nodes?
           (= family-name highlighted-family)) true
      (and (not highlighted-nodes?)
           hovered-node
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
        highlighted-nodes @(model/track-highlighted-nodes)
        hovered-node @model/hovered-node-state
        resources-by-id (:resources-by-id graph-data)
        resource-neighbors-by-id (:resource-neighbors-by-id graph-data)]
    (log/debug "Highlighted nodes" highlighted-nodes)
    (log/debug "Hovered node" hovered-node)
    [:g {:id "family-widget-container"
         :style {:opacity 1}}
     (for [[i family-name] (->> family-index-by-name
                                keys
                                sort
                                (map-indexed vector))
           :let [highlighted? (highlighted-family? hovered-node
                                                   highlighted-nodes
                                                   family-name)]]
       ^{:key family-name}
       [:text {:x (:left margin)
               :y (+ (:top margin) (:bottom margin) (* i line-height))
               :fill (model/get-node-color family-colors family-index-by-name family-name)
               :font-size (* (cond
                               (and (not hovered-node) (not highlighted-nodes)) 1
                               highlighted? 1.1
                               :else 1)
                             (family-widget-font panel-attrs line-height))
               :fill-opacity (cond
                               (and (not hovered-node) (not highlighted-nodes)) 1
                               highlighted? 1
                               :else 0.1)
               :onClick #(swap! model/app-state model/clicked-family (keyword family-name))}
        family-name])]))
