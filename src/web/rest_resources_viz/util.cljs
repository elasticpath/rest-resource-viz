(ns ^{:doc "A classic, utility namespace."
      :author "Andrea Richiardi"}
    rest-resources-viz.util
  (:import [goog.net XhrIo]))

(defn fetch-file!
  "Very simple implementation of XMLHttpRequests that given a file path
  calls src-cb with the string fetched of nil in case of error.
  See doc at https://developers.google.com/closure/library/docs/xhrio"
  [file-url src-cb]
  (try
    (.send XhrIo file-url
           (fn [e]
             (if (.isSuccess (.-target e))
               (src-cb (.. e -target getResponseText))
               (src-cb nil))))
    (catch :default e
      (src-cb nil))))
