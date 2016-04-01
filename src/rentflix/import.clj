(ns rentflix.import
  (:require [rentflix.util :refer [in?]]))

(defn read-lines
  "Reads n-lines of given filename"
  ([filename]
   (with-open [reader (clojure.java.io/reader filename)]
     (doall (line-seq reader))))
  ([filename start offset]
   (with-open [reader (clojure.java.io/reader filename)]
     (doall (take offset (drop start (line-seq reader)))))))

(defn trim-line
  "Replace null bytes and trim"
  [line]
  (clojure.string/replace (clojure.string/trim line) (str (char 0)) ""))

(def trash-values
  [""
   "# of"
   "MOVIE REPORT"
   "3D BLURAY"
   "Continued on next page..."])

(defn trash-line
  [line]
  (or
    (in? trash-values line)
    (.contains line "==========")
    (.contains line "----------")
    (.contains line "HOWARD HUGHES VIDEO")
    (.contains line "Listing of All Movies")
    (.contains line "Cpies")
    (>= 60 (count line))))

(defn line->item
  [line]
  (let [raw-title (clojure.string/trim (subs line 0 40))
        dvd-re #"-DV?D?$"
        blu-re #"-BL?U?R?A?Y?$"]
    {:title (->
              raw-title
              (clojure.string/replace dvd-re "")
              (clojure.string/replace blu-re ""))
     :format (or
               (if (re-find dvd-re raw-title) :dvd)
               (if (re-find blu-re raw-title) :bluray))
     :shelf-id (read-string (clojure.string/trim (subs line 40 60)))
     :num-copies (read-string (clojure.string/trim (subs line 60)))}))

(defn import-movlist
  []
  (->>
    (read-lines "resources/movlist.raw" 0 100)
    (map trim-line)
    (filter #(not (trash-line %)))
    (map line->item)))

; (clojure.pprint/pprint (do (use 'rentflix.import :reload) (import-movlist)))