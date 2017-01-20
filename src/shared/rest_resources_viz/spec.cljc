(ns rest-resources-viz.spec
  (:require [clojure.spec :as s]
            [clojure.spec.test :as stest]
            [clojure.test.check.generators :as gen]))

(defn instrument-all []
  (run! stest/instrument (stest/instrumentable-syms)))

(def qual-or-unqual-keyword? #(or (keyword? %) (qualified-keyword? %)))

(s/def :family/name string?)
(s/def :family/description string?)
(s/def :family/id keyword?)

(s/def :family/entity (s/keys :req-un [:family/name]
                              :opt-un [:family/description]))

(s/def :uri-part/name string?)
(s/def :uri-part/description string?)

(s/def :resource/id qualified-keyword?)
(s/def :resource/name string?)
(s/def :resource/description string?)
(s/def :resource/uri string?)
(s/def :resource/alias string?)
(s/def :resource/family-id :family/id)

(s/def :resource/entity (s/keys :req-un [:resource/family-id :resource/id :resource/name :resource/uri]
                                :opt-un [:resource/alias :resource/description]))

(s/def :relationship/name string?)
(s/def :relationship/description string?)
(s/def :relationship/rel string?)
(s/def :relationship/rev string?)
(s/def :relationship/from qualified-keyword?)
(s/def :relationship/to qualified-keyword?)
(s/def :relationship/family-id :family/id)

(s/def :relationship/entity (s/keys :req-un [:relationship/name :relationship/rel
                                             :relationship/from :relationship/to
                                             :relationship/family-id]
                                    :opt-un [:relationship/description :relationship/rev]))

(s/def :property/name string?)
(s/def :property/description string?)
(s/def :property/type #{:internal :string})

(s/def :graph-data/family (s/coll-of :family/entity :kind vector?))
(s/def :graph-data/resource (s/coll-of :resource/entity :kind vector?))
(s/def :graph-data/relationship (s/coll-of :relationship/entity :kind vector?))

(s/def :graph-data/list-of-relationship (s/coll-of :relationship/entity :kind vector?))
(s/def :graph-data/pagination-relationship (s/coll-of :relationship/entity :kind vector?))

(s/def :graph-data/entity (s/keys :req-un [:graph-data/family]
                                  :opt-un [:graph-data/resource
                                           :graph-data/relationship
                                           :graph-data/list-of-relationship
                                           :graph-data/pagination-relationship]))
