(ns rest-resources-viz.extractor
  (:require [clojure.spec :as s]
            [clojure.pprint :as p :refer [pprint]]
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
            [rest-resources-viz.spec :as res-spec]))

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

(defn node->clj
  "Covert xml nodes to Clojure data structures"
  [node]
  (if (leaf? node)
    (leaf->map node)
    {(:tag node) (transduce (map node->clj)
                            (completing (partial merge-with
                                                 #(if (sequential? %1)
                                                    (conj %1 %2)
                                                    [%2])))
                            ;; {:kind (:tag node)}
                            (children node))}))

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

(defn add-resource-id
  [family]
  (s/assert :family/entity (:family family))
  (./pprint family)
  (sp/transform [:family
                 :resource
                 sp/ALL
                 (sp/collect-one [(sp/submap [:family :name])])
                 ]
                (fn [m _] (println _) (assoc _ :id (keywordize (str (some-> m :family name) "." (:name m)))))
                family))

(defn add-family-id
  [family]
  (sp/transform [:family
                 (sp/collect-one [(sp/submap [:family :name])])
                 :id]
                (fn [m _] (keyword (:name m)))
                family))

(defn normalize-relationship
  [family]
  (sp/transform [:family
                 :relationship
                 sp/ALL]
                #(into %
                       [(when-let [s (:from %)] [:from (keywordize s)])
                        (when-let [s (:to %)] [:to (keywordize s)])])
                family))

#_(defn normalize-family
    [family]
    (merge
     (sp/setval [sp/MAP-VALS string?] sp/NONE (:family family))
     (sp/transform [(sp/collect-one [:family (sp/submap [:name :description])])
                    :family
                    sp/MAP-VALS
                    vector?]
                   (fn [family m] (println "---" m) family)
                   family)))

(defn propagate-family-id
  [family]
  (sp/transform [(sp/collect-one [:family (sp/submap [:id])])
                 :family
                 sp/MAP-VALS
                 vector?
                 sp/ALL
                 :family]
                (fn [family _] (:id family))
                family))

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
                      (map add-family-id)
                      (map propagate-family-id)
                      (map add-resource-id)
                      #_
                      (map normalize-relationship)))
       #_(dx/element :definitions {})))

(defn xml-files->families
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

(defn emit-family-xml!
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
  (spit-xml f (xml-files->families (classpath-resource-xmls!)) opts))

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
  (spit "resource-data.json" json-defs)
  (zip/make-node family-loc (dx/element :root) (map zip/node (dzx/xml-> family-loc :whatever)))
  ;; Some test data
  (def prop (first (dzx/xml-> family-loc :entity :property)))
  (def relationship #clojure.data.xml.node.Element{:tag :relationship, :attrs {}, :content (#clojure.data.xml.node.Element{:tag :name, :attrs {}, :content ("default-wishlist-from-root")} #clojure.data.xml.node.Element{:tag :description, :attrs {}, :content ("Link from root resource to default wishlist.")} #clojure.data.xml.node.Element{:tag :rel, :attrs {}, :content ("defaultwishlist")} #clojure.data.xml.node.Element{:tag :from, :attrs {}, :content ("base.root")} #clojure.data.xml.node.Element{:tag :to, :attrs {}, :content ("default-wishlist")})})
  (def entity #clojure.data.xml.node.Element{:tag :entity, :attrs {}, :content (#clojure.data.xml.node.Element{:tag :name, :attrs {}, :content ("line-item")} #clojure.data.xml.node.Element{:tag :description, :attrs {}, :content ("A line item in a cart.")} #clojure.data.xml.node.Element{:tag :property, :attrs {}, :content (#clojure.data.xml.node.Element{:tag :name, :attrs {}, :content ("quantity")} #clojure.data.xml.node.Element{:tag :description, :attrs {}, :content ("The total number of items in the line item.")} #clojure.data.xml.node.Element{:tag :integer, :attrs {}, :content ()})} #clojure.data.xml.node.Element{:tag :property, :attrs {}, :content (#clojure.data.xml.node.Element{:tag :name, :attrs {}, :content ("line-item-id")} #clojure.data.xml.node.Element{:tag :description, :attrs {}, :content ("The internal line item identifier.")} #clojure.data.xml.node.Element{:tag :internal, :attrs {}, :content ()} #clojure.data.xml.node.Element{:tag :string, :attrs {}, :content ()})} #clojure.data.xml.node.Element{:tag :property, :attrs {}, :content (#clojure.data.xml.node.Element{:tag :name, :attrs {}, :content ("item-id")} #clojure.data.xml.node.Element{:tag :description, :attrs {}, :content ("The internal item identifier.")} #clojure.data.xml.node.Element{:tag :internal, :attrs {}, :content ()} #clojure.data.xml.node.Element{:tag :string, :attrs {}, :content ()})} #clojure.data.xml.node.Element{:tag :property, :attrs {}, :content (#clojure.data.xml.node.Element{:tag :name, :attrs {}, :content ("cart-id")} #clojure.data.xml.node.Element{:tag :description, :attrs {}, :content ("The internal cart identifier.")} #clojure.data.xml.node.Element{:tag :internal, :attrs {}, :content ()} #clojure.data.xml.node.Element{:tag :string, :attrs {}, :content ()})} #clojure.data.xml.node.Element{:tag :property, :attrs {}, :content (#clojure.data.xml.node.Element{:tag :name, :attrs {}, :content ("configuration")} #clojure.data.xml.node.Element{:tag :description, :attrs {}, :content ("The details of the line item configuration.")} #clojure.data.xml.node.Element{:tag :is-a, :attrs {}, :content ("line-item-configuration")})})})
  )
