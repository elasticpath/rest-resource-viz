(ns ^:figwheel-load rest-resources-viz.core
  (:require [cljs.reader :as edn]
            [clojure.string :as str]
            [clojure.set :as set]
            [adzerk.env :as env]
            [oops.core :as o]
            [cljsjs.d3]
            [reagent.core :as r]
            [rest-resources-viz.util :as u]
            [rest-resources-viz.xform :as xform])
  (:require-macros [rest-resources-viz.logging :as log]))

(enable-console-print!)

(env/def
  RESOURCE_DATA_URL "graph-data.edn")

(def init-state {:margin {:top 20 :right 60 :bottom 20 :left 60}
                 :width 960
                 :height 820
                 :attrs {:graph {:strength -75}
                         :node {:radius 8
                                :family->color {}}
                         :link {:distance 50}
                         :tooltip {:width 100 :height 20
                                   :padding 10 :stroke-width 2
                                   :rx 5 :ry 5
                                   :dx 2 :dy 4}}})

(defonce app-state (r/atom init-state))

(defn fetch-data! []
  (u/fetch-file! RESOURCE_DATA_URL (fn [gd]
                                     (if gd
                                       (swap! app-state assoc :graph-data (edn/read-string gd))
                                       (log/warn "Cannot fetch data!")))))

(defn translate-str
  "Return the translate string needed for the transform attribute"
  [x y]
  (str "translate(" x "," y ")"))

(defn get-resources [state]
  (let [resources (get-in @state [:graph-data :resource])]
    (log/debug "Node count" (count resources))
    (log/debug "Node sample" (first resources))
    resources))

(defn get-relationships [state]
  (let [rels (-> @state
                 (get-in [:graph-data :relationship])
                 (xform/unfold-relationships))]
    (log/debug "Link count" (count rels))
    (log/debug "Link sample" (second rels))
    rels))

(defn get-js-nodes [state]
  (clj->js @(r/track get-resources state)))

(defn get-js-links [state]
  (clj->js @(r/track get-relationships state)))

(defn get-width [state]
  (let [margin (:margin @state)]
    (- (:width @state) (:right margin) (:left margin))))

(defn get-height [state]
  (let [margin (:margin @state)]
    (- (:height @state) (:top margin) (:bottom margin))))

(defn event-xy! [d]
  (o/oset! d "x" (o/oget js/d3 "event.x"))
  (o/oset! d "y" (o/oget js/d3 "event.y")))

(defn event-translate! [d3-selection]
  (.attr d3-selection "transform" #(translate-str (o/oget js/d3 "event.pageX")
                                                  (o/oget js/d3 "event.pageY"))))

(defn install-node-events! [d3-selection d3-tooltip tooltip-attrs]
  ;; TODO - display the entities
  ;; (-> d3-tooltip (.select ".content") (.text "Test"))
  (-> d3-selection
      (.on "mouseover" #(let [padding (:padding tooltip-attrs)
                              d3-text (-> d3-tooltip
                                          (.select "text")
                                          (.text (o/oget % "id")))
                              text-bbox (-> d3-text
                                            (.node)
                                            (.getBoundingClientRect))]
                          (-> d3-tooltip
                              (.select "rect")
                              (.attr "width" (+ (* 2 padding) (o/oget text-bbox "width")))
                              (.attr "height" (+ (* 2 padding) (o/oget text-bbox "height"))))
                          (-> d3-tooltip
                              (.transition)
                              (.duration 200)
                              (.style "opacity" 0.9)
                              (event-translate!))))
      (.on "mouseout" #(-> d3-tooltip
                           (.transition)
                           (.duration 500)
                           (.style "opacity" 0)))))

(defn install-drag!
  [d3-selection simulation]
  (.call d3-selection
         (-> (js/d3.drag)
             (.on "start" (fn [d]
                            (-> simulation (.alphaTarget 0.3) (.restart))
                            (event-xy! d)))
             (.on "drag" event-xy!)
             (.on "end" (fn [d]
                          (-> simulation (.alphaTarget 0))
                          (event-xy! d))))))

(defn append-node!
  [d3-selection node-attrs]
  (let [group (-> d3-selection
                  (.append  "g")
                  (.attr "class" "node")
                  (.append "circle"))]
    ;; Text
    #_(-> group
          (.append "text")
          (.attr "dy" 3)
          (.text #(o/oget % "id")))
    group))

(defn append-link!
  [d3-selection _]
  (-> d3-selection
      (.append "g")
      (.attr "class" "link")
      (.append "line")
      (.style "marker-end" "url(#end-arrow)")))

(comment
  (def rels-by-to (group-by :to (first (relationships-with-dups rels))))
  (def ress-by-id (group-by :id ress)))

(defn graph-update! [state]
  (let [node-attrs (get-in @state [:attrs :node])
        d3-nodes @(r/track get-js-nodes state)]
    (when (seq d3-nodes)
      (let [nodes (-> (js/d3.select "#graph-container svg .graph")
                      (.selectAll ".node"))]
        (-> nodes
            (.selectAll "circle")
            (.attr "r" (:radius node-attrs)))))))

(defn graph-enter! [state]
  (let [margin (:margin @state)
        node-attrs (get-in @state [:attrs :node])
        link-attrs (get-in @state [:attrs :link])
        graph-attrs (get-in @state [:attrs :graph])
        tooltip-attrs (get-in @state [:attrs :tooltip])
        width @(r/track get-width state)
        height @(r/track get-height state)
        node-js-data @(r/track get-js-nodes state)
        link-js-data @(r/track get-js-links state)]
    (when (and (seq node-js-data) (seq link-js-data))
      (let [d3-tooltip (js/d3.select "#graph-container svg .tooltip")
            d3-simulation (:simulation (swap! app-state
                                              assoc :simulation
                                              (-> (js/d3.forceSimulation)
                                                  (.nodes node-js-data)
                                                  (.force "link" (-> (js/d3.forceLink)
                                                                     (.links link-js-data)
                                                                     (.distance (:distance link-attrs))
                                                                     (.id (fn [node] (o/oget node "id")))))
                                                  (.force "collide" (js/d3.forceCollide (:radius node-attrs)))
                                                  (.force "charge" (-> (js/d3.forceManyBody)
                                                                       (.strength (:strength graph-attrs))))
                                                  (.force "x" (js/d3.forceX (/ width 2)))
                                                  (.force "y" (js/d3.forceY (/ height 2))))))
            d3-nodes (-> (js/d3.select "#graph-container svg .graph")
                         (.selectAll ".node")
                         (.data node-js-data)
                         (.enter)
                         (append-node! node-attrs)
                         (install-drag! d3-simulation)
                         (install-node-events! d3-tooltip tooltip-attrs))
            d3-links (-> (js/d3.select "#graph-container svg .graph")
                         (.selectAll ".link")
                         (.data link-js-data)
                         (.enter)
                         (append-link! link-attrs))]


        (.on d3-simulation "tick" (fn []
                                    (-> d3-links
                                        (.attr "x1" #(o/oget % "source.x"))
                                        (.attr "y1" #(o/oget % "source.y"))
                                        (.attr "x2" #(o/oget % "target.x"))
                                        (.attr "y2" #(o/oget % "target.y")))
                                    (-> d3-nodes
                                        (.attr "transform" #(translate-str (o/oget % "x") (o/oget % "y"))))))))))

(defn stop-simulation!
  "Helper for stopping the force simulation"
  [state]
  (.stop (:simulation @state)))

(defn start-simulation!
  "Helper for stopping the force simulation"
  [state]
  (.restart (:simulation @state)))

(defn reset-state! []
  (let [state (reset! app-state init-state)]
    (fetch-data!)
    (graph-enter! state)
    (graph-update! state)))

(defn btn-draw [state]
  [:button.btn
   {:on-click #(graph-update! state)}
   "Force draw"])

(defn btn-reset [state]
  [:button.btn
   {:on-click #(reset-state!)}
   "Reset state"])

(defn btn-toggle-simulation [state]
  (let [stopped? (r/atom false)]
    (fn []
      [:button.btn
       {:on-click #(if @stopped?
                     (do (start-simulation! state) (reset! stopped? false))
                     (do (stop-simulation! state) (reset! stopped? true)))}
       (if @stopped?
         "Stop simulation"
         "Start simulation")])))

(defn svg-markers []
  [:defs
   [:marker {:id "end-arrow" :viewBox "0 -5 10 10" :refX 32
             :markerWidth 3.5 :markerHeight 3.5
             :orient "auto"}
    [:path {:d "M0,-5L10,0L0,5"}]]
   [:marker {:id "mark-end-arrow" :viewBox "0 -5 10 10" :refX 7
             :markerWidth 3.5 :markerHeight 3.5
             :orient "auto"}
    [:path {:d "M0,-5L10,0L0,5"}]]])

(defn tooltip [state]
  (let [attrs (get-in @state [:attrs :tooltip])]
    [:g {:class "tooltip" :style {:opacity 0}}
     [:rect {:width (:width attrs)
             :height (:height attrs)
             :stroke-width (str (:stroke-width attrs) "px")
             :rx (:rx attrs)
             :ry (:ry attrs)}]
     [:text {:dx (:padding attrs)
             :dy (+ (* (:padding attrs) 2) (/ (:stroke-width attrs) 2))}]]))

(defn viz-render [state]
  [:div {:id "graph-container"}
   (let [margin (:margin @state)
         width @(r/track get-width state)
         height @(r/track get-height state)]
     [:svg {:width width :height height}
      [svg-markers]
      [:g.graph {:transform (str "translate(" (:left margin) "," (:top margin) ")")}]
      [tooltip state]])])

(defn viz [state]
  (r/create-class
   {:reagent-render #(viz-render state)
    :component-did-update #(graph-update! state)
    :component-did-mount #(graph-enter! state)}))

(defn console [state]
  [:div {:id "console-container"}])

(defn landing [state]
  [:div
   [:div.row
    [btn-reset state]
    [btn-draw state]
    [btn-toggle-simulation state]]
   [viz state]
   [console state]])

(defn on-jsload []
  (.info js/console "Reloading Javacript...")
  (r/render [landing app-state] (.getElementById js/document "app"))
  (fetch-data!))
