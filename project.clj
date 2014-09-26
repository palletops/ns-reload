(defproject com.palletops/ns-reload "0.1.0"
  :description "A library for namespace dependencies."
  :url "https://github.com/palletops/ns-reload"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[robert/hooke "1.3.0"]]
  :profiles {:provided {:dependencies [[org.clojure/clojure "1.6.0"]
                                       [leiningen-core "2.5.0"]]}})
