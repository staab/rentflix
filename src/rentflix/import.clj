(ns rentflix.import
  (:require [rentflix.util :refer [in?]]
            [rentflix.conf :refer [tmdb-api-key]]
            [clj-http.client :as client]
            [clojure.data.json :as json]
            [clojure.string :as s]))

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
  (s/replace (s/trim line) (str (char 0)) ""))

(defn parse-int
  [value]
  (try (read-string (s/trim value)) (catch Exception e nil)))

(def trash-values
  [""
   "Year"
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
    (.contains line "Shelf-ID")
    (or (< (count line) 60) (> (count line) 61))))

(defonce id-to-year
  (->>
    (read-lines "resources/movlist-with-date.raw")
    (map trim-line)
    (filter #(not (trash-line %)))
    (map (fn
           [line]
           (let [shelf-id (subs line 40 56)
                 year (subs line 56)]
           ; Try to read the values, return nil if we fail
           {:shelf-id (parse-int shelf-id)
            :year (parse-int year)})))
    ; Filter out items with invalid values
    (filter #(and (:shelf-id %) (:year %)))
    ; Turn it into a straight up map from shelf id to year
    (map (fn [line] {(:shelf-id line) (:year line)}))
    (apply merge)))

(defn line->item
  [line]
  (let [raw-title (s/trim (subs line 0 40))
        dvd-re #"-DV?D?$"
        blu-re #"-BL?U?R?A?Y?$"]
    {:title (->
              raw-title
              (s/replace dvd-re "")
              (s/replace blu-re ""))
     :format (or
               (if (re-find dvd-re raw-title) :dvd)
               (if (re-find blu-re raw-title) :bluray))
     :shelf-id (read-string (s/trim (subs line 40 60)))
     :num-copies (read-string (s/trim (subs line 60)))}))

(defn search-tmdb
  [url, title]
  ; Avoid getting throttled
  (Thread/sleep 1000)
  (let [res (client/get
                  (str "https://api.themoviedb.org/3/" url)
                  {:query-params {"api_key" tmdb-api-key "query" title}
                   :headers {:accept :json}})
        results (get (json/read-str (:body res)) "results")]
    (map #(select-keys % ["id", "title" "release_date"]) results)))

(defn get-tmdb-id
  [item]
  (let [title (:title item)
        year (get id-to-year (:shelf-id item))
        results (concat
                  (search-tmdb "search/movie" title)
                  (search-tmdb "search/tv" title))
        ; Filter down results to ones with close-ish release dates.
        ; TMDB and HH have conflicting release dates, but they're usually
        ; within a year
        timely (->>
                 results
                 (filter
                   (fn
                     [r]
                     (let [r-year (try
                                    (subs (get r "release_date") 0 4)
                                    (catch Exception e "0"))]
                       (< (Math/abs (- year (parse-int r-year))) 2))))
                 ) ; TODO: order among results with identical titles by year
        ; Try to get a timely one unless we filtered them all out
        result (if (= 0 (count timely)) (first results) (first timely))]
    (get (first results) "id")))

(defn add-tmdb-id
  [item]
  (assoc item :tmdb-id (get-tmdb-id item)))

(defn import-movlist
  []
  (->>
    (read-lines "resources/movlist.raw" 0 200)
    (map trim-line)
    (filter #(not (trash-line %)))
    (map line->item)
    (map add-tmdb-id)))

; (clojure.pprint/pprint (do (use 'rentflix.import :reload) (import-movlist)))
; TODO: get release date to compare and select