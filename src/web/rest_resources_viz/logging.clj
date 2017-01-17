(ns ^{:doc "Logging macros."
      :author "Andrea Richiardi"}
    rest-resources-viz.logging)

(defmacro debug [& args]
  `(.log js/console ~@args))

(defmacro info [& args]
  `(.info js/console ~@args))

(defmacro warn [& args]
  `(.warn js/console ~@args))

(defmacro error [& args]
  `(.error js/console ~@args))
