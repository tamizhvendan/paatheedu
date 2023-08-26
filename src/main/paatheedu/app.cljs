(ns paatheedu.app
  (:require [uix.core :as uix :refer [defui $]]
            [uix.dom]
            ["xlsx" :as XLSX]
            ["pouchdb" :as PouchDB]
            ["moment" :as moment]
            ["pouchdb-replication-stream" :as replicationStream]
            ["memorystream" :as MemoryStream]
            ["pouchdb-load" :as pouchdbLoad]))

(defn- export-db [db on-success]
  (let [dumped-string (atom "")
        stream (new MemoryStream)]
    (.on stream "data" (fn [chunk]
                         (reset! dumped-string (str @dumped-string chunk))))
    (-> (.dump db stream)
        (.then #(on-success @dumped-string)))))

(defn- import-db [db backup on-success]
  (-> (.load db backup)
      (.then on-success)))

(defonce root
  (uix.dom/create-root (js/document.getElementById "root")))

(defonce db (do 
              (.plugin PouchDB (.-plugin replicationStream))
              (.plugin PouchDB pouchdbLoad)
              (.adapter PouchDB "writableStream" (.. replicationStream -adapters -writableStream))
              (new PouchDB "paatheedu")))

(def hdfc-bank-statement-sheet-schema
  #:sheet.schema{:transaction-date-column-name "A"
                 :narration-column-name "B"
                 :reference-number-column-name "C"
                 :value-date-column-name "D"
                 :debit-column-name "E"
                 :credit-column-name "F"
                 :closing-balance-column-name "G"
                 :first-transaction-row-number 23
                 :sheet-name "Sheet 1"
                 :date-format "DD/MM/YY"})

(def idfc-bank-statement-sheet-schema
  #:sheet.schema{:transaction-date-column-name "A"
                 :value-date-column-name "B"
                 :narration-column-name "C"
                 :reference-number-column-name "D"
                 :debit-column-name "E"
                 :credit-column-name "F"
                 :closing-balance-column-name "G"
                 :first-transaction-row-number 22
                 :sheet-name "Account Statement"
                 :date-format "DD-MMM-YYYY"})

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

(defn- parse-date-to-epoch [date-format date]
  (.unix (moment date date-format)))

(defn- transform-transaction-data [{:sheet.schema/keys [date-format]} transactions]
  (map (fn [{:transaction/keys [date value-date narration] :as transaction}]
         (assoc transaction
                :transaction/id (hash-string (str date ":" narration))
                :transaction/date (parse-date-to-epoch date-format date)
                :transaction/value-date (parse-date-to-epoch date-format value-date))) transactions))


(defn- save-transactions [bank-account-id transactions]
  (as-> (map #(assoc % 
                     :_id (str "transaction:" (:transaction/id %))
                     :transaction/bank-account-id bank-account-id
                     :type "transaction") transactions) $
    (map clj->js $)
    (clj->js $)
    (.bulkDocs db $)
    (.then $ #(js/console.log %))
    (.catch $ #(js/console.log %))))

(defn- save-bank-account [name schema on-success on-error]
  (-> (.put db (clj->js (assoc schema
                               :name name
                               :_id (str "bank-account:" (.valueOf (new js/Date)))
                               :type "bank-account")))
      (.then on-success)
      (.catch on-error)))

(defn- get-all-bank-accounts [on-success on-error]
  (-> (.allDocs db #js {:include_docs true
                        :startkey "bank-account:"
                        :endkey "bank-account:\ufff0"})
      (.then (fn [r]
               (on-success
                (->> ((js->clj r) "rows")
                     (map #(get % "doc"))
                     (map (fn [doc]
                            (update-keys doc (fn [key]
                                               (if (#{"name" "_rev", "_id", "type"} key)
                                                 (keyword key)
                                                 (keyword "sheet.schema" key))))))))))
      (.catch on-error)))

(defn- get-all-transactions [on-success on-error]
  (-> (.allDocs db #js {:include_docs true
                        :startkey "transaction:"
                        :endkey "transaction:\ufff0"})
      (.then (fn [r]
               (on-success
                (->> ((js->clj r) "rows")
                     (map #(get % "doc"))
                     (map (fn [doc]
                            (update-keys doc (fn [key]
                                               (if (#{"_rev", "_id", "type"} key)
                                                 (keyword key)
                                                 (keyword "transaction" key))))))))))
      (.catch on-error)))

(comment
  (.destroy db)
  ; HDFC bank-account:1693045183269
  (save-bank-account "HDFC" hdfc-bank-statement-sheet-schema prn prn)
  ; IDFC bank-account:1693045190153
  (save-bank-account "IDFC" idfc-bank-statement-sheet-schema prn prn)
  
  (get-all-bank-accounts prn prn)
  (get-all-transactions prn prn)
  (def backup (atom ""))
  (export-db db (fn [x]
                  (reset! backup x)
                  (prn x)))
  (count @backup)
  (js/download "paatheedu.txt" @backup)
  (def new-db (new PouchDB "temp3"))
  (import-db new-db @backup prn)
  )

(defn- on-file-upload [on-success ^js e]
  (.preventDefault e)
  (let [js-file-reader (js/FileReader.)
        uploaded-file (-> e .-target .-files first)]
    (set! (.-onload js-file-reader)
          (fn [evt]
            (on-success (-> evt .-target .-result))))
    (if uploaded-file
      (.readAsArrayBuffer js-file-reader uploaded-file)
      (on-success nil))))

(defn- on-file-upload-success [bank-account-id bank-statement-sheet-schema set-value! file-content]
  (let [xl-content (XLSX/read file-content)
        extracted-content (extract-transaction-data-from-sheet
                           bank-statement-sheet-schema
                           (js->clj (unchecked-get (.-Sheets xl-content)
                                                   (:sheet.schema/sheet-name bank-statement-sheet-schema))))
        transactions (transform-transaction-data bank-statement-sheet-schema extracted-content)]

    (set-value! transactions)
    (when (seq transactions)
      (save-transactions bank-account-id transactions))))

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
  (let [[value set-value!] (uix/use-state [])
        on-file-upload-success (partial on-file-upload-success "bank-account:1693045183269" hdfc-bank-statement-sheet-schema set-value!)]
    ($ :<>
       ($ :input {:type "file" :on-change (partial on-file-upload on-file-upload-success)})
       ($ :div {:class "mt-4"} (render-transactions value)))))

(defn init []
  (uix.dom/render-root ($ app) root))