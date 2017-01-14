(ns rest-resources-viz.extractor
  (:require [clojure.set :as set]
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
  (s/explain :family/entity (->> (classpath-resource-xmls!)
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
  [s]
  (when s
    (let [ss (remove empty? (str/split s #"\." 2))]
      (when (seq ss)
        (apply keyword (remove empty? (str/split s #"\." 2)))))))

(comment
  (nil? (keywordize ""))
  (nil? (keywordize "."))
  (= :line-item (keywordize "line-item"))
  (= :line-item (keywordize ".line-item"))
  (= :carts/line-item (keywordize "carts.line-item")))

(s/fdef add-resource-id
  :args (s/cat :definitions :with-family-id/definitions))

(defn add-resource-id
  [definitions]
  (sp/transform [:family
                 :resource
                 sp/ALL
                 (sp/collect-one [(sp/submap [:family-id :name])])
                 :id]
                (fn [m _] (keywordize (str (-> m :family-id name) "." (:name m))))
                definitions))

(s/fdef add-family-id
  :args (s/cat :definitions :entity/definitions))

(defn add-family-id
  [definitions]
  (sp/transform [:family] #(assoc % :id (keyword (:name %))) definitions))

(defn has-resources?
  [definitions]
  (get-in definitions [:family :resource]))

(s/fdef sanitize-relationship
  :args (s/cat :definitions :with-family-id/definitions))

(defn sanitize-relationship
  [family]
  (sp/transform [:family
                 :relationship
                 sp/ALL]
                #(into %
                       [(when-let [s (:from %)] [:from (keywordize s)])
                        (when-let [s (:to %)] [:to (keywordize s)])])
                family))

(s/fdef normalize-family
  :args (s/cat :definitions :coll-pre-norm/definitions))

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

(s/fdef propagate-family-id
  :args (s/cat :definitions :with-id/definitions))

(defn propagate-family-id
  [definitions]
  (sp/transform [:family
                 (sp/collect-one [:id])
                 sp/MAP-VALS
                 vector?
                 sp/ALL
                 :family-id]
                (fn [id _] id)
                definitions))

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
                      (filter has-resources?)
                      (map add-family-id)
                      (map propagate-family-id)
                      (map add-resource-id)
                      (map sanitize-relationship)))
       normalize-family)) ;; TODO - spec the final version

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
    </definitions>"
  [f & [opts]]
  (spit-xml f (xml-files->definitions (classpath-resource-xmls!)) opts))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (str/join \newline errors)))

(defn exit [status msg]
  (if (= status 0)
    (println msg)
    (binding [*out* *err*]
      (println msg)))
  (System/exit status))

(defn -main [args]
  (if-not (= (count args) 0)
    (exit 127 "The extractor need the module file path as argument.")
    (println "TODO")
    #_(let [ ])))

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
