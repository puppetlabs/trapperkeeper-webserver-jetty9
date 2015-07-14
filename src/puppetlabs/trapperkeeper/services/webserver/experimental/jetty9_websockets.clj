(ns puppetlabs.trapperkeeper.services.webserver.experimental.jetty9-websockets
  (:import (clojure.lang IFn)
           (org.eclipse.jetty.server Request)
           (org.eclipse.jetty.websocket.api WebSocketAdapter Session)
           (org.eclipse.jetty.websocket.server WebSocketHandler)
           (org.eclipse.jetty.websocket.servlet WebSocketServletFactory WebSocketCreator)
           (java.security.cert X509Certificate)
           (java.nio ByteBuffer))

  (:require [clojure.tools.logging :as log]
            [puppetlabs.experimental.websockets.client :refer [WebSocketProtocol]]
            [schema.core :as schema]))

(def WebsocketHandlers
  {(schema/optional-key :on-connect) IFn
   (schema/optional-key :on-error) IFn
   (schema/optional-key :on-close) IFn
   (schema/optional-key :on-text) IFn
   (schema/optional-key :on-bytes) IFn})

(defprotocol WebSocketSend
  (-send! [x ws] "How to encode content sent to the WebSocket clients"))

(extend-protocol WebSocketSend
  (Class/forName "[B")
  (-send! [ba ws]
    (-send! (ByteBuffer/wrap ba) ws))

  ByteBuffer
  (-send! [bb ws]
    (-> ^WebSocketAdapter ws .getRemote (.sendBytes ^ByteBuffer bb)))

  String
  (-send! [s ws]
    (-> ^WebSocketAdapter ws .getRemote (.sendString ^String s))))

(extend-protocol WebSocketProtocol
  WebSocketAdapter
  (send! [this msg]
    (-send! msg this))
  (close! [this]
    (.. this (getSession) (close)))
  (remote-addr [this]
    (.. this (getSession) (getRemoteAddress)))
  (ssl? [this]
    (.. this (getSession) (getUpgradeRequest) (isSecure)))
  (peer-certs [this]
    (.. this (getCerts)))
  (idle-timeout! [this ms]
    (.. this (getSession) (setIdleTimeout ^long ms)))
  (connected? [this]
    (. this (isConnected))))

(definterface CertGetter
  (^Object getCerts []))

(defn no-handler
  [event & args]
  (log/debugf "No handler defined for websocket event '%s' with args: '%s'" event args))

(schema/defn ^:always-validate proxy-ws-adapter :- WebSocketAdapter
  [handlers :- WebsocketHandlers
   x509certs :- [X509Certificate]]
  (let [{:keys [on-connect on-error on-text on-close on-bytes]
         :or {on-connect (partial no-handler :on-connect)
              on-error   (partial no-handler :on-error)
              on-text    (partial no-handler :on-text)
              on-close   (partial no-handler :on-close)
              on-bytes   (partial no-handler :on-bytes)}} handlers]
    (proxy [WebSocketAdapter CertGetter] []
      (onWebSocketConnect [^Session session]
        (let [^WebSocketAdapter this this]
          (proxy-super onWebSocketConnect session))
        (on-connect this))
      (onWebSocketError [^Throwable e]
        (let [^WebSocketAdapter this this]
          (proxy-super onWebSocketError e))
        (on-error this e))
      (onWebSocketText [^String message]
        (let [^WebSocketAdapter this this]
          (proxy-super onWebSocketText message))
        (on-text this message))
      (onWebSocketClose [statusCode ^String reason]
        (let [^WebSocketAdapter this this]
          (proxy-super onWebSocketClose statusCode reason))
        (on-close this statusCode reason))
      (onWebSocketBinary [^bytes payload offset len]
        (let [^WebSocketAdapter this this]
          (proxy-super onWebSocketBinary payload offset len))
        (on-bytes this payload offset len))
      (getCerts [] x509certs))))

(schema/defn ^:always-validate proxy-ws-creator :- WebSocketCreator
  [handlers :- WebsocketHandlers]
  (reify WebSocketCreator
    (createWebSocket [this req _]
      (let [x509certs (vec (.. req (getCertificates)))]
        (proxy-ws-adapter handlers x509certs)))))

(schema/defn ^:always-validate websocket-handler :- WebSocketHandler
  "Returns a Jetty WebSocketHandler implementation for the given set of Websocket handlers"
  [handlers :- WebsocketHandlers]
  (proxy [WebSocketHandler] []
    (configure [^WebSocketServletFactory factory]
      (.setCreator factory (proxy-ws-creator handlers)))
    (handle [^String target, ^Request request req res]
      (let [wsf (proxy-super getWebSocketFactory)]
        (if (.isUpgradeRequest wsf req res)
          (if (.acceptWebSocket wsf req res)
            (.setHandled request true)
            (when (.isCommitted res)
              (.setHandled request true)))
          (proxy-super handle target request req res))))))
