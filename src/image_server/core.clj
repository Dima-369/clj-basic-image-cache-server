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
            [taoensso.timbre :refer [error info merge-config!
                                     spit-appender]])
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
        (future (download-to-cache-and-process url new-filename))))))

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

; if :println is used the default println appender is overwritten
; we only overwrite it in release mode because tests are nicer if the log is
; also visible in the console
(defn get-appender-name [debug?]
  (if debug? :spit :println))

(defn prepare-before-server-start [debug?]
  (merge-config! {:appenders {(get-appender-name debug?)
                              (spit-appender {:fname log-file})}})
  (check-imagemagick)
  (make-parents (clojure.java.io/file (str cache-directory "a"))))

(defn debug-from-args? [args]
  (if (nil? args)
    false
    (not= -1 (.indexOf args "-debug"))))

(defn -main [& args]
  (prepare-before-server-start (debug-from-args? args))
  (info (str "Starting server on port " port))
  (run-server (wrap-defaults all-routes api-defaults) {:port port}))
