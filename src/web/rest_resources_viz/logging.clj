(ns ^{:doc "Logging macros."
      :author "Andrea Richiardi"}
    rest-resources-viz.logging)

(defmacro log [& args]
  `(.log js/console ~@args))

(defmacro info [& args]
  `(.info js/console ~@args))
