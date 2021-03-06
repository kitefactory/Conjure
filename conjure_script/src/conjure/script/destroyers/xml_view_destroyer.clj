(ns conjure.script.destroyers.xml-view-destroyer
  (:require [clojure.contrib.logging :as logging]
            [conjure.core.view.util :as util]
            [conjure.script.destroyers.view-test-destroyer :as view-test-destroyer]))

(defn
#^{:doc "Prints out how to use the destroy xml view command."}
  view-usage []
  (println "You must supply a controller and action name (Like hello-world).")
  (println "Usage: ./run.sh script/destroy.clj xml-view <controller> <action>"))

(defn
#^{:doc "Destroys the xml view file from the given controller and action."}
  destroy-view-file [controller action]
  (if (and controller action)
    (let [view-directory (util/find-views-directory)]
      (if view-directory
        (let [controller-directory (util/find-controller-directory view-directory controller)]
          (if controller-directory
            (let [view-file (util/find-view-file controller-directory action)]
              (if view-file
                (let [is-deleted (. view-file delete)] 
                  (logging/info (str "File " (. view-file getName) (if is-deleted " destroyed." " not destroyed."))))
                (logging/info "View file not found. Doing nothing.")))
            (logging/info (str "The directory for controller " controller " was not found. Doing nothing."))))
        (do
          (logging/error (str "Could not find views directory: " view-directory))
          (logging/error "Command ignored."))))
    (view-usage)))

(defn
#^{:doc "Destroys an xml view file for the view name given in params."}
  destroy [params]
  (destroy-view-file (first params) (second params)))

(defn
#^{:doc "Destroys all of the files created by the xml_view_generator."}
  destroy-all-dependencies
  ([controller action]
    (destroy-view-file controller action)
    (view-test-destroyer/destroy-all-dependencies controller action)))