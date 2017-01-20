(ns rest-resources-viz.extractor
  (:require [clojure.tools.cli :as cli]
            [clojure.set :as set]
            [clojure.spec :as s]
            [clojure.spec.test :as stest]
            [clojure.pprint :as pp :refer [pprint]]
            [clojure.xml :as xml]
            [clojure.string :as str]
            [clojure.data.xml :as dx]
            [clojure.data.xml.tree :as dxt]
            [clojure.zip :as zip]
            [clojure.data.zip :as dz]
            [clojure.data.zip.xml :as dzx]
            [clojure.java.io :as io]
            [clojure.walk :as walk]
            [clojure.java.classpath :as cp]
            [cheshire.core :as json]
            [com.rpl.specter :as sp]
            [rest-resources-viz.spec :as rspec]))

(defn remove-nils [m]
  (let [f (fn [x]
            (if (map? x)
              (let [kvs (filter (comp not nil? second) x)]
                (if (empty? kvs) nil (into {} kvs)))
              x))]
    (walk/postwalk f m)))

(defn children
  [node]
  (:content node))

(defn leaf?
  [node]
  (let [cs (children node)]
    (or (empty? cs) (and (= 1 (count cs)) (string? (first cs))))))

(defn leaf->map
  [node]
  {(:tag node) (first (children node))})

(defn vectorizing-reduce-kv
  "TODO doc and maybe improve the name"
  [m1 m2]
  (reduce-kv
   (fn [m1 k2 v2]
     (update m1 k2 (fn [v1]
                     ;; AR - this was tough
                     (if (and (nil? v1) (string? v2))
                       v2
                       (conj
                        (cond
                          (nil? v1) []
                          (sequential? v1) v1
                          ;; I am making it a vector
                          :else [v1])
                        v2)))))
   m1
   m2))

(defn node->clj
  "Covert xml nodes to Clojure data structures"
  [node]
  (if (leaf? node)
    (leaf->map node)
    {(:tag node) (transduce (map node->clj)
                            (completing vectorizing-reduce-kv)
                            {}
                            (children node))}))

(comment
  ;; The following was presenting the issue
  (s/explain :graph-data/entity (->> (classpath-resource-xmls!)
                                     (filter (partial re-find #"shipments-shipping-address"))
                                     (into [] (comp (map parse-resource-xml)
                                                    (map descend-to-family)
                                                    (map node->clj)))
                                     first
                                     :family)))

(defn spit-xml
  "Spit an xml, the opts will be passed to clojure.java.io/writer

  Like in cheshire, if the opts contains :pretty true, the output will
  be pretty printed."
  [f node & [opts]]
  (with-open [w (io/writer f)]
    (if-not (:pretty opts)
      (dx/emit node w)
      (dx/indent node w))))

(defn keywordize
  "Convert \"family.resource\" into :family/resource

  If no \".\" is found, the string is converted to keyword as is"
  [family s]
  (when s
    (let [ss (remove empty? (str/split s #"\." 2))]
      (when (seq ss)
        (if (= 2 (count ss))
          (apply keyword ss)
          (keyword family (first ss)))))))

(s/fdef resource->relationship
  :args (s/cat :from-fn (s/fspec :args (s/cat :resource :resource/entity)
                                 :ret string?)
               :to-fn (s/fspec :args (s/cat :resource :resource/entity)
                               :ret string?)
               :resource :resource/entity)
  :ret :relationship/entity)

(defn resource->relationship
  "Produce a relationship from a resource

  The return value of (to-fn resource) will be assigned to the :to
  field, prepended with the family (required), so it should return a
  string. See spec."
  [from-fn to-fn resource]
  (let [f-id (:family-id resource)
        id (:id resource)]
    {:name (name id)
     :family-id f-id
     :from (keywordize (name f-id) (from-fn resource))
     :to (keywordize (name f-id) (to-fn resource))
     :rel (name id)}))

(defn descend-to-family
  [definitions-node]
  (-> definitions-node :content first))

(defn parse-resource-xml
  "Return the resource xml"
  [resource-xml-path]
  (-> resource-xml-path
      io/resource
      slurp
      (dx/parse-str :namespace-aware false :skip-whitespace true)))

;;;;;;;;;;;;;;;;;;;
;; add-family-id ;;
;;;;;;;;;;;;;;;;;;;
(s/def :add-family-id/resource (s/coll-of (s/keys :req-un [:resource/name :resource/uri]
                                                  :opt-un [:resource/alias :resource/description])
                                          :kind vector?))
(s/def :add-family-id-relationship/from string?)
(s/def :add-family-id-relationship/to string?)
(s/def :add-family-id/relationship (s/coll-of (s/keys :req-un [:relationship/name :relationship/rel
                                                               :add-family-id-relationship/from :add-family-id-relationship/to]
                                                      :opt-un [:relationship/description :relationship/rev])
                                              :kind vector?))
(s/def :add-family-id/family (s/keys :req-un [:family/name]
                                     :opt-un [:family/description :add-family-id/resource :add-family-id/relationship]))
(s/def :add-family-id/definitions (s/keys :req-un [:add-family-id/family]))

(s/fdef add-family-id
  :args (s/cat :definitions :add-family-id/definitions))

(defn add-family-id
  [definitions]
  (sp/transform [(sp/must :family)]
                #(assoc % :id (keyword (:name %)))
                definitions))

;;;;;;;;;;;;;;;;;;;;;;;;;
;; propagate-family-id ;;
;;;;;;;;;;;;;;;;;;;;;;;;;
(s/def :propagate-family-id/family (s/merge :add-family-id/family
                                            (s/keys :req-un [:family/id])))

(s/def :propagate-family-id/definitions (s/keys :req-un [:propagate-family-id/family]))

(s/fdef propagate-family-id
  :args (s/cat :definitions :propagate-family-id/definitions))

(defn propagate-family-id
  [definitions]
  (sp/transform [(sp/must :family)
                 (sp/collect-one [:id])
                 sp/MAP-VALS
                 vector?
                 sp/ALL
                 :family-id]
                (fn [id _] id)
                definitions))

;;;;;;;;;;;;;;;;;;;;;
;; add-resource-id ;;
;;;;;;;;;;;;;;;;;;;;;
(s/def :add-resource-id/resource (s/coll-of (s/keys :req-un [:resource/name :resource/uri :resource/family-id]
                                                    :opt-un [:resource/alias :resource/description])
                                            :kind vector?))
(s/def :add-resource-id/family (s/merge :propagate-family-id/family
                                        (s/keys :opt-un [:add-resource-id/resource])))
(s/def :add-resource-id/definitions (s/keys :req-un [:add-resource-id/family]))

(s/fdef add-resource-id
  :args (s/cat :definitions :add-resource-id/definitions))

(defn add-resource-id
  [definitions]
  (sp/transform [(sp/must :family)
                 (sp/must :resource)
                 sp/ALL
                 (sp/collect-one [(sp/submap [:family-id :name])])
                 :id]
                (fn [m _] (keywordize (-> m :family-id name) (:name m)))
                definitions))

;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; sanitize-relationship ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;
(s/def :sanitize-relationship/resource (s/coll-of (s/keys :req-un [:resource/family-id :resource/id :resource/name :resource/uri]
                                                          :opt-un [:resource/alias :resource/description])
                                                  :kind vector?))
(s/def :sanitize-relationship/family (s/keys :req-un [:family/name]
                                             :opt-un [:family/description
                                                      :sanitize-relationship/resource
                                                      :add-family-id/relationship]))
(s/def :sanitize-relationship/definitions (s/keys :req-un [:sanitize-relationship/family]))

(s/fdef sanitize-relationship
  :args (s/cat :definitions :sanitize-relationship/definitions))

(defn sanitize-relationship
  [family]
  (sp/transform [(sp/must :family)
                 (sp/must :relationship)
                 sp/ALL
                 (sp/collect-one [:family-id])]
                (fn [family-id rel]
                  (into rel
                        [(when-let [s (:from rel)] [:from (keywordize (name family-id) s)])
                         (when-let [s (:to rel)] [:to (keywordize (name family-id) s)])]))
                family))

;;;;;;;;;;;;;;;;;;;;;;
;; normalize-family ;;
;;;;;;;;;;;;;;;;;;;;;;
(s/def :normalize-family/relationship (s/coll-of :relationship/entity :kind vector?))
(s/def :normalize-family/family (s/keys :req-un [:family/name]
                                        :opt-un [:family/description
                                                 :sanitize-relationship/resource
                                                 :normalize-family/relationship]))
(s/def :normalize-family/definitions (s/keys :req-un [:normalize-family/family]))

(s/fdef normalize-family
  :args (s/cat :defs (s/coll-of :normalize-family/definitions :kind vector?)))

(defn normalize-family
  [definitions]
  (merge {:family (transduce (map :family)
                             (completing conj)
                             []
                             (sp/setval [sp/ALL :family sp/MAP-VALS vector?] sp/NONE definitions))}
         (transduce (map identity)
                    (completing (fn [acc [k v]]
                                  (update acc k #(into (or % []) v))))
                    {}
                    (sp/select [sp/ALL :family sp/ALL (sp/pred (comp vector? second))]
                               definitions))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; add-list-of-relationship ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(s/def :add-list-of-relationship/family (s/coll-of :family/entity :kind vector?))
(s/def :add-list-of-relationship/resource (s/coll-of :resource/entity :kind vector?))
(s/def :add-list-of-relationship/relationship (s/coll-of :relationship/entity :kind vector?))
(s/def :add-list-of-relationship/graph (s/keys :req-un [:add-list-of-relationship/family]
                                               :opt-un [:add-list-of-relationship/resource
                                                        :add-list-of-relationship/relationship]))

(s/fdef add-list-of-relationship
  :args (s/cat :defs :add-list-of-relationship/graph))

(defn add-list-of-relationship
  [definitions]
  (assoc definitions :list-of-relationship (->> definitions
                                                (sp/select-one [:resource (sp/filterer #(:list-of %))])
                                                (mapv (partial resource->relationship :name :list-of)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; add-pagination-relationship ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn add-pagination-relationship
  [definitions]
  (assoc definitions :pagination-relationship (->> definitions
                                                   (sp/select-one [:resource (sp/filterer #(:paginates %))])
                                                   (mapv (partial resource->relationship :name :paginates)))))

(comment
  (sp/select-one [:resource (sp/filterer #(:list-of %))] gd))

(defn xml-files->graph-data
  "Transform data in order to obtain a format that is good for a graph
  visualization.

  The input is a string sequence of paths and the ruturn is the a
  Clojure map."
  [xml-files]
  (->> xml-files
       (into [] (comp (map parse-resource-xml)
                      (map descend-to-family)
                      (map node->clj)
                      (map add-family-id)
                      (map propagate-family-id)
                      (map add-resource-id)
                      (map sanitize-relationship)))
       normalize-family
       add-list-of-relationship
       add-pagination-relationship)) ;; TODO - spec the final version

(defn xml-files->definitions
  "Aggregate <family> under <definitions>

  The input is a string sequence of paths and the ruturn is the a
  Clojure map of xml nodes."
  [xml-files]
  (->> xml-files
       (into [] (comp (map parse-resource-xml)
                      (map descend-to-family)))
       (dx/element :definitions {})))

(defn resource-xml?
  "Is the file path a resource xml?"
  [file-path]
  (re-find #"META-INF\/rest-definitions\/.*\.xml$" file-path))

(defn classpath-resource-xmls!
  []
  (into [] (comp (map cp/filenames-in-jar)
                 (mapcat identity)
                 (filter resource-xml?))
        (cp/classpath-jarfiles)))

(defn spit-graph-data-edn!
  "Write to file the resource graph data in json"
  [f & [opts]]
  (let [graph-data (xml-files->graph-data (classpath-resource-xmls!))]
    (s/assert* :graph-data/entity graph-data)
    (apply spit f (if-not (:pretty opts)
                    (pr-str graph-data)
                    ;; AR - use fipp
                    (with-out-str (pprint graph-data))) (flatten opts))))

(defn spit-graph-data-json!
  "Write to file the resource graph data in json"
  [f & [opts]]
  (let [graph-data (xml-files->graph-data (classpath-resource-xmls!))]
    (s/assert* :graph-data/entity graph-data)
    (apply spit f (json/encode graph-data opts) (flatten opts))))

(defn spit-family-xml!
  "Emit an aggregate version of the xml definitions

  The xml will have the form:
    <definitions>
      <family>
      </family>
      <family>
      </family>
      ...
    </definitions>

  If the opts contains :pretty true, the output will be pretty printed."
  [f & [opts]]
  (spit-xml f (xml-files->definitions (classpath-resource-xmls!)) opts))

(defn usage [options-summary]
  (->> [""
        "Dump resource data to disk."
        ""
        "Usage: boot [ extract -- options ]"
        ""
        "Options:"
        options-summary]
       (str/join \newline)))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (str/join \newline errors)))

(defn exit [status msg]
  (if (= status 0)
    (println msg)
    (binding [*out* *err*]
      (println msg)))
  (System/exit status))

(def cli-options
  [["-f" "--family-xml FILE-PATH" "Dumps an xml with all the families defined"]
   ["-g" "--graph-edn FILE-PATH" "Dumps an edn containing the graph data"]
   ["-p" "--pretty" "Pretty prints the output"]
   ["-h" "--help" "Prints out the help"]])

(defn -main [args]
  (let [{:keys [options arguments errors summary]} (cli/parse-opts args cli-options)]
    (cond
      (:help options) (exit 0 (usage summary))
      (:family-xml options) (apply spit-family-xml! (:family-xml options) :pretty (:pretty options))
      (:graph-edn options) (spit-graph-data-edn! (:graph-edn options) {:pretty (:pretty options)})
      errors (exit 1 (error-msg errors))
      :else (exit 1 (usage summary)))))

(comment
  (def file-path "META-INF/rest-definitions/profiles.xml")
  (def xml-root (-> r parse-resource-xml! zip/xml-zip))
  (def root {:tag :root :attrs {} :content (list (->> file-path parse-resource-xml!))})
  (def json-defs (-> root element->map second json/encode))
  (def fs (into [] (comp (map parse-resource-xml)
                         (map descend-to-family)
                         (map node->clj)) (classpath-resource-xmls!)))
  (spit-graph-data-json! "data/graph-data.json" {:pretty true})
  (spit-graph-data-edn! "data/graph-data.edn" {:pretty true})
  (def issue-xml (->> (classpath-resource-xmls!)
                      (filter (partial re-find #"shipments-shipping-address"))
                      (into [] (comp (map parse-resource-xml)
                                     (map descend-to-family)
                                     #_(map node->clj)))
                      first))
  (def relationship #clojure.data.xml.node.Element{:tag :relationship, :attrs {}, :content (#clojure.data.xml.node.Element{:tag :name, :attrs {}, :content ("default-wishlist-from-root")} #clojure.data.xml.node.Element{:tag :description, :attrs {}, :content ("Link from root resource to default wishlist.")} #clojure.data.xml.node.Element{:tag :rel, :attrs {}, :content ("defaultwishlist")} #clojure.data.xml.node.Element{:tag :from, :attrs {}, :content ("base.root")} #clojure.data.xml.node.Element{:tag :to, :attrs {}, :content ("default-wishlist")})})
  (def entity #clojure.data.xml.node.Element{:tag :entity, :attrs {}, :content (#clojure.data.xml.node.Element{:tag :name, :attrs {}, :content ("line-item")} #clojure.data.xml.node.Element{:tag :description, :attrs {}, :content ("A line item in a cart.")} #clojure.data.xml.node.Element{:tag :property, :attrs {}, :content (#clojure.data.xml.node.Element{:tag :name, :attrs {}, :content ("quantity")} #clojure.data.xml.node.Element{:tag :description, :attrs {}, :content ("The total number of items in the line item.")} #clojure.data.xml.node.Element{:tag :integer, :attrs {}, :content ()})} #clojure.data.xml.node.Element{:tag :property, :attrs {}, :content (#clojure.data.xml.node.Element{:tag :name, :attrs {}, :content ("line-item-id")} #clojure.data.xml.node.Element{:tag :description, :attrs {}, :content ("The internal line item identifier.")} #clojure.data.xml.node.Element{:tag :internal, :attrs {}, :content ()} #clojure.data.xml.node.Element{:tag :string, :attrs {}, :content ()})} #clojure.data.xml.node.Element{:tag :property, :attrs {}, :content (#clojure.data.xml.node.Element{:tag :name, :attrs {}, :content ("item-id")} #clojure.data.xml.node.Element{:tag :description, :attrs {}, :content ("The internal item identifier.")} #clojure.data.xml.node.Element{:tag :internal, :attrs {}, :content ()} #clojure.data.xml.node.Element{:tag :string, :attrs {}, :content ()})} #clojure.data.xml.node.Element{:tag :property, :attrs {}, :content (#clojure.data.xml.node.Element{:tag :name, :attrs {}, :content ("cart-id")} #clojure.data.xml.node.Element{:tag :description, :attrs {}, :content ("The internal cart identifier.")} #clojure.data.xml.node.Element{:tag :internal, :attrs {}, :content ()} #clojure.data.xml.node.Element{:tag :string, :attrs {}, :content ()})} #clojure.data.xml.node.Element{:tag :property, :attrs {}, :content (#clojure.data.xml.node.Element{:tag :name, :attrs {}, :content ("configuration")} #clojure.data.xml.node.Element{:tag :description, :attrs {}, :content ("The details of the line item configuration.")} #clojure.data.xml.node.Element{:tag :is-a, :attrs {}, :content ("line-item-configuration")})})})
  )
