(ns ^:figwheel-load rest-resource-viz.core
  (:require [clojure.string :as str]
            [adzerk.env :as env]
            [dommy.core :as dommy]
            [oops.core :as o]
            [cljsjs.d3]
            [rest-resource-viz.util :as u])
  (:require-macros [rest-resource-viz.logging :as l]))

(enable-console-print!)

(defonce app-state (atom {:name "User"}))

;; (swap! app-state assoc :name "Andrea")

(env/def
  RESOURCE_DATA_URL "resource-data.json")

;; (def collapse [node]
;; (if (seq (.-children node))
;; (recur (collapse (first (.-children node))))
;; ))

(def margin {:top 20 :right 120 :bottom 20 :left 120})
(def width (- 960 (:right margin) (:left margin)))
(def height (- 800 (:top margin) (:bottom margin)))
(def tree (-> js/d3 (.tree) (.size (clj->js [width height]))))
(def g (doto (dommy/create-element "g")
         (dommy/set-attr! "transform" (str "translate(" (:left margin) "," (:top margin) ")"))))
(def svg (doto (-> js/d3 (.select "svg") (.node))
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

(defn on-jsload []
  (.info js/console "Reloading Javascript...")

  (let [margin {:top top :right right :bottom bottom :left left}
        width (- 960 right left)
        height (- 800 top bottom)

        #_#_tree (doto js/d3 (.tree) (.size (clj->js [width height])))

        #_#_g (doto (dommy/create-element "g")
                (dommy/set-attr! "transform" (str "translate(" left "," top ")")))

        #_#_svg (doto (-> js/d3 (.select "svg") (.node))
                  (dommy/append! g)
                  (dommy/set-attr! "width" (+ width right left))
                  (dommy/set-attr! "height" (+ height top bottom)))]

    ))
