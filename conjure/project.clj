(defproject conjure "0.8.0-SNAPSHOT"
  :description "Self extracting jar file for Conjure. Conjure is a full stack web framework written entirely in Clojure."
  :dependencies [[org.clojure/clojure "1.1.0"]
                 [org.clojure/clojure-contrib "1.1.0"]
                 [conjure-script "0.8.0-SNAPSHOT"]]
  :dev-dependencies [[lein-clojars "0.5.0"]]
  :main conjure.extract)