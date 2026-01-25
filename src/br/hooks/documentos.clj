(ns br.hooks.documentos)

;; =============================================================================
;; AFTER-LOAD Crear anchor para ver el pdf
;; =============================================================================

(defn after-load
  [rows params]
  (let [rows (map #(assoc %
                          :archivo_url (str "<a href=\"" (:archivo_url %) "\"  target=\"_blank\">Abrir Archivo</a>")) rows)]
    rows))

