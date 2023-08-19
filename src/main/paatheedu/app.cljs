(ns paatheedu.app
  (:require [uix.core :as uix :refer [defui $]]
            [uix.dom]
            [goog.labs.format.csv :as csv]
            [clojure.string :as str]))

(defonce root
  (uix.dom/create-root (js/document.getElementById "root")))

(defn- format-csv [parsed-csv]
  (->> (filter seq parsed-csv)
       (map #(map str/trim %))))

#_ (.parse js/Date (clojure.string/trim (first (get d 2))))

(defn- on-file-upload [set-value! ^js e]
  (.preventDefault e)
  (let [js-file-reader (js/FileReader.)
        uploaded-file (-> e .-target .-files first)]
    (set! (.-onload js-file-reader)
          (fn [evt]
            (let [file-content (-> evt .-target .-result)
                  parsed-content (csv/parse file-content)]
              (prn file-content)
              (set-value! parsed-content))))
    (if uploaded-file
     (.readAsText js-file-reader uploaded-file) 
     (set-value! []))))

(defn- render-csv [formatted-csv-content]
  (let [[header & rows] formatted-csv-content]
    ($ :table 
       ($ :thead
          ($ :tr
             (map #($ :th {:key (gensym)} %) header)))
       ($ :tbody
          (map (fn [row]
                 ($ :tr {:key (gensym)}
                    (map #($ :td {:key (gensym)} %) row))) rows)))))

(defui app []
  (let [[value set-value!] (uix/use-state [])]
    ($ :<> 
       ($ :input {:type "file" :on-change (partial on-file-upload set-value!)})
       ($ :div {:class "mt-4"} (-> value
                                 format-csv
                                 render-csv)))))

(defn init []
  (uix.dom/render-root ($ app) root))