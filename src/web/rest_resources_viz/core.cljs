(ns ^{:doc "The main namespace"
      :author "Andrea Richiardi"
      :figwheel-load true}
    rest-resources-viz.core
  (:require [cljs.reader :as edn]
            [clojure.spec :as s]
            [clojure.spec.test :as stest :include-macros true]
            [clojure.string :as str]
            [clojure.set :as set]
            [adzerk.env :as env]
            [reagent.core :as r]
            [rest-resources-viz.model :as model]
            [rest-resources-viz.graph :as graph]
            [rest-resources-viz.spec :as rspec]
            [rest-resources-viz.util :as u])
  (:require-macros [rest-resources-viz.logging :as log]))

(enable-console-print!)

(env/def
  RESOURCE_DATA_URL "graph-data.edn")

(defn fetch-data! []
  (u/fetch-file! RESOURCE_DATA_URL (fn [gd]
                                     (if gd
                                       (swap! model/app-state assoc :graph-data (edn/read-string gd))
                                       (log/warn "Cannot fetch data!")))))

(defn btn-draw []
  [:button.btn {:on-click #(do (model/trash-graph-data!) (fetch-data!))}
   "Force draw"])

(defn btn-reset []
  [:button.btn {:on-click #(do (model/reset-state!) (fetch-data!))}
   "Reset state"])

(defn landing []
  [:div
   (when u/debug?
     [:div.row
      [btn-reset]
      [btn-draw]])
   [graph/container {:attrs @model/attrs-state
                     :node-js-data @(r/track model/get-js-nodes)
                     :link-js-data @(r/track model/get-js-links)
                     :hlighted-node-id @model/hlighted-node-id-state
                     :resource-neighbors-by-id @(r/track model/get-resource-neighbors-by-id)
                     :family-by-name @(r/track model/get-indexed-families)}]])

(when u/debug?
  (stest/instrument 'rest-resources-viz.core/get-node-color))

(defn on-jsload []
  (.info js/console "Reloading Javascript...")
  (r/render [landing] (.getElementById js/document "app"))
  (fetch-data!))
