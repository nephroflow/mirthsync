(ns mirthsync.actions
  (:require [clojure.data.xml :as xml]
            [clojure.tools.logging :as log]
            [clojure.zip :as zip]
            [mirthsync.cli :as cli]
            [mirthsync.http-client :as mhttp]
            [mirthsync.xml :as mxml])
  (:import java.io.File))

(defn upload-node
  "Extracts the id from the xmlloc using the find-id predicates. PUTs or
  POSTs the params to the location constructed from the base-url,
  rest-path, and id."
  [{:keys [el-loc] :as app-conf
    {:keys [post-path push-params after-push] :as api} :api}]

  (let [params (log/spyf :trace "Push params: %s" (push-params app-conf))
        result (if (post-path api)
                 (mhttp/post-xml app-conf params)
                 (mhttp/put-xml app-conf params))]
    (after-push app-conf result)))


(defn fetch-and-pre-assoc
  "Fetches the children of the current api from the server. Wraps the
  children with the supplied keyword tag, zippers the zml and returns
  a modified app-conf with the zipper assoc'ed using the supplied
  keyword."
  [k ktag app-conf]
  (assoc app-conf
         k
         (zip/xml-zip
          (apply xml/element
                 ktag nil (zip/children
                           (mhttp/fetch-all app-conf
                                            identity))))))

(defn local-locs
  "Lazy seq of local el-locs for the current api."
  [{:as app-conf
    {:keys [local-path api-files]} :api}]
  (map #(mxml/to-zip
         (do
           (log/infof "\tFile: %s" (.toString ^File %))
           (slurp %)))
       (api-files (local-path app-conf))))

(defn remote-locs
  "Seq of remote el-locs for the current api. Could be lazy or not
  depending on the implementation of find-elements."
  [{:as app-conf
    {:keys [find-elements] :as api} :api}]
  (mhttp/fetch-all app-conf find-elements))

(defn process
  "Prints the message and processes the el-locs via the action."
  [{:as app-conf
    {:keys [pre-node-action]} :api} msg el-locs action]
  (log/info msg)
  (loop [app-conf app-conf
         el-locs el-locs]
    (if-let [el-loc (first el-locs)]
      (recur
       (-> app-conf
           (assoc :el-loc el-loc)
           (pre-node-action)
           action)
       (rest el-locs))
      app-conf)))

(defn download
  "Serializes all xml found at the api rest-path to the filesystem using the
  supplied config. Returns a (potentially) updated app-conf with
  details about the fetched apis."
  [{:as app-conf
    {:keys [local-path transformer] :as api} :api}]
  (process
   app-conf
   (str "Downloading from " (mhttp/api-url app-conf) " to " (local-path app-conf))
   (remote-locs app-conf)
   mxml/serialize-node))

(defn upload
  "Takes the current app-conf with the current api and finds associated
  files within the target directory. The files in the specified path
  directory are each read and handed to upload-node to push to Mirth."
  [{:as app-conf
    {:keys [local-path rest-path transformer] :as api} :api}]

  (process
   app-conf
   (str "Uploading from " (local-path app-conf) " to " (rest-path api))
   (local-locs app-conf)
   upload-node))
