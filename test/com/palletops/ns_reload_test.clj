(ns com.palletops.ns-reload-test
  (:require [com.palletops.ns-reload :refer :all]
            [com.palletops.ns-reload.hooks :as hooks]
            [clojure.test :refer :all]
            [robert.hooke :refer [add-hook]]))

;; add hook so require of clojure.string gets recorded
(add-hook #'clojure.core/load-lib @#'hooks/-load-lib)

(defmacro x [body]
  (with-requires
    `(clojure.string/upper-case ~body)))

(def y (x "a"))

(deftest with-requires-test
  (is (= y "A"))
  (println (required-namespaces 'com.palletops.ns-reload-test))
  (is ((set (required-namespaces 'com.palletops.ns-reload-test))
       'clojure.string)))
