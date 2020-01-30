(ns rest-resources-viz.test-util
  (:require [clojure.spec.alpha :as s]
            [pjstadig.humane-test-output]
            [expound.alpha :as expound]))

(pjstadig.humane-test-output/activate!)

;; See https://github.com/bhb/expound/issues/78
(defn safe-printer [explain-data]
  (let [exp (expound/custom-printer {:show-valid-values? true :theme :figwheel-theme})]
    (try
      (exp explain-data)
      (catch Exception e
        (s/explain-printer explain-data)))))

(alter-var-root #'s/*explain-out* (fn [_] safe-printer))

(s/check-asserts true)
