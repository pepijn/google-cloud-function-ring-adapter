(ns nl.epij.gcf.run-test
  (:require [clojure.test :refer [deftest is]]
            [helpers :refer [with-tmp-dir list-files zip-file files-in-zip]]
            [nl.epij.gcf.run :as run]
            [clojure.java.io :as io]
            [badigeon.classpath :as classpath]))

(defn compiled-java!
  [body]
  (with-tmp-dir
   (fn [java-compile-dir]
     (let [class-path (classpath/make-classpath {:aliases [:example]})]
       (try (run/compile-javac! {:src-dir       "../example/src/java"
                                 :compile-path  java-compile-dir
                                 :javac-options ["-cp" class-path]})
            (body java-compile-dir))))))

(deftest java-compilation
  (is (= (compiled-java! list-files)
         ["JsonHttpEcho.class"])))

(defn assembled-uberjar!
  [body]
  (with-tmp-dir
   (fn [tmp-dir]
     (compiled-java!
      (fn [compile-path]
        (let [jar-path (io/file tmp-dir "uberjar.jar")
              options  '{:entrypoint JsonHttpEcho}]
          (run/build-jar! (merge options
                                 {:out-path     (str jar-path)
                                  :aliases      [:example]
                                  :compile-path compile-path}))
          (body jar-path)))))))

(deftest entrypoint-uberjar-generation
  (let [files (files-in-zip (assembled-uberjar! zip-file))]
    (is (contains? files "JsonHttpEcho.class"))
    (is (contains? files "nl/epij/gcf/example__init.class"))))
