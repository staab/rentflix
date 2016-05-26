(ns rentflix.import
  (:require [clj-http.client :as client]
            [clojure.data.json :as json]
            [clojure.string :as s]
            [datomic.api :as d]
            [rentflix.conf :refer [tmdb-api-key]]
            [rentflix.db :as db]
            [rentflix.util :refer [in? has-keys? map-values]]))

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
  (try (. Integer parseInt (s/trim value)) (catch Exception e nil)))

(def trash-values
  [""
   "Year"
   "# of"
   "MOVIE REPORT"
   "3D BLURAY"
   "Continued on next page..."])

(defn trash-line?
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
    (filter (complement trash-line?))
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
        special-dvd-re #"-SPECI?A?L? ?E?D?I?T?I?O?N? ?D?V?D?$"
        dvd-re #"-DV?D?$"
        blu-re #"-BL?U?R?A?Y?$"
        is-3d-re #"\(3D\)"
        ultimate-ed #"-UT?LT?I?M?A?T?E? ED\.?"
        year-re #"-?\(?\d{4}\)?$"
        sped-ed #"\(SPED ED\)"
        xxx #"^XXX:"]
    {:title (->
              raw-title
              (s/replace special-dvd-re "")
              (s/replace dvd-re "")
              (s/replace blu-re "")
              (s/replace is-3d-re "")
              (s/replace ultimate-ed "")
              (s/replace year-re "")
              (s/replace sped-ed "")
              (s/replace xxx ""))
     :raw-title raw-title
     :format (or
               (if (re-find dvd-re raw-title) "dvd")
               (if (re-find blu-re raw-title) "bluray"))
     :is-3d (boolean (re-find is-3d-re raw-title))
     :shelf-id (parse-int (subs line 40 60))
     :num-copies (read-string (s/trim (subs line 60)))}))

(defn item-imported?
  [item]
  (not (empty? (db/find-eids :title/shelfid (:shelf-id item) 1 0))))

(defn search-tmdb
  [url, title]
  ; Avoid getting throttled
  (Thread/sleep 1000)
  (let [res (client/get
                  (str "https://api.themoviedb.org/3/" url)
                  {:query-params {"api_key" tmdb-api-key "query" title}
                   :headers {:accept :json}})
        results (get (json/read-str (:body res)) "results")]
    (->> results
      (map #(select-keys % ["id", "title" "release_date"]))
      (filter #(has-keys? % ["id", "title" "release_date"])))))

(defn get-tmdb-matches
  [item]
  (let [title (:title item)
        year (get id-to-year (:shelf-id item))
        results (concat
                  (search-tmdb "search/movie" title)
                  (search-tmdb "search/tv" title))
        ; Filter down results to ones with close-ish release dates.
        ; TMDB and HH have conflicting release dates, but they're usually
        ; within a year
        timely (if year
                 (->>
                   results
                   (filter
                     (fn
                       [r]
                       (let [r-year (try
                                      (subs (get r "release_date") 0 4)
                                      (catch Exception e "0"))]
                         (< (Math/abs (- year (parse-int r-year))) 2)))))
                 results)
        matches (if (= 0 (count timely)) results timely)]
    (println
      (str "-- Got " (count matches) " matches for " (:raw-title item) ":"))
    (clojure.pprint/pprint matches)
    ; Try to get timely ones unless we filtered them all out
    (map #(get % "id") matches)))

(defn add-tmdb-matches
  [item]
  (let [matches (get-tmdb-matches item)]
    (merge item {:tmdb-id (first matches) :matches matches})))

(defn item->title-txn
  [item]
  (->>
    {:type :type/title
     :title/name (:title item)
     :title/rawname (:raw-title item)
     :title/shelfid (:shelf-id item)
     :title/tmdbid (:tmdb-id item)
     :title/format (:format item)
     :title/is3d (:is-3d item)
     :title/matches (:matches item)}
    ; Remove nil values
    (filter (fn [[k v]] v))
    ; Add the temp id
    (concat [[:db/id (:tempid item)]])
    (into {})))

(defn save-to-db
  [items]
  (let [tempid-items (map #(assoc % :tempid (d/tempid :db.part/user)) items)
        title-txns (reduce #(conj %1 (item->title-txn %2)) [] tempid-items)
        title-txn-result @(d/transact (db/get-conn) title-txns)
        media-txns (apply
                     concat
                     (map
                       (fn
                         [item]
                         (for [i (range (:num-copies item))]
                           {:db/id (d/tempid :db.part/user)
                            :media/title
                            (d/resolve-tempid
                              (d/db (db/get-conn))
                              (:tempids title-txn-result)
                              (:tempid item))}))
                       tempid-items))
        media-txn-result @(d/transact (db/get-conn) media-txns)]
    items))

(defn import-movies
  [items]
  (->>
    items
    (map trim-line)
    (filter (complement trash-line?))
    (map line->item)
    (filter #(:shelf-id %))
    (filter (complement item-imported?))
    (map add-tmdb-matches)
    (save-to-db)))

(defn import-movlist
  []
  (map
    import-movies
    (partition 10 (read-lines "resources/movlist.raw"))))

; (clojure.pprint/pprint (do (use 'rentflix.import :reload) (import-movlist)))
; TODO: Insert into the database