(ns paatheedu.app
  (:require [uix.core :as uix :refer [defui $]]
            [uix.dom]
            [goog.object]
            ["xlsx" :as XLSX]))

(defonce root
  (uix.dom/create-root (js/document.getElementById "root")))

(def hdfc-bank-statement-sheet-schema
  #:sheet.schema{:transaction-date-column-name "A"
                 :narration-column-name "B"
                 :reference-number-column-name "C"
                 :value-date-column-name "D"
                 :debit-column-name "E"
                 :credit-column-name "F"
                 :closing-balance-column-name "G"
                 :first-transaction-row-number 23
                 :sheet-name "Sheet 1"})

(def idfc-bank-statement-sheet-schema
  #:sheet.schema{:transaction-date-column-name "A"
                 :value-date-column-name "B"
                 :narration-column-name "C"
                 :reference-number-column-name "D"
                 :debit-column-name "E"
                 :credit-column-name "F"
                 :closing-balance-column-name "G"
                 :first-transaction-row-number 22
                 :sheet-name "Account Statement"})

(defn- extract-transaction-data-from-sheet [{:sheet.schema/keys [transaction-date-column-name
                                                                 narration-column-name
                                                                 reference-number-column-name
                                                                 value-date-column-name
                                                                 debit-column-name
                                                                 credit-column-name
                                                                 closing-balance-column-name
                                                                 first-transaction-row-number]} sheet]
  (reduce (fn [acc row-number]
            (let [transaction-date (get sheet (str transaction-date-column-name row-number))
                  value-date (get sheet (str value-date-column-name row-number))
                  narration (get sheet (str narration-column-name row-number))
                  reference-number (get sheet (str reference-number-column-name row-number))
                  debit (get sheet (str debit-column-name row-number))
                  credit (get sheet (str credit-column-name row-number))
                  closing-balance (get sheet (str closing-balance-column-name row-number))]
              (if (every? nil? [transaction-date value-date narration
                                reference-number debit credit closing-balance])
                (reduced acc)
                (conj acc #:transaction{:date (get transaction-date "v")
                                        :value-date (get value-date "v")
                                        :narration (get narration "v")
                                        :reference-number (get reference-number "v")
                                        :debit (get debit "v")
                                        :credit (get credit "v")
                                        :closing-balance (get closing-balance "v")}))))
          []
          (filter #(<= first-transaction-row-number %) (range))))

(defn- on-file-upload [bank-statement-sheet-schema set-value! ^js e]
  (.preventDefault e)
  (let [js-file-reader (js/FileReader.)
        uploaded-file (-> e .-target .-files first)]
    (set! (.-onload js-file-reader)
          (fn [evt]
            (let [file-content (-> evt .-target .-result)
                  xl-content (XLSX/read file-content)
                  extracted-content (extract-transaction-data-from-sheet
                                     bank-statement-sheet-schema
                                     (js->clj (unchecked-get (.-Sheets xl-content) 
                                                     (:sheet.schema/sheet-name bank-statement-sheet-schema))))]
              (set-value! extracted-content))))
    (if uploaded-file
      (.readAsArrayBuffer js-file-reader uploaded-file)
      (set-value! []))))




(defn- render-transactions [transactions]
  (when (seq transactions)
    ($ :table
      ($ :thead
         ($ :tr
            ($ :th "Transaction Date")
            ($ :th "Narration")
            ($ :th "Debit")
            ($ :th "Credit")
            ($ :th "Closing Balance")))
      ($ :tbody
         (map (fn [{:transaction/keys [date narration debit credit closing-balance]}]
                ($ :tr {:key (gensym)}
                   ($ :td date)
                   ($ :td narration)
                   ($ :td debit)
                   ($ :td credit)
                   ($ :td closing-balance))) transactions)))))

(defui app []
  (let [[value set-value!] (uix/use-state [])]
    ($ :<>
       ($ :input {:type "file" :on-change (partial on-file-upload idfc-bank-statement-sheet-schema set-value!)})
       ($ :div {:class "mt-4"} (render-transactions value)))))

(defn init []
  (uix.dom/render-root ($ app) root))