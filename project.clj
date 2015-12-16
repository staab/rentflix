(defproject rentflix "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "https://github.com/staab/rentflix"
  :license {:name "MIT"
            :url "https://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [ring/ring-core "1.4.0"]
                 [ring/ring-jetty-adapter "1.4.0"]]
  :main ^:skip-aot rentflix.core
  :target-path "target/%s"
  :plugins [[lein-ring "0.9.7"]]
  :profiles {:uberjar {:aot :all}}
  :ring {:handler rentflix.core/handler})