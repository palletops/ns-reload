(ns ns-reload.plugin
  "Plugin for using ns-reload"
  (:require
   [clojure.java.io :as io]
   [leiningen.core.main :as main]
   [leiningen.core.project :as project]
   [robert.hooke :refer [add-hook]]))

;; Keep in sync with VERSION-FORM in project.clj
(def ^:private properties-file
  "META-INF/maven/com.palletops/ns-reload/pom.properties")

(defn- version
  []
  (let [v (-> (doto (java.util.Properties.)
                (.load (io/reader (io/resource properties-file))))
              (.getProperty "version"))]
    (assert v (str "Unexpected error, plugin version is not availaible"))
    v))

(defn profiles
  [project]
  {:plugin.ns-reload/injections
   {:dependencies [['com.palletops/ns-reload (version)]]
    :injections [`(require 'com.palletops.ns-reload.hooks
                           'com.palletops.ns-reload.repl)
                 `(com.palletops.ns-reload.hooks/hooks)
                 `(com.palletops.ns-reload.repl/set-config!
                   ~(:ns-reload project))]}})

(defn middleware
  "Middleware to add a profile defining :injections."
  [project]
  (project/add-profiles project (profiles project)))

;; We would like to be able to hook a function after the :repl profile
;; is merged, but that is not very feasible at the moment.

;; We also want to avoid require'ing leiningen.repl just to hook it,as
;; it can add considerably to lein startup time when not using the repl.

(defn repl-hook
  [task & [project & args]]
  (apply task
         (project/merge-profiles project [:plugin.ns-reload/injections])
         args))

(defn add-repl-hook  []
  (when-let [repl (resolve 'leiningen.repl/repl)]
    (add-hook repl repl-hook)))

(defn resolve-task-hook
  [f & args]
  (let [r (apply f args)]
    (add-repl-hook)
    r))

(defn hooks
  []
  (add-hook #'main/resolve-task resolve-task-hook))
