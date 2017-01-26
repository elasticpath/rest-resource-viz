(ns ^:figwheel-load rest-resources-viz.core
  (:require [cljs.reader :as edn]
            [clojure.spec :as s]
            [clojure.spec.test :as stest :include-macros true]
            [clojure.string :as str]
            [clojure.set :as set]
            [adzerk.env :as env]
            [oops.core :as o]
            [cljsjs.d3]
            [reagent.core :as r]
            [rest-resources-viz.spec :as rspec]
            [rest-resources-viz.util :as u]
            [rest-resources-viz.xform :as xform])
  (:require-macros [rest-resources-viz.logging :as log]))

(enable-console-print!)

(env/def
  RESOURCE_DATA_URL "graph-data.edn")

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

(defn fetch-data! []
  (u/fetch-file! RESOURCE_DATA_URL (fn [gd]
                                     (if gd
                                       (swap! app-state assoc :graph-data (edn/read-string gd))
                                       (log/warn "Cannot fetch data!")))))

(defn translate-str
  "Return the translate string needed for the transform attribute"
  [x y]
  (str "translate(" x "," y ")"))

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

(defn event-xy! [d]
  (o/oset! d "x" (o/oget js/d3 "event.x"))
  (o/oset! d "y" (o/oget js/d3 "event.y")))

(defn event-translate! [d3-selection]
  (.attr d3-selection "transform" #(translate-str (o/oget js/d3 "event.pageX")
                                                  (o/oget js/d3 "event.pageY"))))

(defn remove-all! []
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
      (.on "click" #(swap! app-state
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
  [graph-selection node-js-data attrs family-by-name]
  (let [group (-> graph-selection
                  (.selectAll ".node")
                  (.data node-js-data)
                  (.enter)
                  (.append  "g")
                  (.attr "class" "node"))
        circle (-> group
                   (.append "circle")
                   (.attr "fill" #(get-node-color (get-in attrs [:node :colors])
                                                  family-by-name
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
  [graph-selection node-js-data attrs]
  (let [group (-> graph-selection
                  (.selectAll ".label")
                  (.data node-js-data)
                  (.enter)
                  (.append "g")
                  (.attr "class" "label")
                  (.append "text")
                  (.text #(o/oget % "name"))
                  (.style "fill-opacity" 1))]
    group))

(comment
  (def rels-by-to (group-by :to (first (relationships-with-dups rels))))
  (def ress-by-id (group-by :id ress)))

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
        family-by-name (:family-by-name state)
        node-js-data (:node-js-data state)
        link-js-data (:link-js-data state)]
    (when (and (seq node-js-data) (seq link-js-data))
      (let [d3-tooltip (js/d3.select "#graph-container svg .tooltip")
            d3-simulation (install-simulation! attrs node-js-data link-js-data)
            d3-graph (js/d3.selectAll "#graph-container svg .graph")
            d3-links (link-enter! d3-graph link-js-data attrs)
            d3-lines (-> d3-links (.selectAll "line"))
            d3-nodes (node-enter! d3-graph node-js-data attrs family-by-name)
            d3-circles (-> d3-nodes (.selectAll "circle"))
            d3-labels (labels-enter! d3-graph node-js-data attrs)]
        (install-graph-events!)
        (install-drag! d3-nodes d3-simulation)
        (install-node-events! d3-nodes d3-tooltip attrs hlighted-node-id)
        (.on d3-simulation "tick" (fn []
                                    (-> d3-lines
                                        (.attr "x1" #(o/oget % "source.x"))
                                        (.attr "y1" #(o/oget % "source.y"))
                                        (.attr "x2" #(o/oget % "target.x"))
                                        (.attr "y2" #(o/oget % "target.y")))
                                    (-> d3-labels
                                        (.attr "transform" #(let [r (o/oget % "radius")]
                                                              (translate-str (max r (min (- width r) (o/oget % "x")))
                                                                             (max r (min (- height r) (o/oget % "y")))))))
                                    (-> d3-circles
                                        (.attr "transform" #(let [r (o/oget % "radius")]
                                                              (translate-str (max r (min (- width r) (o/oget % "x")))
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

(defn reset-state! []
  (reset! app-state init-state)
  (fetch-data!))

(defn btn-draw []
  [:button.btn {:on-click #(do (swap! app-state assoc :graph-data nil)
                               (fetch-data!))}
   "Force draw"])

(defn btn-reset []
  [:button.btn {:on-click #(reset-state!)}
   "Reset state"])

(defn svg-markers []
  [:defs
   [:marker {:id "end-arrow" :viewBox "0 -5 10 10" :refX 15 :refY 0
             :markerWidth 6 :markerHeight 6 :markerUnits "strokeWidth"
             :orient "auto"}
    [:path {:d "M0,-5L10,0L0,5"}]]])

(defn tooltip []
  (let [tooltip-attrs (get-in @attrs-state [:tooltip])]
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
      [tooltip]]]))

(defn graph [state]
  (log/warn "component creation" state)
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

(defn landing []
  [:div
   (when u/debug?
     [:div.row
      [btn-reset]
      [btn-draw]])
   [graph {:attrs @attrs-state
           :node-js-data @(r/track get-js-nodes)
           :link-js-data @(r/track get-js-links)
           :hlighted-node-id @hlighted-node-id-state
           :resource-neighbors-by-id @(r/track get-resource-neighbors-by-id)
           :family-by-name @(r/track get-indexed-families)}]])

(when u/debug?
  (stest/instrument 'rest-resources-viz.core/get-node-color))

(defn on-jsload []
  (.info js/console "Reloading Javacript...")
  (r/render [landing] (.getElementById js/document "app"))
  (fetch-data!))
