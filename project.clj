(defproject audio-converter "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :repositories {"jaudiotagger-repository"
                 "https://dl.bintray.com/ijabz/maven"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [me.raynes/fs "1.4.6"]
                 [net.jthink/jaudiotagger "2.2.6-SNAPSHOT"]]
  :main ^:skip-aot audio-converter.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
