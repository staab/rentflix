(ns rentflix.import
  (:require [clj-http.client :as client]
            [clojure.data.json :as json]
            [clojure.string :as s]
            [datomic.api :as d]
            [rentflix.conf :refer [tmdb-api-key]]
            [rentflix.db :as db]
            [rentflix.util :refer [in? has-keys? map-values abs]]))

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
        xxx #"^XXX:"
        shelf-id (parse-int (subs line 40 60))]
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
     :shelf-id shelf-id
     :num-copies (read-string (s/trim (subs line 60)))
     :year (get id-to-year shelf-id)}))

(defn item-imported?
  [item]
  (not (empty? (db/find-eids :title/shelfid (:shelf-id item) 1 0))))

(defn q-tmdb-uncached
  ([url params]
   ; Avoid getting throttled
   (Thread/sleep 1000)
   (clojure.pprint/pprint (str "API: getting " url))
   (let [res (client/get
                   (str "https://api.themoviedb.org/3/" url)
                   {:query-params (merge params {"api_key" tmdb-api-key})
                    :headers {:accept :json}})]
     (json/read-str (:body res))))
  ([url] (q-tmdb-uncached url {})))

(defonce q-tmdb (memoize q-tmdb-uncached))

(defn search-tmdb
  [item type]
  (let [title (:title item)
        results (get (q-tmdb (str "search/" type) {"query" title}) "results")]
    (filter #(has-keys? % ["id", "title" "release_date"]) results)))

(defn add-tmdb-movie-data
  [item]
  (let [url (str "movie/" (get item "id"))
        movie-data (q-tmdb url)
        year-data (get (q-tmdb (str url "/release_dates")) "results")
        keyword-data (get (q-tmdb (str url "/keywords")) "keywords")
        release-dates (map
                        #(get (first (get % "release_dates")) "release_date")
                        year-data)]
    {:tmdb-type "movie"
     :tmdb-id (get item "id")
     :title (get item "title")
     :adult (get movie-data "adult")
     :years (map #(parse-int (subs % 0 4)) release-dates)
     :genres (map #(get % "name") (get movie-data "genres"))
     :tagline (get movie-data "tagline")
     :overview (get movie-data "overview")
     :keywords (map #(get % "name") keyword-data)}))

(defn add-tmdb-tv-data
  [item]
  (let [url (str "tv/" (get item "id"))
        tv-data (q-tmdb url)
        keyword-data (get (q-tmdb (str url "/keywords")) "keywords")]
    {:tmdb-type "tv"
     :tmdb-id (get item "id")
     :title (get item "title")
     :adult false
     :years (map #(parse-int (subs (get % "air_date") 0 4)) (get tv-data "seasons"))
     :genres (map #(get % "name") (get tv-data "genres"))
     :overview (get tv-data "overview")
     :keywords (map #(get % "name") keyword-data)}))

(defn find-tmdb-match
  [item]
  (let [movies (map add-tmdb-movie-data (search-tmdb item "movie"))
        tv (map add-tmdb-tv-data (search-tmdb item "tv"))
        sub-year (fn [year]
                   (abs (- (:year item) year)))
        add-year-delta (fn [record]
                         (assoc
                           record
                           :year-delta
                           (apply min (map sub-year (:years record)))))
        data (map add-year-delta
                  (filter #(> (count (:years %)) 0)
                          (concat movies tv)))
        matches (reduce
                 (fn [[min-delta matches] record]
                   (let [record-delta (:year-delta record)
                         matches (if
                                   (<= record-delta min-delta)
                                   (conj matches record)
                                   matches)
                         min-delta (min min-delta record-delta)]
                     [min-delta matches]))
                 [100 []]
                 data)]
    (first (second matches))))

(defn add-tmdb-data
  [item]
  (let [match (find-tmdb-match item)]
    (merge item match)))

      :tmdb-id
      :date
      :tagline
      :overview
      :keywords
      :genres
      :adult

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
     ; todo:
     :title/date (:date item)
     :title/tagline (:tagline item)
     :title/overview (:overview item)
     :title/keywords (:keywords item)
     :title/genres (:genres item)
     :title/adult (:adult item)}

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
    (map add-tmdb-data)
    (save-to-db)))

(defn import-movlist
  []
  (map
    import-movies
    (partition 10 (read-lines "resources/movlist.raw"))))

; (clojure.pprint/pprint (do (use 'rentflix.import :reload) (import-movlist)))
; TODO: Insert into the database