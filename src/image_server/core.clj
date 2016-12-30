(ns image-server.core
  (:require [base64-clj.core :as base64]
            [clojure.java.io :refer [make-parents]]
            [compojure.core :refer [GET defroutes]]
            [compojure.route :refer [not-found]]
            [digest :refer [sha-256]]
            [image-server.randomimages :refer :all]
            [image-server.utils :refer :all]
            [org.httpkit.server :refer [run-server send! with-channel]]
            [ring.middleware.defaults :refer [api-defaults
                                              wrap-defaults]]
            [taoensso.timbre :as timbre :refer [error info]]
            [taoensso.timbre.appenders.core :as appenders])
  (:import (org.apache.commons.validator UrlValidator))
  (:gen-class))

; very simple regex because ^ and $ do not seem to work and it really should
; not matter much anyway
(def base64-regex #"[a-zA-Z0-9+/=]+")
(def port 8080)
(def log-file "server.log")

(defn decode-base64
  "Returns nil if the string could not be decoded"
  [s]
  (try
    (base64/decode s)
    (catch Exception e nil)))

(defn send-error [s channel]
  (error s)
  (send! channel {:status 400
                  :headers {"content-type" "text/plain"}
                  :body s}))

(defn send-redirect [url channel]
  (info (str "Redirecting to \"" url "\""))
  (send! channel {:status 307
                  :headers {"location" url}}))

(defn process-image-url [url channel]
  (let [new-filename (sha-256 url)]
    (if (exists-cache-file? new-filename)
      (if (non-empty-cache-file? new-filename)
        (send-jpg-cache-file new-filename channel)
        (send-redirect url channel)) ; we are still downloading the file
      (do
        (spit (str cache-directory new-filename) "")
        (send-redirect url channel)
        (download-to-cache-and-process url new-filename)))))

(defn get-image-url [{:keys [] :as req
                      {:keys [url]} :params}]
  (with-channel req channel
    (let [decoded-url (decode-base64 url)]
      (if decoded-url
        (if (.isValid (UrlValidator.) decoded-url)
          (process-image-url decoded-url channel)
          (send-error "Decoded URL is not valid" channel))
        (send-error "Invalid base64" channel)))))

(defroutes all-routes
  (GET ["/get/:url", :url base64-regex] [] get-image-url)
  (GET "/random" [] get-random)
  (not-found "Page not found"))

(defn prepare-before-server-start []
  (timbre/merge-config!
    {:appenders {:spit (appenders/spit-appender {:fname log-file})}})
  (check-imagemagick)
  (make-parents (clojure.java.io/file (str cache-directory "a"))))

(defn -main [& args]
  (prepare-before-server-start)
  (info (str "Starting server on port " port))
  (run-server (wrap-defaults all-routes api-defaults) {:port port}))
