{:connections
 {  ;; --- Local SQLite Database (Great for development) ---
  :sqlite {:db-type   "sqlite"
           :db-class  "org.sqlite.JDBC"
           :db-name   "db/br.sqlite"}

  ;; --- Default connection used by the app ---
  ;; SQLite by default for easy prototyping, switch to MySQL/PostgreSQL for production
  :main :sqlite        ; Used for migrations (lein migrate)
  :default :sqlite     ; Used by the application
  :localdb :sqlite}    ; SQLite connection reference

 ;; --- Application Settings ---
 :uploads      "./uploads/br/"
 :site-name    "Bienes Raices Mexicali"
 :company-name "Ruiz Sofware Solutions"
 :port         3000
 :tz           "US/Pacific"
 :base-url     "http://localhost:3000/"
 :img-url      "http://localhost:3000/uploads/"
 :path         "/uploads/"

 ;; --- File Upload Settings ---
 :max-upload-mb 5
 :allowed-image-exts ["jpg" "jpeg" "png" "gif" "bmp" "webp"]
 :locale "es-MX"
 :date-format "dd/MM/yyyy"
 :currency "MXN"
 :currency-symbol "$"
 :decimal-separator "."
 :thousands-separator ","

 ;; --- Optional Email Configuration ---
 ;; Uncomment and configure if you need email functionality
 ;; :email-host   "smtp.example.com"
 ;; :email-user   "user@example.com"
 ;; :email-pwd    "Patito0257."
 ;; :email-port   587
 ;; :email-tls    true
 }
