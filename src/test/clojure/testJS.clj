(ns testJS
    (:import (survey Question Block$BranchParadigm Survey Block Component StringComponent)
             (interstitial IQuestionResponse OptTuple Record BackendType Library BoxedBool ISurveyResponse
                           AbstractResponseManager)
             (system.localhost LocalLibrary LocalResponseManager Server)
             (qc RandomRespondent RandomRespondent$AdversaryType)
             (system Runner)
             (system.generators HTML)
             (system.localhost.generators LocalHTML)
             (input Slurpie))
    (:use testLog)
    (:use clojure.test)
    (:use clj-webdriver.taxi)
    (:require (qc analyses)))

;; generate answer set
;; start up server
;; navigate server
;; make sure returned SR corresponds to the answer set

(def numQ (atom 1))


(defn sampling?
    [^Question q]
  (= (.branchParadigm ^Block (.block q)) Block$BranchParadigm/ALL)
  )


(defn compute-offset
  [^String qid ^String oid]
  (let [a (read-string ((clojure.string/split qid #"_") 1))
        b (read-string ((clojure.string/split oid #"_") 1))]
    (- b a)
    )
  )

(defn getAltOid
    [^Question q ^String qid ^String oid]
    (let [offset (compute-offset qid oid)
          dat (clojure.string/split (.quid q) #"_")
          optcol ((clojure.string/split oid #"_") 2)]
        (if (sampling? q)
            (format "comp_%d_%s" (+ offset (read-string (dat 1))) optcol)
            oid
            )
        )
    )

(defn resolve-variant
    [^String qid ansMap ^Survey survey]
    ;; qid will be the question displayed in the html
    (let [variants (.getVariantSet survey (.getQuestionById survey qid))]
        (if (sampling? (.getQuestionById survey qid))
            (first (filter #(contains? ansMap (.quid ^Question %)) variants))
            (.getQuestion ^IQuestionResponse (.get ansMap qid))
            )
        )
    )

(defn compare-answers
    [^IQuestionResponse qr1 ^IQuestionResponse qr2]
    ;;(println "question responses:" qr1 "\n" qr2)
    (if (and (sampling? (.getQuestion qr1))
             (sampling? (.getQuestion qr2))
             (= (.block (.getQuestion qr1)) (.block (.getQuestion qr2))))
        (doseq [[opt1 opt2] (map vector (.getOpts qr1) (.getOpts qr2))]
            (let [offset1 (compute-offset (.quid (.getQuestion qr1)) (.getCid ^Component (.c ^OptTuple opt1)))
                  offset2 (compute-offset (.quid (.getQuestion qr2)) (.getCid ^Component (.c ^OptTuple opt2)))]
                (is (= offset1 offset2))
                )
            )
        (= qr1 qr2)
        )
    )

(defn subsetOf
    [ansMapS ansMapB s]
    (reduce #(and %1 %2) true
            (map #(let [q (.getQuestionById s %)]
                     (or (.freetext q)
                         (empty? (.options q))
                         (compare-answers (get ansMapS %) (get ansMapB (.quid (resolve-variant % ansMapB s))))
                         )
                     )
                 (keys ansMapS)
                 )
            )
    )

(defn answer-survey
  ([driver q2ansMap survey qid]
   (cond (and (not= qid "") (find-element driver {:id (str "next_" qid)})) (do (click driver (str "input#next_" qid))
                                                                               (recur driver q2ansMap survey ""))
         (find-element driver {:id "final_submit"}) (submit driver "#final_submit")
         :else (let [qid (attribute (find-element driver {:class "question"}) :name) ;{:id (str "ans" @numQ)})
                     qseen (.getQuestionById survey qid)
                     q (resolve-variant qid q2ansMap survey) ;; this is the q that's in the answer map
                     oids (map #(.getCid ^Component (.c ^OptTuple %)) (.getOpts (get q2ansMap (.quid q))))
                     ]
                 (doseq [oid oids]
                   (let [oidseen (getAltOid qseen (.quid q) oid)]
                     (println qseen ":" (.getOptById qseen oidseen) "\n" q ":" (.getOptById q oid) (map #(.c %) (.getOpts (get q2ansMap (.quid q)))))
                     (cond (.freetext qseen) (input-text driver {:id qid} (.data ^StringComponent
                                                                                 (.c (first (.getOpts (.get q2ansMap (.quid q)))))))
                           (empty? (.options qseen)) :noop
                           :else (select (find-element driver {:id oidseen})))
                     )
                   )
                 (swap! numQ inc)
                 (recur driver q2ansMap survey qid)
                 )
         )
   )
  ([driver q2ansMap survey]
   (answer-survey driver q2ansMap survey "")
   )
  )

(deftest answerInvariant
  (doseq [^Survey survey (keys @response-lookup)]
    (let [^LocalLibrary lib (LocalLibrary.)
          q2ansMap (-> (RandomRespondent. survey RandomRespondent$AdversaryType/UNIFORM)
                       (.response)
                       (.resultsAsMap))
          ^Record record (do (LocalResponseManager/putRecord survey lib BackendType/LOCALHOST)
                             (LocalResponseManager/getRecord survey))
          ^String url ( -> record
                           (.getHtmlFileName)
                           (.split (Library/fileSep))
                           (->> (last)
                                (format "http://localhost:%d/logs/%s" Server/frontPort)))
          ^BoxedBool interrupt (BoxedBool. false)
          ^Thread runner (Thread. (fn [] (Runner/run record interrupt BackendType/LOCALHOST)))
         ]
      (println (.source survey))
      (Runner/init BackendType/LOCALHOST)
      (HTML/spitHTMLToFile (HTML/getHTMLString survey (LocalHTML.)) survey)
      (assert (not= (count (Slurpie/slurp (.getHtmlFileName record))) 0))
      (Server/startServe)
      (.start (Runner/makeResponseGetter survey interrupt BackendType/LOCALHOST))
      (.start runner)
      (let [driver (new-driver {:browser :firefox})]
        (to driver url)
        (Thread/sleep 500)
        (try
          (click driver "#continue")
          (catch Exception e (println (.getMessage e))))
        (answer-survey driver q2ansMap survey)
        (quit driver)
        (while (empty? (.responses record)) (AbstractResponseManager/chill 2))
        (.setInterrupt interrupt true "Finished test")
        (Thread/sleep 7500)
        (let [responses (.responses record)
              responseMap (.resultsAsMap ^ISurveyResponse (first responses))]
            (is (= (count responses) 1))
            (subsetOf responseMap q2ansMap survey))
        (Server/endServe)
        (reset! numQ 1)
        (Thread/sleep 7500)
        )
      )
    )
  )