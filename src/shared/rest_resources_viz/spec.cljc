(ns rest-resources-viz.spec
  (:require [clojure.spec :as s]
            [clojure.spec.test :as stest]
            [clojure.test.check.generators :as gen]))

(defn instrument-all []
  (run! stest/instrument (stest/instrumentable-syms)))

(def qual-or-unqual-keyword? #(or (keyword? %) (qualified-keyword? %)))

(s/def :family/family-id keyword?)

(s/def :uri-part/name string?)
(s/def :uri-part/description string?)

(s/def :resource/id qual-or-unqual-keyword?)
(s/def :resource/name string?)
(s/def :resource/description string?)
(s/def :resource/uri string?)
(s/def :resource/alias string?)
(s/def :resource/family-id :family/family-id)

(s/def :entity/resource (s/keys :req-un [:resource/name :resource/uri]
                                :opt-un [:resource/alias :resource/description]))
(s/def :with-family-id/resource (s/keys :req-un [:resource/name :resource/uri :resource/family-id]
                                        :opt-un [:resource/alias :resource/description]))

(s/def :relationship/name string?)
(s/def :relationship/description string?)
(s/def :relationship/rel string?)
(s/def :relationship/rev string?)
(s/def :relationship/from string?)
(s/def :relationship/to string?)
(s/def :relationship/family-id :family/family-id)
(s/def :pre-norm-relationship/from qual-or-unqual-keyword?)
(s/def :pre-norm-relationship/to qual-or-unqual-keyword?)

(s/def :entity/relationship (s/keys :req-un [:relationship/name :relationship/rel
                                             :relationship/from :relationship/to]
                                    :opt-un [:relationship/description :relationship/rev]))
(s/def :pre-norm/relationship (s/keys :req-un [:relationship/name :relationship/rel
                                               :pre-norm-relationship/from :pre-norm-relationship/to
                                               :relationship/family-id]
                                      :opt-un [:relationship/description :relationship/rev]))

(s/def :entity/name string?)
(s/def :entity/description string?)

(s/def :property/name string?)
(s/def :property/description string?)
(s/def :property/type #{:internal :string})

(s/def :family/name string?)
(s/def :family/description string?)
(s/def :family/id keyword?)

(s/def :coll/resource (s/coll-of :entity/resource :kind vector?))
(s/def :coll-with-family-id/resource (s/coll-of :with-family-id/resource :kind vector?))
(s/def :coll/relationship (s/coll-of :entity/relationship :kind vector?))
(s/def :coll-pre-norm/relationship (s/coll-of :pre-norm/relationship :kind vector?))

(s/def :entity/family (s/keys :req-un [:family/name :coll/resource]
                              :opt-un [:family/description])) ;; :coll/relationship is not overwritten

(s/def :entity/definitions (s/keys :req-un [:entity/family]))

(s/def :with-id/family (s/merge :entity/family
                                (s/keys :req-un [:family/id]
                                        :opt-up [:coll/relationship])))
(s/def :with-id/definitions (s/keys :req-un [:entity-with-id/family]))

(s/def :with-family-id/family (s/merge :with-id/family
                                       (s/keys :req-un [:coll-with-family-id/resource])))
(s/def :with-family-id/definitions (s/keys :req-un [:with-family-id/family]))

(s/def :pre-norm/family (s/merge :entity/family
                                 (s/keys :req-un [:family/id]
                                         :opt-up [:coll-pre-norm/relationship])))

(s/def :pre-norm/definitions (s/keys :req-un [:pre-norm/family]))
(s/def :coll-pre-norm/definitions (s/coll-of :pre-norm/definitions :kind vector?))

(s/def :graph-data/family (s/coll-of (s/keys :req-un [:family/name :family/id])))

(s/def :graph-data/resource (s/coll-of (s/merge :with-family-id/resource
                                                (s/keys :req-un [:resource/id]))
                                       :kind vector?))
(s/def :graph-data/relationship (s/coll-of :pre-norm/relationship :kind vector?))
(s/def :graph-data/entity (s/keys :req-un [:graph-data/relationship
                                           :graph-data/resource
                                           :graph-data/family]))
