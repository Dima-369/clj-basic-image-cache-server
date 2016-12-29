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

(def test-image "http://designed-x.com/img/me.png")
(def test-image-sha (sha-256 test-image))

(defmacro swallow-exceptions [& body]
  `(try ~@body (catch Exception e#)))

(defn file-seq-string
  "Takes a string instead of clojure.java.io/file and returns only files"
  [dir]
  (filter #(.isFile %) (file-seq (clojure.java.io/file dir))))

(deftest tests
  (testing "Random Pictures"
    (let [random-pics (map #(.getName %) (file-seq-string "resources/random/"))]
      (is (= 48 (count random-pics)))
      (is (every? #(s/ends-with? % ".jpg") random-pics))
      (is (= (apply hash-set (map #(str random-picture-dir %) random-pics))
             (apply hash-set (repeatedly 2000 #(get-random-pic))))))))

(defn format-localhost [url]
  (str "http://localhost:" port "/" url))

(defn localhost
  ([] (localhost ""))
  ([url] @(http/get (format-localhost url))))

(defn are-random-pics-random? [n]
  (not= n (count
            (apply hash-set
                   (repeatedly n #(get-in (localhost "get/random")
                                          [:headers :content-length]))))))

(defn test-image-download-url [f]
  (let [{:keys [status] :as resp
         {:keys [content-length]} :headers
         {:keys [url]} :opts}
        (localhost (str "get/" (base64/encode test-image)))]
    (is (= status 200))
    (is (> (Integer. content-length) 1000))
    (is (f url))))

(deftest server
  (swallow-exceptions (delete-file log-file))
  (swallow-exceptions (delete-file (str cache-directory test-image-sha)))
  (let [httpkitserver (-main)]
    (let [{:keys [status body]} (localhost)]
      (is (= status 404))
      (is (= body "Page not found")))
    (is (are-random-pics-random? 50))
    (let [{:keys [status body]} (localhost "get/invalidbase64")]
      (is (= status 400))
      (is (= body "Invalid base64")))
    (let [{:keys [status body]}
          (localhost (str "get/" (base64/encode "www.notvalid")))]
      (is (= status 400))
      (is (= body "Decoded URL is not valid")))
    (let [url (format-localhost (str "get/" (base64/encode test-image)))]
      (test-image-download-url #(= test-image %))
      (test-image-download-url #(= url %))) ; 2. call is from this server
    (httpkitserver))
  (is (not-empty (slurp log-file))))
