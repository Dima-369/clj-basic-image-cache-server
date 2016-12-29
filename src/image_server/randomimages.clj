(ns image-server.randomimages
  (:require [image-server.utils :refer :all]
            [org.httpkit.server :refer [with-channel]])
  (:gen-class))

(def random-picture-dir "resources/random/")

(defn get-random-pic []
  (str random-picture-dir (inc (rand-int 48)) ".jpg"))

(defn get-random [req]
  (with-channel req channel
    (send-jpg-file (get-random-pic) channel)))
