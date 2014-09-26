(ns com.palletops.ns-dependencies-test
  (:require [com.palletops.ns-dependencies :refer :all]
            [com.palletops.ns-dependencies.hooks :as hooks]
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
  (println (required-namespaces 'com.palletops.ns-dependencies-test))
  (is ((set (required-namespaces 'com.palletops.ns-dependencies-test))
       'clojure.string)))
