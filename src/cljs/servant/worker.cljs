(ns worker.core
  (:require 
            [servant.test-ns :as test-ns]
            [cljs.core.async :refer [chan close! timeout ]])
  (:require-macros [cljs.core.async.macros :as m :refer [go]])
  )
 
(def worker-fn-map (atom {}))

;; We will keep the worker alive, and when we get a message to do work

(defn inject-function 
  "Returns the name of the injected function"
  [function-str]
  (let [function-name (gensym "func")
        function-blob (js/Blob. (clj->js [(str function-name) " = (" function-str ")" ]) (clj->js {:type "text/javascript"}))
        function-script (.createObjectURL js/URL function-blob)]
    (js/importScripts function-script)
    function-name))

(defn run-function [message-data]
  ;; First we need to inject the function into the worker scope
  (let [function-name (inject-function (aget message-data "function-str"))
        args (.-args message-data)]
    ;; The function has been loaded, lets call the function
    (apply (aget js/self function-name) args)))

(defn run-function-name [message-data]
  (let [function-name (inject-function (aget message-data "fn"))
        f (get @worker-fn-map function-name)
        args (.-args message-data)]
    ;; The function has been loaded, lets call the function
    (apply f args)))

(defn decode-message [event]
  (->>
    (condp = (.-command (.-data event))
      "function" (run-function (.-data event))
      "channel" (run-function-name (.-data event))
      "echo" (.-data event)
      (.-data event))
    (.postMessage js/self)
    ))  


(defn bootstrap []
  (.postMessage js/self (str "Hello there! " "I'm ready for action :D"))
  (set! (.-onmessage js/self) decode-message))
