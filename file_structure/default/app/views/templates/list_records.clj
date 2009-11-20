(ns views.templates.list-records
  (:use conjure.view.base)
  (:require [clj-html.core :as html]
            [clj-html.utils :as utils]
            [clj-html.helpers :as helpers]))

(defview [records]
  (html/html 
    [:table
      (utils/domap-str [record records]
        (html/html 
          [:tr 
            (html/htmli 
              (map 
                (fn [record-key] [:td (link-to-if (= :id record-key) (helpers/h (get record record-key)) request-map { :action "show", :id (:id record) })]) 
                (keys record)))
            [:td 
              (form-for request-map { :action "delete" :name "delete" } 
                (hidden-field record "record" :id)
                (form-button "Delete"))]]))]
    (link-to "Add" request-map { :action "add" } )))