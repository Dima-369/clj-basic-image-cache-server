(ns scraper.scrape-unsplash
  (:require [clojure.java.io :refer [copy input-stream output-stream]]))

(defn download-file [url name]
  (with-open [in (input-stream url)
              out (output-stream (str "resources/random/" name))]
    (copy in out)))

(defn unsplash-link [res] (str "https://unsplash.it/" res "/" res "/?random"))

(def unsplash-link-memo (memoize unsplash-link))

(defn scrape-unsplash
  "Use :amount and :resolution in the passed map"
  [m]
  (let [amount (:amount m)
        res (:resolution m)]
    (map #(download-file (unsplash-link-memo res) (str % ".jpg"))
         (range 1 (inc amount)))))
