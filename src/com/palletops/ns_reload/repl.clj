(ns com.palletops.ns-reload.repl
  "Wrappers for use at the repl, with persistent configuration, so it
  can be set in profiles."
  (:require
   [com.palletops.ns-reload :as ns-reload]))

(defonce ^:private -config (atom {}))

(defn pred-fn
  [k v]
  (case k
    :constantly (fn [f] (fn [_] v))
    :excludes (fn [f] (fn [n] (if ((set v) n) nil (f n))))
    :includes (fn [f] (fn [n] (if ((set v) n) true (f n))))
    :exclude-regex (fn [f] (fn [n] (if (re-find v (name n)) nil (f n))))
    :include-regex (fn [f] (fn [n] (if (re-find v (name n)) true (f n))))))

(defn filter-pred
  "Combine clauses in a filter predicate."
  [clauses default]
  (reduce
   (fn [f [k v]]
     ((pred-fn k v) f))
   (fn [_] default)
   clauses))

(defn set-config!
  "Set the configuration for using ns-reload from the repl."
  ([{:keys [options ns-filters reload-filters pre-reload-hook post-reload-hook]
     :as config}]
     (reset! -config
             (merge
              (select-keys config [:pre-reload-hook :post-reload-hook])
              {:options (assoc options
                          :filter-ns? (filter-pred
                                       (conj ns-filters [:excludes ['user]])
                                       true))
               :should-reload-dependents? (filter-pred reload-filters nil)}))))

(defn reload
  "Reload a namespace and it's dependents.  `options' are merged with any
  configuration that has been set with `set-config!'."
  ([ns-sym {:keys [unload verbose filter-ns?] :as options}]
     (ns-reload/reload ns-sym (merge @-config options)))
  ([ns-sym]
     (reload ns-sym {})))

(defn reload-dependents
  "Reload the dependents of a namespace. `options' are merged with any
  configuration that has been set with `set-config!'."
  ([ns-sym {:keys [unload verbose filter-ns?] :as options}]
     (ns-reload/reload-dependents
      ns-sym (merge (:options @-config) options)))
  ([ns-sym]
     (reload-dependents ns-sym {})))

(defn ns-reload-hook
  "Function that can be used to add a hook on namespace reload and
  trigger a reload of dependents."
  [ns-sym options]
  (let [{:keys [should-reload-dependents? pre-reload-hook post-reload-hook]}
        @-config]
    (when (and should-reload-dependents? (should-reload-dependents? ns-sym))
      (when-let [f (and pre-reload-hook (resolve pre-reload-hook))]
        (f))
      (ns-reload/reload-dependents
       ns-sym (merge (:options @-config) options))
      (when-let [f (and post-reload-hook (resolve post-reload-hook))]
        (f)))))
