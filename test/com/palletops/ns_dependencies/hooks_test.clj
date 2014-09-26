(ns com.palletops.ns-dependencies.hooks-test
  (:require [com.palletops.ns-dependencies.hooks :refer :all :as hooks]
            [clojure.test :refer :all :as test]))

(comment
  (robert.hooke/clear-hooks #'clojure.core/load-lib)
  (robert.hooke/clear-hooks #'clojure.core/defmacro))

(hooks)

(defonce aliases
  (set (vals (ns-aliases 'com.palletops.ns-dependencies.hooks-test))))

(defmacro m [] :m)
(defmacro p [] `(clojure.set/difference #{} #{}))
(defmacro q
  ([] `(clojure.walk/postwalk #(do %) nil))
  ([x] `(clojure.walk/postwalk #(do %) nil)))

(defonce aliases-after-defmacro
  (set (vals (ns-aliases 'com.palletops.ns-dependencies.hooks-test))))

(deftest defmacro-hook-declare-test
  (is (= aliases aliases-after-defmacro)))

(m)
(defonce aliases-after-m
  (set (vals (ns-aliases 'com.palletops.ns-dependencies.hooks-test))))

(p)
(defonce aliases-after-p
  (set (vals (ns-aliases 'com.palletops.ns-dependencies.hooks-test))))

(q)
(defonce aliases-after-q
  (set (vals (ns-aliases 'com.palletops.ns-dependencies.hooks-test))))

(deftest defmacro-hook-macro-call-test
  (is (= aliases aliases-after-m))
  (is (= (conj aliases (the-ns 'clojure.set)) aliases-after-p))
  (is (= (conj aliases-after-p (the-ns 'clojure.walk)) aliases-after-q)))
