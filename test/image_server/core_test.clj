(ns image-server.core-test
  (:require [base64-clj.core :as base64]
            [clojure.string :as s]
            [clojure.test :refer [deftest is testing]]
            [digest :refer [sha-256]]
            [image-server.core :refer :all]
            [image-server.randomimages :refer :all]
            [image-server.utils :refer :all]
            [org.httpkit.client :as http]))

(def test-image
  "https://en.wikipedia.org/static/images/project-logos/enwiki.png")
(def large-test-image
  (str "https://upload.wikimedia.org/wikipedia/commons/e/e3/"
       "Large_and_small_magellanic_cloud_from_new_zealand.jpg"))
(def non-image-url
  "https://raw.githubusercontent.com/Gira-X/VMT-Editor/master/misc/version.txt")

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

(defn is-local-url? [url]
  (= (format-localhost (str "get/" (base64/encode test-image))) url))

(defn got-redirected? [image]
  (let [{:keys [status]}
        @(http/get (format-localhost (str "get/" (base64/encode image)))
                   {:follow-redirects false})]
    (is (= status 307))))

(defn count-in-log [s log]
  (count (re-seq (re-pattern s) log)))

(def race-condition-test-amount 5)

(defn single-concurrent-test []
  (delete-files [(str cache-directory (sha-256 large-test-image))])
  (reset! downloads '())
  (Thread/sleep 200) ; catching up with the filesystem
  (doseq [f (doall
              (repeatedly 15 #(future (got-redirected? large-test-image))))]
    @f)
  (Thread/sleep 4000)) ; need to wait until the server has downloaded the image

; See issue #2
(defn test-concurrent-race-condition
  "Tests multiple concurrent calls to the same URL several times, hoping to
  trigger the race condition where the image is downloaded multiple times"
  []
  (doall (repeatedly race-condition-test-amount single-concurrent-test)))

(defn test-non-image-url []
    (let [{:keys [status body]}
          (localhost (str "get/" (base64/encode non-image-url)))]
      (is (= status 200))
      (Thread/sleep 500) ; waiting for the server
      (is (not (exists-cache-file? (str sha-256 non-image-url))))))

(deftest server
  (delete-files [log-file])
  (let [httpkitserver (-main "-debug")]
    (let [{:keys [status body]} (localhost)]
      (is (= status 404))
      (is (= body "Page not found")))
    (is (are-random-pics-random? (/ random-picture-amount 2)))
    (let [{:keys [status body]} (localhost "get/invalidbase64")]
      (is (= status 400))
      (is (= body "Invalid base64")))
    (let [{:keys [status body]}
          (localhost (str "get/" (base64/encode "www.notvalid")))]
      (is (= status 400))
      (is (= body "Decoded URL is not valid")))
    (test-non-image-url)
    ; second call returns the cached image which should be smaller than the
    ; original image
    (delete-files [(str cache-directory (sha-256 test-image))])
    (is (> (get-content-length test-image #(= test-image %) {:delay 0})
           (get-content-length test-image is-local-url? {:delay 1000})))
    (test-concurrent-race-condition)
    (httpkitserver))
  (let [log (slurp log-file)]
    (is (= race-condition-test-amount
           (count-in-log (str "Downloaded \"" large-test-image "\"") log)))
    (is (zero? (count-in-log "Exception: " log)))))
