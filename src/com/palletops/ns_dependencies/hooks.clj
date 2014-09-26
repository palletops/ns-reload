(ns com.palletops.ns-dependencies.hooks
  "Hooks for augmented namespace tracking"
  (:require
   [com.palletops.ns-dependencies :refer [depends-on? with-requires]]
   [robert.hooke :refer [add-hook]]))

(defn- -defmacro
  [f &form &env & args]
  (let [[nameargs & arities] (partition-by sequential? args)
        arities (apply concat arities)]
    (if (vector? (first arities))
      (apply f &form &env
             (concat nameargs [(first arities)
                               `(with-requires (do ~@(rest arities)))]))
      (apply f &form &env
             (concat
              nameargs
              (map
               (fn [[argv & r]]
                 (list argv `(with-requires (do ~@r))))
               arities))))))

(defn- -load-lib
  "Augment load-lib to add aliases for all required namespaces that
  have no other discoverable dependency information."
  [f prefix lib & options]
  (let [v (apply f prefix lib options)
        lib (if prefix (symbol (str prefix \. lib)) lib)]
    (when-not (depends-on? (ns-name *ns*) lib)
      (alias (symbol (str (name lib) (hash (name lib)))) lib))
    v))

(defn hooks
  []
  (add-hook #'clojure.core/load-lib -load-lib)
  (add-hook #'clojure.core/defmacro -defmacro))

(comment
  (hooks)
  (robert.hooke/clear-hooks #'clojure.core/load-lib)
  (robert.hooke/clear-hooks #'clojure.core/defmacro))
