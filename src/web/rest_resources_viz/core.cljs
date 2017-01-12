(ns ^:figwheel-load rest-resources-viz.core
  (:require [clojure.string :as str]
            [adzerk.env :as env]
            [dommy.core :as dommy]
            [oops.core :as o]
            [cljsjs.d3]
            [reagent.core :as r]
            [rest-resources-viz.util :as u])
  (:require-macros [rest-resources-viz.logging :as l]))

(enable-console-print!)

(env/def
  RESOURCE_DATA_URL "graph-data.json")

#_(def tree (-> js/d3 (.tree) (.size (clj->js [width height]))))
#_(def g (doto (dommy/create-element "g")
           (dommy/set-attr! "transform" (str "translate(" (:left margin) "," (:top margin) ")"))))
#_(def svg (doto (-> js/d3 (.select "svg") (.node))
             (dommy/append! g)
             (dommy/set-attr! "width" (+ width (:left margin) (:right margin)))
             (dommy/set-attr! "height" (+ height (:top margin) (:bottom margin)))))

(defn data-callback
  [error data]
  (l/info "In data callback!")
  (if error (throw error))

  (l/info data))

;; (.json js/d3 RESOURCE_DATA_URL data-callback)
(defn resourcify
  [definitions]

  )

(def init-state {:margin {:top 40 :right 20 :bottom 40 :left 20}
                 :width 400
                 :height 400
                 :nodes {:spacing 100}
                 :data #{{:name "cart" } {:name "wishlist"}}})

(defonce state
  (r/atom init-state))

(defn btn-reset [state]
  [:div
   [:button
    {:on-click #(reset! state init-state)}
    "Reset state"]])

(defn get-width [state]
  (let [margin (:margin @state)]
    (- (:width @state) (:right margin) (:left margin))))

(defn get-height [state]
  (let [margin (:margin @state)]
    (- (:height @state) (:top margin) (:bottom margin))))

(defn graph-enter [state]
  (when-let [data (:data @state)]
    (let [node-enter (-> (js/d3.select "#graph-container svg .graph")
                         (.selectAll "rect")
                         (.data (clj->js data))
                         (.enter))]
      (-> node-enter
          (.append "g")
          (.attr "class" "node")
          (.append "circle")
          (.attr "r" 20)
          (.attr "cy" 100)))))

(defn graph-update [state]
  (let [margin (:margin @state)
        width @(r/track get-width state)
        height @(r/track get-height state)
        spacing (get-in @state [:nodes :spacing])
        data (map-indexed #(assoc %2 :index %1) (:data @state))]
    (-> (js/d3.select "#graph-container svg .graph")
        (.selectAll ".node circle")
        (.data (clj->js data))
        (.attr "cx" (fn [d] (+ (:left margin) (* (o/oget d :index) spacing)))))))

(defn graph-exit [state]
  (when-let [data (:data @state)]
    (let [node-exit (-> (js/d3.select "#graph-container svg .graph")
                        (.selectAll ".node")
                        (.data (clj->js data))
                        (.exit))]
      (-> node-exit
          (.remove)))))

(defn viz [state]
  [:div {:id "graph-container"}
   (let [margin (:margin @state)
         width @(r/track get-width state)
         height @(r/track get-height state)]
     [:svg {:width width :height height}
      [:g.graph {:transform (str "translate(" (:left margin) "," (:top margin) ")")}]])])

(defn landing [state]
  [:div
   [btn-reset state]
   [viz state]])

;; Cheating
(defonce track-graph-enter (r/track! graph-enter state))
(defonce track-graph-update (r/track! graph-update state))
(defonce track-graph-exit (r/track! graph-exit state))

(defn on-jsload []
  (.info js/console "Reloading Javascript...")
  (r/render [landing state] (.getElementById js/document "app"))
  #_(reset! state init-state))
