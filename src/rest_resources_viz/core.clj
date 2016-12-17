(ns rest-resources-viz.core
  (:require [clojure.data.xml :as xml]
            [clojure.zip :as zip]
            [clojure.data.zip :as data-zip]
            [clojure.java.io :as io]))

(defn parse-resource-xml!
  "Return the resource data starting from the root (family)."
  [java-resource-path]
  (-> java-resource-path
      io/resource
      slurp
      xml/parse-str
      zip/xml-zip))

(comment
  (def xml-file "META-INF/rest-definitions/wishlists.xml")
  )
