(ns rentflix.util)

(defn map-keys
  "Alters keys according to given function"
  [f item]
  (into {} (for [[k v] item] [(f k) v])))
