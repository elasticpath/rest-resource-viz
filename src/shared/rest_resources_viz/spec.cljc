(ns rest-resources-viz.spec
  (:require [clojure.spec :as s]
            [clojure.test.check.generators :as gen]))

;; (def generate gen/)
(s/def :uri-part/name string?)
(s/def :uri-part/description string?)

(s/def :resource/name string?)
(s/def :resource/description string?)
(s/def :resource/uri string?)
(s/def :resource/alias string?)
(s/def :resource/entity string?)

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
(s/def :family/uri-part (s/keys :req-un [:uri-part/name :uri-part/description]))

(s/def ::family (s/keys :req-un [:family/name :family/description :family/uri-part]))
