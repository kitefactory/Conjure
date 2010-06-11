(ns bindings.templates.show
  (:use conjure.binding.base)
  (:require [conjure.server.request :as request]
            [helpers.template-helper :as template-helper]
            [views.templates.show :as show]))

(def-binding [model-name]
  (show/render-view
    model-name
    (template-helper/table-metadata model-name)
    (template-helper/get-record model-name (request/id))))