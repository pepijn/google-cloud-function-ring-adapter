(ns nl.epij.gcp.gcf.ring
  (:require [clojure.string :as str]
            [nl.epij.gcp.gcf.env :as env])
  (:import (com.google.cloud.functions HttpRequest HttpResponse)
           (java.io BufferedWriter)
           [java.util Optional]))

(defn process-response!
  [{:keys [status headers body]} http-response]
  (doseq [[key value] headers
          :let [key' (if (keyword? key) (name key) key)]]
    (.appendHeader http-response key' value))
  (.setStatusCode http-response status)
  (when body (.write ^BufferedWriter (.getWriter http-response) body)))

(defn adapter
  [^HttpRequest http-request ^HttpResponse http-response handler]
  (let [{:strs [host
                x-forwarded-for
                x-forwarded-proto]
         :as headers}
        (into {}
              (map (fn [[k v]] [(str/lower-case k) (str/join v)]))
              (.getHeaders http-request))
        query-string   ^Optional (.getQuery http-request)
        query-string'  (.orElse query-string nil)
        request-method (-> (.getMethod http-request)
                           str/lower-case
                           keyword)
        uri            (.getPath http-request)
        {::env/keys [port] :as platform-env} (env/extract-env-vars!)
        request-base   {:request-method request-method
                        :uri            uri
                        :query-string   query-string'
                        :headers        headers
                        :body           (.getInputStream http-request)

                        :server-name    host
                        :server-port    (Integer/parseInt port)
                        :remote-addr    x-forwarded-for
                        :scheme         (keyword x-forwarded-proto)}
        request        (merge request-base platform-env)]
    (-> (handler request)
        (process-response! http-response))))
