(ns com.palletops.ns-reload
  "Calculate a dependency graph for your loaded namespaces.  Can
  reload your dependencies."
  (:require [clojure.set :as set]
            [clojure.string :as string]
            [clojure.walk :as walk]))

;;; Namespace Dependencies
(defn package-dependencies
  "Returns the packages on which the namespace depends."
  [ns]
  (->>
   (ns-imports ns)
   vals
   (map
    ;; can throw if mixed pre/post 1.2.1 types are on the classpath due to
    ;; changes in defrecord and deftype mangling
    #(try (symbol (.getName (.getPackage ^Class %))) (catch Exception _)))
   distinct
   (filter identity)
   (filter (set (map ns-name (all-ns))))))

(defn refered-dependencies
  "Calculate the namespaces that are refered for a given namespace."
  [ns]
  (distinct (map (comp ns-name :ns meta val) (ns-refers ns))))

(defn aliased-dependencies
  "Calculate the namespaces that are aliased for a given namespace."
  [ns]
  (->> (ns-aliases ns) vals (map ns-name) distinct))

(defn namespace-dependencies
  "Calculate the namespaces that the given namespace directly depends on."
  [ns]
  (distinct
   (concat
    (refered-dependencies ns)
    (aliased-dependencies ns))))

(defn depends-on? [ns-sym dependency-ns-sym]
  {:pre [(symbol? dependency-ns-sym)]}
  (some #(= dependency-ns-sym %) (namespace-dependencies ns-sym)))

(defn direct-dependencies
  "Calculate namespace dependencies based on namespace information.
This reduces a map, from namespace symbol, to a set of symbols for the direct
namespace dependencies of that namespace."
  []
  (-> (reduce
       (fn [dependencies ns]
         (assoc dependencies
           (ns-name ns)
           (disj (set (namespace-dependencies ns)) (ns-name ns))))
       {}
       (all-ns))
      ;; Patch for circular dependency in core
      (update-in ['clojure.core] disj 'clojure.java.io)))

(def ^:private ^:dynamic *circular* nil)

(defn dependency-graph*
  "Given a map of direct dependencies, construct a dependency graph for ns-sym."
  [direct-dependencies ns-sym seen]
  (if-not (some #(= ns-sym %) seen)
    (let [seen (conj seen ns-sym)
          children (direct-dependencies ns-sym)
          r (fn [ns-sym] (dependency-graph* direct-dependencies ns-sym seen))]
      (zipmap children (map r children)))
    (let [p (->> seen
                 (split-with #(not= ns-sym %))
                 last
                 vec)]
      (set! *circular* (conj *circular*
                             (conj p ns-sym)))
      nil)))

(defn dependency-graph
  "Given a map of direct dependencies, construct a dependency graph for ns-sym."
  [direct-dependencies ns-sym]
  (binding [*circular* #{}]
    (let [r (dependency-graph* direct-dependencies ns-sym [])]
      (when (seq *circular*)
        (println "WARN: circular dependencies:")
        (doseq [c *circular*]
          (println c)))
      r)))

(defn direct-dependents
  "Build a map of direct dependents (the inverse of direct-dependencies)."
  [direct-dependencies]
  (reduce-kv
   (fn [m k v]
     (reduce
      (fn [m d]
        (update-in m [d] (fnil conj #{}) k))
      m
      v))
   {}
   direct-dependencies))

(defn ordered-dependencies
  "Return a sequence of dependencies from a dependency graph, in a
  deepest first order. Will contain repeated namespaces."
  [graph]
  (mapcat
   (fn [[k v]]
     (conj (vec (ordered-dependencies v)) k))
   graph))

(defn required-namespaces
  "Return a sequence of namespaces that the `ns-sym` namespace depends on.
  The sequence will be in dependency order (deepest first)."
  [ns-sym]
  (->> ns-sym
       (dependency-graph (direct-dependencies))
       ordered-dependencies
       distinct))

(defn dependent-namespaces
  "Return a sequence of namespaces that depend on the `ns-sym` namespace'.
  The sequence will be in dependency order (nearest dependency first)."
  [ns-sym]
  (->> ns-sym
       (dependency-graph (direct-dependents (direct-dependencies)))
       ordered-dependencies
       reverse
       distinct))


;;; Namespace unloading and reloading

(defn unload-ns
  "Unload the namespace, ns-sym."
  [ns-sym]
  (remove-ns ns-sym)
  (dosync (commute @#'clojure.core/*loaded-libs* disj ns-sym))
  nil)

;; core.async injects these through macros
;; into user code (via the analyzer)
;; (.startsWith (name %) "clojure.core.cache")
;; (.startsWith (name %) "clojure.core.memoize")

(defn reload-ns
  "Reload all the given namespaces, filtering those that filter-ns?
  returns true for."
  [ns-sym {:keys [unload verbose]}]
  (when verbose
    (println "Reloading" ns-sym))
  (when unload
    (unload-ns ns-sym))
  (require ns-sym :reload))

(defn reload-namespaces
  "Reload all the given namespaces, filtering those that filter-ns?
  returns true for."
  [namespaces {:keys [filter-ns? unload verbose]
               :or {filter-ns? #(not (or (.startsWith (name %) "clojure.tools")
                                         (= 'user %)))}
               :as options}]
  (doseq [n namespaces
          :when (filter-ns? n)]
    (reload-ns n (select-keys options [:unload :verbose]))))

(defn reload-all
  "Reload all required and dependent namespaces, filtering those that
  filter-ns? returns true for."
  ([ns-sym {:keys [filter-ns? unload verbose]}]
     (reload-namespaces (concat (required-namespaces ns-sym)
                                [ns-sym]
                                (dependent-namespaces ns-sym))))
  ([ns-sym]
     (reload-all ns-sym {})))

(defn reload-dependents
  "Reload all dependent namespaces, filtering those that filter-ns?
  returns true for."
  ([ns-sym {:keys [filter-ns? unload verbose] :as options}]
     (reload-namespaces (dependent-namespaces ns-sym) options))
  ([ns-sym]
     (reload-dependents ns-sym {})))

(defn reload
  "Reload a namespace and all its dependent namespaces, filtering
  those that filter-ns?  returns true for."
  ([ns-sym {:keys [filter-ns? unload verbose] :as options}]
     (reload (concat [ns-sym] (dependent-namespaces ns-sym) options)))
  ([ns-sym]
     (reload ns-sym {})))


;;; Injection of requires by macros
(def ^:private ^:dynamic *ns-names*)

(defn- possibly-require
  [x]
  (when (symbol? x)
    (let [n (namespace x)]
      (when (and n
                 (not= (ns-name *ns*) n)
                 (.contains n ".")
                 (not (depends-on? (ns-name *ns*) (symbol n))))
        (set! *ns-names* (conj *ns-names* n)))))
  x)

(defn with-requires
  "A function for use in macros that adds requires to *ns* for the
  namespace of any namespace qualified symbol in body.  Returns body.

  This ensures that there is a discoverable dependency on the
  namespaces of code injected by body."
  [body]
  (binding [*ns-names* []]
    (walk/postwalk possibly-require body)
    (doseq [x (distinct *ns-names*)
            :when (not                  ; this is rather slow
                   (try (Class/forName x)
                        (catch Exception e)))]
      (require (symbol x))))
  body)
