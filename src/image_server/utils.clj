(ns image-server.utils
  (:require [clojure.java.io :refer [copy delete-file file input-stream
                                     output-stream]]
            [clojure.java.shell :refer [sh]]
            [org.httpkit.server :refer [send!]]
            [taoensso.timbre :refer [error info]])
  (:gen-class))

(def cache-directory "resources/cache/")

(defn non-empty-cache-file? [name]
  (pos? (.length (file (str cache-directory name)))))

(defn exists-cache-file? [name]
  (.exists (file (str cache-directory name))))

(defn is-cache-image-file? [name]
  (zero? (:exit (sh "identify" (str cache-directory name)))))

(defn send-jpg-file [file channel]
  (info (str "Serving \"" file "\""))
  (send! channel {:status 200
                  :headers {"content-type" "image/jpeg"}
                  :body (input-stream file)}))

(defn send-jpg-cache-file [file channel]
  (send-jpg-file (str cache-directory file) channel))

(defn nanoTime-diff [start]
  (/ (double (- (System/nanoTime) start)) 1000000.0))

(defn downscaled-cache-image-to-jpg?
  "Converts to a JPG with 80% quality and a maximum side length of 800px"
  [name]
  (let [name (str cache-directory name)]
    (sh "convert" "-quality" "80" "-resize" "800x800>"
        name (str "jpg:" name))))

(defn download-cache-file-to-raw [url name]
  (with-open [in (input-stream url)
              out (output-stream (str cache-directory name))]
    (copy in out)))

(defn delete-cache-file [name]
  (delete-file (str cache-directory name)))

(defn process-downloaded-image [name]
  (if (is-cache-image-file? name)
    (when-not (downscaled-cache-image-to-jpg? name)
      (delete-cache-file name)
      (error (str "Could not downscale image: \"" name "\"")))
    (do
      (delete-cache-file name)
      (error (str "Not an image file: \"" name "\"")))))

(defn download-to-cache-and-process [url name]
  (info (str "Downloading from \"" url "\""))
  (let [start (System/nanoTime)]
    (try
      (do
        (download-cache-file-to-raw url name)
        (info (str "Downloaded \"" url "\" in " (nanoTime-diff start) "ms"))
        (process-downloaded-image name))
      (catch Exception e
        (do
          (error (str "Exception on \"" url "\": " e))
          (delete-cache-file name))))))

(defn check-imagemagick []
  (try
    (sh "identify" "&&" "convert")
    (catch Exception e
      (do
        (error "ImageMagick seems to be missing!")
        (System/exit 1)))))
