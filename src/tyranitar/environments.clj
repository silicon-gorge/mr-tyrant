(ns tyranitar.environments
  (:require [clojure.tools.logging :refer [warn]]
            [overtone.at-at :as at]
            [tyranitar.onix :as onix]))

(def ^:private pool
  (atom nil))

(def environments-atom
  (atom nil))

(defn create-pool
  []
  (when-not @pool
    (reset! pool (at/mk-pool :cpu-count 1))))

(defn environments
  []
  @environments-atom)

(defn map-by-name-kw
  [list]
  (apply merge (map (fn [v] {(keyword (:name v)) v}) list)))

(defn default-environments
  []
  (map-by-name-kw (filter #(get-in % [:metadata :default]) (vals (environments)))))

(defn update-environments
  []
  (try
    (when-let [environments (map-by-name-kw (map onix/environment (onix/environments)))]
      (reset! environments-atom environments))
    (catch Exception e
      (warn e "Failed to update environments"))))

(defn init
  []
  (create-pool)
  (at/interspaced (* 1000 60 30) update-environments @pool :initial-delay 0))
