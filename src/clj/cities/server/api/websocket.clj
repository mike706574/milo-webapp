(ns cities.server.api.websocket
  (:require [aleph.http :as http]
            [manifold.stream :as s]
            [manifold.deferred :as d]
            [manifold.bus :as bus]
            [cities.server.connection :as conn]
            [cities.server.message :refer [encode decode]]
            [cities.server.api.model :as model]
            [taoensso.timbre :as log]))

(defn non-websocket-response
  []
  {:status 400
   :headers {"content-type" "application/text"}
   :body "Expected a websocket request."})

(defn handle
  [{:keys [games invites player-bus conn-manager] :as deps} req]
  (d/let-flow [conn (d/catch
                        (http/websocket-connection req)
                        (constantly nil))]
    (if-not conn
      (non-websocket-response)
      (let [conn-id (conn/add! conn-manager :menu conn)
            conn-label (str "[ws-conn-" conn-id "] ")]
        (log/debug (str conn-label "Initial connection established."))
        (d/let-flow [initial-message @(s/take! conn)]
          (try
            (let [player (decode initial-message)]
              (log/debug (str conn-label "Connecting player \"" player "\"."))
              ;; Updates
              (s/connect-via
               (bus/subscribe player-bus player)
               (fn [message]
                 (log/debug (str conn-label "Preparing " (:cities/status message) " message for " player "."))
                 (s/put! conn (encode message)))
               conn)
              (log/debug (str conn-label "Connected player \"" player "\"."))
              (s/put! conn (encode {:cities/status :connected}))
              {:status 101})
            (catch Exception e
              (log/error e (str conn-label "Exception thrown while connecting player. Initial message: " initial-message))
              {:status 500})))))))

(defn handler
  [deps]
  (partial handle deps))
