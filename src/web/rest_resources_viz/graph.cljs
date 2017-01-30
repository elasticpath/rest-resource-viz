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

(defn toggle-or-nil
  "Simple toggle-like schema, return new iff old is nil or not the same
  as new. Otherwise return nil."
  [new old]
  (cond
    (nil? old) new
    (= old new) nil
    :else new))

(defn install-node-events! [d3-selection attrs]
  (-> d3-selection
      (.selectAll "circle")
      (.on "mouseover" #(swap! model/app-state update :hovered-js-node (partial toggle-or-nil %)))
      (.on "mouseout" #(swap! model/app-state assoc :hovered-js-node nil))
      (.on "click" #(swap! model/app-state update :clicked-js-node (partial toggle-or-nil %)))))

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

(defn node-neighbors
  [resource-neighbors-by-id js-node]
  (get resource-neighbors-by-id (-> js-node (o/oget "id") keyword)))

(defn graph-update! [clicked-js-node attrs graph-data]
  (let [text-attrs (:text attrs)
        node-attrs (:node attrs)
        node-js-data (:node-js-data graph-data)
        link-js-data (:link-js-data graph-data)
        resource-neighbors-by-id (:resource-neighbors-by-id graph-data)
        neighbor-ids-of-clicked (some->> clicked-js-node
                                         (node-neighbors resource-neighbors-by-id))]
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
                                   (nil? clicked-js-node) 1
                                   (or (= node-id (o/oget clicked-js-node "id"))
                                       (contains? neighbor-ids-of-clicked (keyword node-id))) 1
                                   :else 0.1))))
        (-> d3-texts
            (.attr "dx" #(+ (o/oget % "radius") (:dx text-attrs)))
            (.attr "dy" #(+ (o/oget % "radius") (:dy text-attrs)))
            (.transition)
            (.delay 100)
            (.duration 200)
            (.ease js/d3.easeLinear)
            (.style "fill-opacity" #(let [node-id (o/oget % "id")]
                                      (cond
                                        (nil? clicked-js-node) 0
                                        (or (= node-id clicked-js-node)
                                            (contains? neighbor-ids-of-clicked (keyword node-id))) 1
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
            (.style "opacity" #(let [target (o/oget % "?target")
                                     source (o/oget % "?source")]
                                 (cond
                                   (nil? clicked-js-node) 1
                                   (or (= target clicked-js-node) (= source clicked-js-node)) 1
                                   :else 0.1))))))))

(defn graph-enter! [clicked-js-node attrs graph-data]
  (let [margin (get-in attrs [:graph :margin])
        width (get-in attrs [:graph :width])
        height (get-in attrs [:graph :height])
        family-index-by-name (:family-index-by-name graph-data)
        node-js-data (:node-js-data graph-data)
        link-js-data (:link-js-data graph-data)
        resource-neighbors-by-id (:resource-neighbors-by-id graph-data)]
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

(defn graph-exit! [clicked-js-node attrs graph-data]
  (let [node-js-data (:node-js-data graph-data)
        link-js-data (:link-js-data graph-data)]
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

(defn graph! [clicked attrs graph-data]
  (graph-enter! clicked attrs graph-data)
  (graph-update! clicked attrs graph-data)
  (graph-exit! clicked attrs graph-data))

(defn svg-markers []
  [:defs
   [:marker {:id "end-arrow" :viewBox "0 -5 10 10" :refX 17 :refY 0
             :markerWidth 6 :markerHeight 6 :markerUnits "strokeWidth"
             :orient "auto"}
    [:path {:d "M0,-5L10,0L0,5"}]]])

(defn family-panel [attrs graph-data]
  (let [margin (get-in attrs [:graph :margin])
        width (get-in attrs [:graph :width])
        height (get-in attrs [:graph :height])
        tooltip-attrs (:family-panel attrs)
        family-colors (:family-colors attrs)
        family-index-by-name (:family-index-by-name graph-data)
        text-spacing (/ (- height (:top margin) (:bottom margin)) (count family-index-by-name))
        clicked-js-node @model/clicked-js-node-state
        hovered-js-node @model/hovered-js-node-state
        family-of-hovered (some-> hovered-js-node (o/oget "family-id"))
        family-of-clicked (some-> clicked-js-node (o/oget "family-id"))
        resources-by-id (:resources-by-id graph-data)
        resource-neighbors-by-id (:resource-neighbors-by-id graph-data)
        neighbor-families-of-clicked (some->> clicked-js-node
                                              (node-neighbors resource-neighbors-by-id)
                                              (map #(->> %
                                                         (get resources-by-id)
                                                         :family-id
                                                         clj->js))
                                              set)]
    (log/debug "Clicked node" clicked-js-node)
    (log/debug "Families of clicked node" neighbor-families-of-clicked)
    [:g {:id "family-panel-container"
         :style {:opacity 1}}
     [:text {:id "family-panel-text-container"}
      (for [[i family-name] (->> family-index-by-name
                                 keys
                                 sort
                                 (map-indexed vector))]
        ^{:key family-name}
        [:tspan {:x (:left margin)
                 :y (+ (:top margin) (:bottom margin) (* i text-spacing))
                 :fill (model/get-node-color family-colors family-index-by-name family-name)
                 :fill-opacity (cond
                                 (and (not hovered-js-node) (not clicked-js-node)) 1
                                 (and clicked-js-node
                                      (or (= family-name family-of-clicked)
                                          (contains? neighbor-families-of-clicked family-name))) 1
                                 (and (not clicked-js-node)
                                      hovered-js-node
                                      (= family-name family-of-hovered)) 1
                                 :else 0.1)}
         family-name])]]))

;; (defn tooltip [state]
;;   (let [tooltip-attrs (get-in state [:attrs :tooltip])]
;;     [:g {:class "tooltip" :style {:opacity 0}}
;;      [:rect {:width (:width tooltip-attrs)
;;              :height (:height tooltip-attrs)
;;              :stroke-width (str (:stroke-width tooltip-attrs) "px")
;;              :rx (:rx tooltip-attrs)
;;              :ry (:ry tooltip-attrs)}]
;;      [:text {:dx (:padding tooltip-attrs)
;;              :dy (+ (* (:padding tooltip-attrs) 2) (/ (:stroke-width tooltip-attrs) 2))}]]))

(defn graph-render [clicked attrs graph-data]
  (log/debug "Graph-render: attrs" attrs)
  (log/debug "Graph-render: graph-data" graph-data)
  (let [width (get-in attrs [:graph :width])
        height (get-in attrs [:graph :height])]
    [:svg {:width width :height height}
     [svg-markers]
     [:g.graph]
     [family-panel attrs graph-data]]))

(defn graph [clicked attrs graph-data]
  (log/debug "Init: attrs" attrs)
  (log/debug "Init: graph-data" graph-data)
  (r/create-class
   {:display-name "graph"
    :reagent-render graph-render
    :component-did-update (fn [this]
                            (let [[_ clicked attrs graph-data] (r/argv this)]
                              (log/debug "Did-update: clicked" clicked)
                              (log/debug "Did-update: attrs" attrs)
                              (log/debug "Did-update: graph-data" graph-data)
                              (graph! clicked attrs graph-data)))
    :component-did-mount (fn [this]
                           (let [[_ clicked attrs graph-data] (r/argv this)]
                             (log/debug "Did-mount: clicked" clicked)
                             (log/debug "Did-mount: attrs" attrs)
                             (log/debug "Did-mount: graph-data" graph-data)
                             (graph! clicked attrs graph-data)))}))

(defn container []
  [:div {:id "graph-container"}
   [graph
    @model/clicked-js-node-state
    @model/attrs-state
    {:node-js-data @(r/track model/get-js-nodes)
     :link-js-data @(r/track model/get-js-links)
     :resource-neighbors-by-id @(r/track model/get-resource-neighbors-by-id)
     :resources-by-id @(r/track model/get-resources-by-id)
     :family-index-by-name @(r/track model/get-family-index-by-name)}]])
