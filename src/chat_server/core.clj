(ns chat-server.core
  (:gen-class)
  (:require [org.httpkit.server :as server]
            [clojure.tools.logging :refer [info]]
            [clojure.data.json :refer [json-str read-json]]
            [compojure.core :refer [defroutes GET POST]]
            [compojure.route :refer [files not-found]]
            [compojure.handler :refer [site]]))



(defn now
  "quot perfroms division"
  []
  (quot (System/currentTimeMillis) 1000))

(def clients (atom {}))

(let [msg-id (atom 0)]
  (defn next-id
    "increases msg id"
    []
    (swap! msg-id inc)))

(def all-msgs (ref [{:id (next-id)
                     :time (now)
                     :msg "this is a live chatroom, have fun"
                     :author "system"}]))

(defn mesg-received [msg]
  (let [data (read-json msg)]
    (info "mesg received" data)
    (when (:msg data)
      (let [data (merge data {:time (now) :id (next-id)})]
        (dosync
         (let [all-msgs* (conj @all-msgs data)
               total (count all-msgs*)]
           (if (> total 100)
             (ref-set all-msgs (vec (drop (- total 100) all-msgs*)))
             (ref-set all-msgs all-msgs*)))))
      (doseq [client (keys @clients)]
        (server/send! client (json-str @all-msgs))))))

(defn chat-handler [req]
  (server/as-channel req
                     {:on-open (fn [ch]
                                 (info ch "connected")
                                 (swap! clients assoc ch true))
                      :on-receive (fn [ch msg]
                                    (mesg-received msg))
                      :on-close (fn [ch status]
                                  (swap! clients dissoc ch)
                                  (info ch "closed, status" status))}))

(defroutes chatroom
  (GET "/ws" [] chat-handler))


(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (server/run-server chatroom {:port 9889})
  (info "server started. http://127.0.0.1:9889"))
