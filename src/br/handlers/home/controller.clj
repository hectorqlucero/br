(ns br.handlers.home.controller
  (:require
   [buddy.hashers :as hashers]
   [clojure.string :as st]
   [br.i18n.core :as i18n]
   [br.handlers.home.model :refer [get-user get-users update-password get-featured-properties get-property-by-id get-estados get-municipios-by-estado]]
   [br.handlers.home.view :refer [change-password-view home-view
                                  main-view property-detail-view]]
   [br.layout :refer [application error-404]]
   [br.models.util :refer [get-session-id json-response]]
   [ring.util.response :refer [redirect]]))

(defn main
  [request]
  (let [title "Home"
        ok (get-session-id request)
        js nil
        params (:params request)]
    (try
      (let [estado-param (or (get params "estado") (get params :estado))
            municipio-param (or (get params "municipio") (get params :municipio))
            estado-id (when (and estado-param (not (clojure.string/blank? (str estado-param)))) (try (Integer/parseInt (str estado-param)) (catch Exception _ nil)))
            municipio-id (when (and municipio-param (not (clojure.string/blank? (str municipio-param)))) (try (Integer/parseInt (str municipio-param)) (catch Exception _ nil)))
            properties (get-featured-properties nil {:estado-id estado-id :municipio-id municipio-id})
            estados (get-estados)
            municipios (get-municipios-by-estado estado-id)
            content (home-view properties estados municipios {:selected-estado estado-id :selected-municipio municipio-id :raw-params params})]
        (application request title ok js content))
      (catch Exception e
        (let [err (str "EXCEPTION: " (.getMessage e) "\n" (pr-str e))]
          (application request title ok js [:div [:h3 "Error rendering home"] [:pre err]]))))))

(defn municipios-api
  [request]
  (let [params (:params request)
        estado-param (or (get params "estado") (get params :estado))
        estado-id (when (and estado-param (not (clojure.string/blank? (str estado-param)))) (try (Integer/parseInt (str estado-param)) (catch Exception _ nil)))
        municipios (if estado-id (get-municipios-by-estado estado-id) [])]
    (json-response {:ok true :municipios municipios})))

(defn property-detail
  [request]
  (let [id (-> request :params :id)
        title "Propiedad"
        ok (get-session-id request)
        js nil
        property (get-property-by-id id)
        content (if property (property-detail-view property) (error-404 "Propiedad no encontrada" "/"))]
    (application request title ok js content)))

(defn login
  [request]
  (let [title (i18n/tr request :auth/login)
        ok (get-session-id request)
        js nil
        content (main-view title)]
    (application request title ok js content)))

(defn login-user
  [{:keys [params session]}]
  (let [title (i18n/tr params :auth/login)
        username (:username params)
        password (:password params)
        row (first (get-user username))
        active (:active row)
        return-path "/"
        back-msg (i18n/tr params :common/back)
        error-general (i18n/tr params :error/general)
        content-error-general [:p error-general [:a {:href return-path} back-msg]]
        error-forbidden (i18n/tr params :auth/invalid-credentials)
        content-error-forbidden [:p error-forbidden [:a {:href return-path} back-msg]]]
    (if (= active "T")
      (if (hashers/check password (:password row))
        (-> (redirect "/")
            (assoc :session (assoc session :user_id (:id row))))
        (application params title 0 nil content-error-general))
      (application params title 0 nil content-error-forbidden))))

(defn change-password
  [request]
  (let [title (i18n/tr request :auth/change-password)
        ok (get-session-id request)
        js nil
        content (change-password-view title)]
    (application request title ok js content)))

(defn process-password
  [{:keys [params] :as request}]
  (let [title (i18n/tr request :auth/login)
        username (:email params)
        password (:password params)
        row (first (get-user username))
        result (or (update-password username (hashers/derive password)) 0)
        return-path "/home/login"
        back-msg (i18n/tr request :common/back)
        error-general (i18n/tr request :error/general)
        content-error-general [:p error-general [:a {:href return-path} back-msg]]
        error-not-found (i18n/tr request :error/not-found)
        content-error-not-found [:p error-not-found [:a {:href return-path} back-msg]]
        success-updated (i18n/tr request :success/updated)
        content-success [:p success-updated [:a {:href return-path} back-msg]]]
    (if (and row (= (:active row) "T"))
      (if (and password (not (st/blank? password)))
        (if (> result 0)
          (application request title 0 nil content-success)
          (application request title 0 nil content-error-general))
        (application request title 0 nil content-error-not-found))
      (application request title 0 nil content-error-general))))

(defn logoff-user
  [_]
  (-> (redirect "/")
      (assoc :session {})))

(comment
  (:username (first (get-users))))
