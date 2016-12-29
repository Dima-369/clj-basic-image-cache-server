(ns image-server.randomimages
  (:require [image-server.utils :refer :all]
            [clojure.java.io :refer [resource]]
            [org.httpkit.server :refer [with-channel]])
  (:gen-class))

(def random-picture-dir "random/")
(def random-picture-amount 25)

(defn get-random-pic []
  ; do not call file here, it will pass the tests but will fail when the uberjar
  ; is actually ran
  (resource (str random-picture-dir (inc (rand-int random-picture-amount))
                 ".jpg")))

(defn get-random [req]
  (with-channel req channel
    (send-jpg-file (get-random-pic) channel)))
