(ns tyrant.environments
  (:require [clojure.tools.logging :refer [warn]]
            [ninjakoala.ttlr :as ttlr]
            [tyrant.lister :as lister]))

(defn environments
  []
  (ttlr/state :environments))

(defn map-by-name-kw
  [list]
  (apply merge (map (fn [v] {(keyword (:name v)) v}) list)))

(defn default-environments
  []
  (map-by-name-kw (filter #(get-in % [:metadata :create-repo]) (vals (environments)))))

(defn load-environments
  []
  (map-by-name-kw (map lister/environment (lister/environments))))

(defn environments-healthy?
  []
  (some? (environments)))

(defn init
  []
  (ttlr/schedule :environments load-environments (* 1000 60 20) (load-environments)))
