(ns rentflix.util)

(defn in?
  "true if coll contains el (http://stackoverflow.com/a/3249777/1467342)"
  [coll el]
  (some #(= el %) coll))

(defn map-keys
  "Alters keys according to given function"
  [f item]
  (into {} (for [[k v] item] [(f k) v])))

(defn has-keys? [m keys]
  (apply = (map count [keys (select-keys m keys)])))