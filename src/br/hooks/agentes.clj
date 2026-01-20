(ns br.hooks.agentes
  (:require [clojure.string :as str]))

(defn nombre-completo
  "Agrega nombre completo del agente"
  [rows params]
  (mapv (fn [row]
          (let [nombre (str/trim (str (:nombre row) " "
                                      (:apellido_paterno row) " "
                                      (:apellido_materno row)))]
            (assoc row :nombre_completo nombre)))
        rows))
