(ns paatheedu.app
  (:require [uix.core :refer [defui $]]
            [uix.dom]))

(defonce root
  (uix.dom/create-root (js/document.getElementById "root")))

(defui app []
  ($ :span "Hello, World!"))

(defn init []
  (uix.dom/render-root ($ app) root))