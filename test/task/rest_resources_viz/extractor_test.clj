(ns rest-resources-viz.extractor-test
  (:require [clojure.spec :as s]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.test :as test :refer [deftest testing is are use-fixtures]]
            [rest-resources-viz.extractor :as ext]))

(def test-state (atom nil))

(defn wrap-setup
  [f]
  (try
    (swap! test-state assoc :graph-data (-> "graph-data.edn"
                                            io/resource
                                            slurp
                                            edn/read-string))
    (f)
    (reset! test-state nil)
    (catch Exception e
      (println "Exception" e))))

(use-fixtures :once wrap-setup)

(deftest keywordize
  (is (nil? (ext/keywordize "" "")) "When input is empty, return nil")
  (is (nil? (ext/keywordize "" ".")) "When input is just a full stop (.), return nil")
  (is (= :family/line-item (ext/keywordize "family" "line-item")) "Convert single string with no . to qualified keyword with family")
  (is (= :family/line-item (ext/keywordize "family" ".line-item")) "Convert single string with no one preceding . to qualified keyword with family")
  (is (= :carts/line-item (ext/keywordize "family" "carts.line-item")) "Convert string with something preceding and following full stop (.) to qualified keyword ignoring family"))

(deftest graph-data
  (testing "Spec conformance test"
    (let [graph-data (ext/xml-files->graph-data (ext/classpath-resource-xmls!))]
      (is (s/valid? :graph-data/entity graph-data) (s/explain-str :graph-data/entity graph-data)))))
