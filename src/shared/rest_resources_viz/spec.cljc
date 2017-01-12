(ns rest-resources-viz.spec
  (:require [clojure.spec :as s]
            [clojure.test.check.generators :as gen]))

(s/def :uri-part/name string?)
(s/def :uri-part/description string?)

(s/def :resource/name string?)
(s/def :resource/description string?)
(s/def :resource/uri string?)
(s/def :resource/alias string?)

(s/def :resource/entity (s/keys :req-un [:resource/name :resource/uri]
                                :opt-un [:resource/alias :resource/description]))

(s/def :relationship/name string?)
(s/def :relationship/description string?)
(s/def :relationship/rel string?)
(s/def :relationship/from string?)
(s/def :relationship/to string?)

(s/def :entity/name string?)
(s/def :entity/description string?)

(s/def :property/name string?)
(s/def :property/description string?)
(s/def :property/type #{:internal :string})

(s/def :family/name string?)
(s/def :family/description string?)

(s/def :coll/resource (s/coll-of :resource/entity :kind vector?))

(s/def :family/entity (s/keys :req-un [:family/description:family/name :coll/resource]
                              :opt-un []))
