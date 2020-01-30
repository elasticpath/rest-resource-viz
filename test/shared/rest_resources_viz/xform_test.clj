(ns rest-resources-viz.xform-test
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.spec.test.alpha :as stest]
            [clojure.test :as test :refer [deftest testing is are use-fixtures]]
            [rest-resources-viz.test-util :as tutil]
            [rest-resources-viz.xform :as xform]))

(run! stest/instrument (stest/instrumentable-syms))

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

(deftest unfold-relationships
  ;; [rels (get-in @test-state [:graph-data :relationship])]

  (testing "Sanity check"
    (let [rel-set (xform/unfold-relationships [{:name        "slots-from-root"
                                                :description "A description"
                                                :rel         "slots"
                                                :from        :base/root
                                                :to          :slots
                                                :family-id   :slots}])]
      (is (= 1 (count rel-set)) "No reverse relationship and no duplicates should result in one item in rels")
      (are [x y] (= x (y (first rel-set)))
        :base/root        :source
        :slots            :target
        "slots"           :label
        "slots-from-root" :name
        "A description"   :description
        :slots            :family-id))

    (let [rel-set (xform/unfold-relationships [{:name        "assets-for-item"
                                                :description "Retrieves an item's asset."
                                                :rel         "assets"
                                                :rev         "definition"
                                                :from        :itemdefinitions/item-definition
                                                :to          :assets
                                                :family-id   :assets}])]
      (is (= 2 (count rel-set)) "With a reverse relationship and no duplicates should result in two items in rels")
      (are [x y] (= x (y (first (filter #(= "assets" (:label %)) rel-set))))
        "assets-for-item"                :name
        "Retrieves an item's asset."     :description
        "assets"                         :label
        :itemdefinitions/item-definition :source
        :assets                          :target
        :assets                          :family-id)
      (are [x y] (= x (y (first (filter #(= "definition" (:label %)) rel-set))))
        "assets-for-item"                :name
        "Retrieves an item's asset."     :description
        "definition"                     :label
        :assets                          :source
        :itemdefinitions/item-definition :target
        :assets                          :family-id))))
