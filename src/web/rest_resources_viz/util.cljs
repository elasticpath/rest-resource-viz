(ns ^{:doc "A classic, utility namespace."
      :author "Andrea Richiardi"}
    rest-resources-viz.util
  (:require-macros [rest-resources-viz.logging :as log])
  (:import [goog object]
           [goog.net XhrIo]))

(defonce debug? ^boolean js/goog.DEBUG)

(defn keyword->str [k]
  (str (if-let [n (namespace k)] (str n "/")) (name k)))

(extend-type Keyword
  IEncodeJS
  (-clj->js [x] (keyword->str x))
  (-key->js [x] (keyword->str x)))

(comment
  ;; "soft tests"
  (= "test" (clj->js :test))
  (= "test/test" (clj->js :test/test)))

(defn fetch-file!
  "Very simple implementation of XMLHttpRequests that given a file path
  calls src-cb with the string fetched of nil in case of error.
  See doc at https://developers.google.com/closure/library/docs/xhrio"
  [file-url src-cb]
  (let [log-error #(log/error "Could not fetch" file-url ":" %)]
    (try
      (.send XhrIo file-url
             (fn [e]
               (log/debug "XhrIo returned" e)
               (if (.isSuccess (.-target e))
                 (src-cb (.. e -target getResponseText))
                 (do (log-error (-> (.-target e) (object.get "lastError_")))
                     (src-cb nil)))))
      (catch :default e
        (log-error (.-message e))
        (src-cb nil)))))

(defn translate-str
  "Return the translate string needed for the transform attribute"
  [x y]
  (str "translate(" x "," y ")"))

(defn toggle-or-nil
  "Simple toggle-like schema, return new iff old is nil or not the same
  as new. Otherwise return nil."
  [new old]
  (cond
    (nil? old) new
    (= old new) nil
    :else new))
