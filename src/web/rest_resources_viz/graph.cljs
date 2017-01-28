(ns ^{:doc "Namespace including graph-related component and functions"
      :author "Andrea Richiardi"}
    rest-resources-viz.graph
  (:require [clojure.spec :as s]
            [oops.core :as o]
            [cljsjs.d3]
            [reagent.core :as r]
            [rest-resources-viz.model :as model]
            [rest-resources-viz.util :as util])
  (:require-macros [rest-resources-viz.logging :as log]))

(defn event-xy!
  "Set the x and y properties of the input object based on the current x and y of d3.event"
  [d]
  (o/oset! d "x" (o/oget js/d3 "event.x"))
  (o/oset! d "y" (o/oget js/d3 "event.y")))

(defn event-translate! [d3-selection]
  (.attr d3-selection "transform" #(util/translate-str (o/oget js/d3 "event.pageX")
                                                       (o/oget js/d3 "event.pageY"))))

(defn remove-all!
  "Just remove everything in the dom, utility function"
  []
  (-> (js/d3.select "#graph-container svg .graph")
      (.selectAll ".node")
      (.data (clj->js []))
      (.exit)
      (.remove))
  (-> (js/d3.select "#graph-container svg .graph")
      (.selectAll ".link")
      (.data (clj->js []))
      (.exit)
      (.remove)))

(defn zoomed []
  (let [transform (o/oget js/d3 "?event.?transform")]
    (when transform
      (-> (js/d3.select ".graph")
          (.attr "transform" transform)))))

(defn install-graph-events! []
  (let [d3-zoom (-> (js/d3.zoom)
                    (.on "zoom" zoomed))]
    (-> (js/d3.select "svg")
        (.call d3-zoom))))

(defn install-node-events! [d3-selection d3-tooltip attrs hlighted-node-id]
  (-> d3-selection
      (.selectAll "circle")
      (.on "mouseover" #(let [padding (get-in attrs [:tooltip :padding])
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
                           (.style "opacity" 0)))
      (.on "click" #(swap! model/app-state
                           update :hlighted-node-id
                           ;; Implement a toggle-like behavior
                           (fn [old] (let [node-id (o/oget % "id")]
                                       (cond
                                         (nil? old) node-id
                                         (= old node-id) nil
                                         :else node-id)))))))

(defn install-drag!
  [d3-selection simulation]
  (-> d3-selection
      (.selectAll "circle")
      (.call (-> (js/d3.drag)
                 (.on "start" (fn [d]
                                (-> simulation (.alphaTarget 0.3) (.restart))
                                (event-xy! d)))
                 (.on "drag "event-xy!)
                 (.on "end" (fn [d]
                              (-> simulation (.alphaTarget 0))
                              (event-xy! d)))))))

(defn node-enter!
  [graph-selection node-js-data attrs family-index-by-name]
  (let [group (-> graph-selection
                  (.selectAll ".node")
                  (.data node-js-data)
                  (.enter)
                  (.append  "g")
                  (.attr "class" "node"))
        circle (-> group
                   (.append "circle")
                   (.attr "fill" #(model/get-node-color (:family-colors attrs)
                                                        family-index-by-name
                                                        (o/oget % "?family-id"))))]
    group))

(defn link-enter!
  [graph-selection link-js-data attrs]
  (let [group (-> graph-selection
                  (.selectAll ".link")
                  (.data link-js-data)
                  (.enter)
                  (.append "g")
                  (.attr "class" "link"))
        link (-> group
                 (.append "line")
                 (.style "marker-end" "url(#end-arrow)"))]
    group))

(defn labels-enter!
  [graph-selection node-js-data attrs family-index-by-name]
  (let [group (-> graph-selection
                  (.selectAll ".label")
                  (.data node-js-data)
                  (.enter)
                  (.append "g")
                  (.attr "class" "label")
                  (.append "text")
                  (.text #(o/oget % "name"))
                  (.attr "fill" #(model/get-node-color (:family-colors attrs)
                                                       family-index-by-name
                                                       (o/oget % "?family-id")))
                  (.style "fill-opacity" 1))]
    group))

(defn install-simulation!
  [attrs node-js-data link-js-data]
  (-> (js/d3.forceSimulation)
      (.nodes node-js-data)
      (.force "link" (-> (js/d3.forceLink)
                         (.links link-js-data)
                         (.distance (get-in attrs [:link :distance]))
                         (.id #(o/oget % "id"))))
      (.force "collide" (js/d3.forceCollide #(o/oget % "radius")))
      (.force "charge" (-> (js/d3.forceManyBody)
                           (.strength (get-in attrs [:node :strength]))))
      (.force "x" (js/d3.forceX (/ (get-in attrs [:graph :width]) 2)))
      (.force "y" (js/d3.forceY (/ (get-in attrs [:graph :height]) 2)))))

(defn graph-update! [state]
  (let [node-attrs (get-in state [:attrs :node])
        node-js-data (:node-js-data state)
        link-js-data (:link-js-data state)
        hlighted-node-id (:hlighted-node-id state)
        resource-neighbors-by-id (:resource-neighbors-by-id state)]
    (when (seq node-js-data)
      (let [d3-nodes (-> (js/d3.select "#graph-container svg .graph")
                         (.selectAll ".node"))
            d3-texts (-> (js/d3.select "#graph-container svg .graph")
                         (.selectAll ".label text"))]
        (-> d3-nodes
            (.transition)
            (.duration 200)
            (.ease js/d3.easeLinear)
            (.style "opacity" #(let [node-id (o/oget % "id")]
                                 (cond
                                   (nil? hlighted-node-id) 1
                                   (= node-id hlighted-node-id) 1
                                   (contains? (resource-neighbors-by-id (keyword hlighted-node-id))
                                              (keyword node-id)) 1
                                   :else 0.1))))
        (-> d3-texts
            (.attr "dx" #(+ (o/oget % "radius") 4))
            (.attr "dy" #(+ (o/oget % "radius") 4))
            (.transition)
            (.delay 100)
            (.duration 200)
            (.ease js/d3.easeLinear)
            (.style "fill-opacity" #(let [node-id (o/oget % "id")]
                                      (cond
                                        (nil? hlighted-node-id) 0
                                        (= node-id hlighted-node-id) 1
                                        (contains? (resource-neighbors-by-id (keyword hlighted-node-id))
                                                   (keyword node-id)) 1
                                        :else 0))))
        (-> d3-nodes
            (.selectAll "circle")
            (.attr "r" #(o/oget % "radius")))))

    (when (seq link-js-data)
      (let [d3-links (-> (js/d3.select "#graph-container svg .graph")
                         (.selectAll ".link"))]
        (-> d3-links
            (.transition)
            (.delay 100)
            (.duration 200)
            (.ease js/d3.easeLinear)
            (.style "opacity" #(let [target-id (o/oget % "?target.id")
                                     source-id (o/oget % "?source.id")]
                                 (cond
                                   (nil? hlighted-node-id) 1
                                   (or (= target-id hlighted-node-id) (= source-id hlighted-node-id)) 1
                                   :else 0.1))))))))

(defn graph-enter! [state]
  (let [attrs (:attrs state)
        margin (get-in attrs [:graph :margin])
        width (get-in attrs [:graph :width])
        height (get-in attrs [:graph :height])
        hlighted-node-id (:hlighted-node-id state)
        family-index-by-name (:family-index-by-name state)
        node-js-data (:node-js-data state)
        link-js-data (:link-js-data state)]
    (when (and (seq node-js-data) (seq link-js-data))
      (let [d3-simulation (install-simulation! attrs node-js-data link-js-data)
            d3-graph (js/d3.selectAll "#graph-container svg .graph")
            d3-links (link-enter! d3-graph link-js-data attrs)
            d3-lines (-> d3-links (.selectAll "line"))
            d3-nodes (node-enter! d3-graph node-js-data attrs family-index-by-name)
            d3-circles (-> d3-nodes (.selectAll "circle"))
            d3-labels (labels-enter! d3-graph node-js-data attrs family-index-by-name)]
        (install-graph-events!)
        (install-drag! d3-nodes d3-simulation)
        (install-node-events! d3-nodes attrs)
        (.on d3-simulation "tick" (fn []
                                    (-> d3-lines
                                        (.attr "x1" #(o/oget % "source.x"))
                                        (.attr "y1" #(o/oget % "source.y"))
                                        (.attr "x2" #(o/oget % "target.x"))
                                        (.attr "y2" #(o/oget % "target.y")))
                                    (-> d3-labels
                                        (.attr "transform" #(let [r (o/oget % "radius")]
                                                              (util/translate-str (max r (min (- width r) (o/oget % "x")))
                                                                                  (max r (min (- height r) (o/oget % "y")))))))
                                    (-> d3-circles
                                        (.attr "transform" #(let [r (o/oget % "radius")]
                                                              (util/translate-str (max r (min (- width r) (o/oget % "x")))
                                                                                  (max r (min (- height r) (o/oget % "y")))))))))))))

(defn graph-exit! [state]
  (let [node-js-data (:node-js-data state)
        link-js-data (:link-js-data state)]
    (-> (js/d3.select "#graph-container svg .graph")
        (.selectAll ".link")
        (.data link-js-data)
        .exit
        .remove)
    (-> (js/d3.select "#graph-container svg .graph")
        (.selectAll ".node")
        (.data node-js-data)
        .exit
        .remove)))

(defn graph! [state]
  (graph-enter! state)
  (graph-update! state)
  (graph-exit! state))

(defn svg-markers []
  [:defs
   [:marker {:id "end-arrow" :viewBox "0 -5 10 10" :refX 17 :refY 0
             :markerWidth 6 :markerHeight 6 :markerUnits "strokeWidth"
             :orient "auto"}
    [:path {:d "M0,-5L10,0L0,5"}]]])

(defn tooltip [state]
  (let [tooltip-attrs (get-in state [:attrs :tooltip])]
    [:g {:class "tooltip" :style {:opacity 0}}
     [:rect {:width (:width tooltip-attrs)
             :height (:height tooltip-attrs)
             :stroke-width (str (:stroke-width tooltip-attrs) "px")
             :rx (:rx tooltip-attrs)
             :ry (:ry tooltip-attrs)}]
     [:text {:dx (:padding tooltip-attrs)
             :dy (+ (* (:padding tooltip-attrs) 2) (/ (:stroke-width tooltip-attrs) 2))}]]))

(defn graph-render [state]
  (let [width (get-in state [:attrs :graph :width])
        height (get-in state [:attrs :graph :height])]
    [:div {:id "graph-container"}
     [:svg {:width width :height height}
      [svg-markers]
      [:g.graph]
      [tooltip state]]]))

(defn container [state]
  (log/debug "component creation" state)
  (r/create-class
   {:display-name "graph-container"
    :reagent-render graph-render
    :component-did-update (fn [this]
                            (let [state (r/props this)]
                              (do (log/debug "graph-did-update" state)
                                  (graph! state))))
    :component-did-mount (fn [this]
                           (let [state (r/props this)]
                             (log/debug "graph-did-mount" state)
                             (graph! state)))}))
