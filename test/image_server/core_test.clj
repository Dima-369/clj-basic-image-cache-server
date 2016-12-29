(ns image-server.core-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.string :as s]
            [clojure.java.io :refer [delete-file]]
            [base64-clj.core :as base64]
            [digest :refer [sha-256]]
            [org.httpkit.client :as http]
            [image-server.core :refer :all]
            [image-server.utils :refer :all]
            [image-server.randomimages :refer :all]))

(def test-image "http://designed-x.com/img/github.png")
; https://upload.wikimedia.org/wikipedia/commons/e/e3/Large_and_small_magellanic_cloud_from_new_zealand.jpg
(def large-test-image "http://designed-x.com/large.jpg")

(defn file-seq-string
  "Returns only files in the passed directory"
  [dir]
  (filter #(.isFile %) (file-seq (clojure.java.io/file dir))))

(deftest tests
  (testing "Random Pictures"
    (let [random-pics (map #(.getName %) (file-seq-string "resources/random/"))]
      (is (= random-picture-amount (count random-pics)))
      (is (every? #(s/ends-with? % ".jpg") random-pics))
      (is (= random-picture-amount
             (count (apply hash-set
                           (repeatedly (* 10 random-picture-amount)
                                       get-random-pic))))))))

(defn format-localhost [url]
  (str "http://localhost:" port "/" url))

(defn localhost
  ([] (localhost ""))
  ([url] @(http/get (format-localhost url))))

(defmacro swallow-exceptions [& body]
  `(try ~@body (catch Exception e#)))

(defn delete-files [files]
  (doseq [f files] (swallow-exceptions (delete-file f))))

(defn are-random-pics-random? [n]
  (not= n (count
            (apply hash-set
                   (repeatedly n #(get-in (localhost "random")
                                          [:headers :content-length]))))))

(defn get-content-length
  "A function which validates the f should be passed for f and m
  should contain {:delay 100} or any other value.

  The content-length of the response is returned"
  [imageUrl f m]
  (when-let [delay (:delay m)]
    (Thread/sleep delay))
  (let [{:keys [status]
         {:keys [content-length]} :headers
         {:keys [url]} :opts}
        (localhost (str "get/" (base64/encode imageUrl)))]
    (is (= status 200))
    (is (f url))
    (Integer. content-length)))

(defn test-random-image []
  (let [{:keys [status]
         {:keys [content-length]} :headers
         {:keys [url]} :opts}
        (localhost "random")]
    (is (= status 200))
    (is (= (format-localhost "random") url))))

(defn is-local-url? [url]
  (= (format-localhost (str "get/" (base64/encode test-image))) url))

(defn is-redirect? [image]
  (let [{:keys [status]
         {:keys [url]} :opts}
        (localhost (str "get/" (base64/encode image)))]
    (is (= status 200))
    (is (= url image))))

(defn count-downloads-in-log [s log]
  (count (re-seq (re-pattern (str "Downloaded \"" s "\"")) log)))

(deftest server
  (delete-files [log-file
                 (str cache-directory (sha-256 test-image))
                 (str cache-directory (sha-256 large-test-image))])
  (let [httpkitserver (-main)]
    (let [{:keys [status body]} (localhost)]
      (is (= status 404))
      (is (= body "Page not found")))
    (is (are-random-pics-random? 30))
    (let [{:keys [status body]} (localhost "get/invalidbase64")]
      (is (= status 400))
      (is (= body "Invalid base64")))
    (let [{:keys [status body]}
          (localhost (str "get/" (base64/encode "www.notvalid")))]
      (is (= status 400))
      (is (= body "Decoded URL is not valid")))
    (doall (repeatedly 5 test-random-image))
    ; second call returns the cached image which should be smaller than the
    ; original image
    (is (> (get-content-length test-image #(= test-image %) {:delay 0})
           (get-content-length test-image is-local-url? {:delay 1000})))
    ; testing multiple concurrent calls
    (doseq [f (doall (repeatedly 15 #(future (is-redirect? large-test-image))))]
      @f)
    ; wait until all server threads are finished, the above future calls are
    ; usually faster
    (Thread/sleep 1000)
    (httpkitserver))
  (let [log (slurp log-file)]
    (is (= 1 (count-downloads-in-log large-test-image log)))))
