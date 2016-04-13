(defproject rentflix "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "https://github.com/staab/rentflix"
  :license {:name "MIT"
            :url "https://opensource.org/licenses/MIT"}
  :git-dependencies [["https://github.com/runexec/Moov.git"]]
  :repositories {"my.datomic.com" {:url "https://my.datomic.com/repo"
                                   :creds :gpg}}
  :dependencies [[argo "0.1.1"]
                 [clj-http "2.0.1"]
                 [environ "1.0.2"]
                 [io.rkn/conformity "0.4.0"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/clojure "1.7.0"]
                 [com.datomic/datomic-free "0.9.5344" :exclusions [joda-time]]
                 [ring/ring-core "1.4.0"]
                 [ring/ring-jetty-adapter "1.4.0"]
                 [compojure "1.1.8"]
                 [environ "0.5.0"]]
  :min-lein-version "2.0.0"
  :target-path "target/%s"
  :plugins [[environ/environ.lein "0.2.1"]
            [lein-ring "0.9.7"]
            [lein-git-deps "0.0.1-SNAPSHOT"]]
  :hooks [environ.leiningen.hooks]
  :uberjar-name "rentflix-standalone.jar"
  :profiles {:uberjar {:aot :all}
             :dev {:env {:datomic-uri "datomic:free://localhost:4334/rentflix-dev"}}
             :test {:env {:datomic-uri "datomic:mem://rentflix-test"}}}
  :source-paths ["src" ".lein-git-deps/Moov/src"]
  :ring {:handler rentflix.server/api-handler :auto-refresh? true})