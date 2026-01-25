(ns br.handlers.home.view
  (:require
   [br.models.form :refer [login-form password-form]]
   [br.models.crud :refer [config get-config]]
   [clojure.java.io :as io]
   [clojure.string :as str]))

;; Local CSS/JS used by this view. Keep small and theme-aware using Bootstrap CSS variables.
(def ^:private view-styles
  "/* Hero */
  .hero{padding:8px 0;}
  @media (max-width:768px){ .hero{padding:6px 0;} .hero h1{font-size:1rem;} }
  .hero h1{font-size:1.1rem; margin-bottom:0.25rem;}
  .hero .lead{margin-bottom:0.35rem;}

  /* Carousel / property image sizes */
  .property-img{height:250px; object-fit:cover;}
  @media (min-width:992px){ .property-img{height:300px;} }
  @media (min-width:1200px){ .property-img{height:360px;} }

  /* Detail view larger image */
  .detail-img{height:420px; object-fit:cover;}
  @media (min-width:992px){ .detail-img{height:560px;} }

  /* Thumbnails */
  .thumb-item{display:inline-block;}
  .thumb-img{width:64px; height:48px; object-fit:cover; border-radius:4px;}
  @media (max-width:575.98px){ .thumb-img{width:48px; height:36px;} }
  .thumb-item img{border:2px solid transparent; transition: box-shadow .12s, border-color .12s;}
.thumb-item.active img{border-color: var(--bs-primary, #0d6efd); box-shadow: 0 0 6px rgba(var(--bs-primary-rgb,13,110,253), .35);} 
  /* Card transition */
  .property-card-transition{transition: transform 0.3s ease, box-shadow 0.3s ease;} 

  /* Global loading overlay fade-in/out */
  #global-loading-overlay{
    transition: opacity .18s ease, transform .18s ease, backdrop-filter .18s ease;
    opacity: 0;
    pointer-events: none;
    transform: scale(.99);
    backdrop-filter: blur(0px);
    -webkit-backdrop-filter: blur(0px);
  }
  #global-loading-overlay.show{
    opacity: 1;
    pointer-events: auto;
    transform: scale(1);
    backdrop-filter: blur(2px);
    -webkit-backdrop-filter: blur(2px);
  } ")

(def ^:private thumb-sync-js
  "(function(){ var carousels = Array.from(document.querySelectorAll('.carousel')); carousels.forEach(function(carouselEl){ if(carouselEl.getAttribute('data-bs-ride')!=='carousel') return; var id = carouselEl.id; var thumbs = Array.from(document.querySelectorAll('.thumb-item')).filter(function(t){ return t.getAttribute('data-bs-target') === '#'+id; }); function setActive(idx){ thumbs.forEach(function(t){ if(t.getAttribute('data-bs-slide-to')==String(idx)) t.classList.add('active'); else t.classList.remove('active'); }); } carouselEl.addEventListener('slid.bs.carousel', function(e){ setActive(e.to); }); var initial = carouselEl.querySelector('.carousel-item.active'); if(initial){ var idx = Array.prototype.indexOf.call(carouselEl.querySelectorAll('.carousel-item'), initial); setActive(idx); } }); })()")

(def ^:private municipios-js
  "(function(){ window.onEstadoChange = function(el){ var estadoVal = el.value; var form = el.form; var municipioSelect = form.querySelector('select[name=\"municipio\"]'); var spinner = form.querySelector('[data-spinner=\"municipio\"]'); var overlay = document.getElementById('global-loading-overlay'); var prevSel = municipioSelect.getAttribute('data-selected') || municipioSelect.value || ''; if(!estadoVal){ if(spinner) spinner.classList.add('d-none'); if(overlay){ overlay.classList.remove('show'); setTimeout(function(){ overlay.classList.add('d-none'); }, 220); } municipioSelect.innerHTML = '<option value=\"\">Todos los Municipios</option>'; municipioSelect.value = prevSel; form.submit(); return; } // Show inline spinner + overlay to give clear feedback
 if(spinner) spinner.classList.remove('d-none'); if(overlay){ overlay.classList.remove('d-none'); requestAnimationFrame(function(){ overlay.classList.add('show'); }); } municipioSelect.disabled = true; municipioSelect.innerHTML = '<option value=\"\">Cargando...</option>'; // delay the fetch briefly so the browser can paint the spinner/overlay
 setTimeout(function(){ fetch('/api/municipios?estado='+encodeURIComponent(estadoVal)).then(function(r){ return r.json(); }).then(function(data){ var opts = ['<option value=\"\">Todos los Municipios</option>']; var found = false; if (data && data.ok && Array.isArray(data.municipios)) { data.municipios.forEach(function(m){ if(String(m.id) === String(prevSel)){ opts.push('<option value=\"'+m.id+'\" selected>'+m.nombre+'</option>'); found = true; } else { opts.push('<option value=\"'+m.id+'\">'+m.nombre+'</option>'); } }); } municipioSelect.innerHTML = opts.join(''); if(!found && prevSel){ try{ municipioSelect.value = prevSel; }catch(e){} } municipioSelect.disabled = false; if(spinner) spinner.classList.add('d-none'); // submit to apply the filter and navigate; overlay remains until browser navigates
 form.submit(); }).catch(function(){ municipioSelect.disabled = false; if(spinner) spinner.classList.add('d-none'); if(overlay){ overlay.classList.remove('show'); setTimeout(function(){ overlay.classList.add('d-none'); }, 220); } // on error, hide overlay and allow user interaction
 }); }, 40); }; })()")

;; Image attribute helpers
(defn- common-img-attrs [src alt]
  {:src src
   :alt alt
   :loading "lazy"
   :onerror "this.onerror=null;this.src='/images/placeholder_property.svg';"})

(defn- property-img-attrs [src alt]
  (merge (common-img-attrs src alt) {:class "property-img d-block w-100"}))

(defn- thumb-img-attrs [src alt]
  (merge (common-img-attrs src alt) {:class "thumb-img rounded"}))

(defn- detail-img-attrs [src alt]
  (merge (common-img-attrs src alt) {:class "detail-img d-block w-100"}))

(defn format-currency
  "Format price as currency"
  [amount currency]
  (when amount
    (str "$" (format "%,.0f" (float amount)) " " currency)))

(defn format-property-address
  "Format full property address"
  [property]
  (clojure.string/join ", "
                       (filter identity
                               [(when (:calle property) (:calle property))
                                (when (:numero_exterior property) (:numero_exterior property))
                                (when (:colonia_nombre property) (:colonia_nombre property))
                                (when (:municipio_nombre property) (:municipio_nombre property))
                                (when (:estado_nombre property) (:estado_nombre property))
                                (when (:codigo_postal property) (:codigo_postal property))])))

;; Componente para mostrar la alerta de contacto
(defn contact-alert [property]
  (let [agent (or (:agente_nombre property) "No asignado")
        tel (or (:agente_telefono property) "")
        cel (or (:agente_celular property) "")]
    (str "alert('Contacto: \n"
         "Agente: " agent "\\n"
         (when (seq tel) (str "Teléfono: " tel "\\n"))
         (when (seq cel) (str "Celular: " cel "\\n"))
         "')")))

(defn property-carousel
  "Render a bootstrap carousel for property photos (with thumbnails). Assumes photos is a vector of maps with :url and :descripcion"
  [id photos]
  (let [cid (str "prop-carousel-" id)
        items (map-indexed (fn [idx img]
                             ^{:key (str id "-img-" idx)}
                             [:div.carousel-item {:class (when (zero? idx) "active")}
                              [:img (property-img-attrs (:url img) (or (:descripcion img) "Foto"))]]) photos)
        thumbs (map-indexed (fn [idx img]
                              ^{:key (str id "-thumb-" idx)}
                              [:a.thumb-item {:href (str "#" cid) :class "thumb-item" :data-bs-target (str "#" cid) :data-bs-slide-to idx :aria-current (when (zero? idx) "true") :data-slide-to idx}
                               [:img (thumb-img-attrs (:url img) (or (:descripcion img) "Miniatura"))]]) photos)]
    [:div
     [:div.carousel.slide {:id cid :data-bs-ride "carousel"}
      [:div.carousel-inner items]
      (when (> (count photos) 1)
        [:div.carousel-controls
         [:button.carousel-control-prev {:type "button" :data-bs-target (str "#" cid) :data-bs-slide "prev"}
          [:span.carousel-control-prev-icon {:aria-hidden "true"}]
          [:span.visually-hidden "Previous"]]
         [:button.carousel-control-next {:type "button" :data-bs-target (str "#" cid) :data-bs-slide "next"}
          [:span.carousel-control-next-icon {:aria-hidden "true"}]
          [:span.visually-hidden "Next"]]])]
     (when (> (count photos) 1)
       [:div.mt-2.d-flex.gap-2.flex-wrap {:id (str cid "-thumbs")}
        thumbs])]))

(defn- property-title
  "Render the property title with link." [property]
  [:h5.card-title.fw-bold.text-primary.mb-2
   [:a.text-decoration-none.text-dark {:href (str "/property/" (:id property))} (or (:titulo property) "Propiedad Exclusiva")]])
(defn- property-prices
  "Render sale / rent pricing for a property."
  [property]
  (let [sale-price (:precio_venta property)
        rent-price (:precio_renta property)
        currency (:moneda property)]
    (cond-> nil
      sale-price (conj [:div.text-success.fw-bold.mb-1 (format-currency sale-price currency)])
      rent-price (conj [:div.text-info.fw-bold (format-currency rent-price currency) [:small.text-muted.ms-1 "/mes"]]))))

(defn- property-badges [property]
  [:div.mt-2.mb-2
   (when (= "T" (:destacada property))
     [:span.badge.bg-danger.me-2 "Destacada"])
   (when (:recamaras property)
     [:span.badge.bg-primary.me-1 (str (:recamaras property) " Rec")])])

(defn- property-card-footer [property]
  [:div.d-flex.align-items-center.justify-content-between.mt-3
   [:div.small.text-muted (or (:agente_nombre property) "Agente Disponible")]
   [:a.btn.btn-sm.btn-primary {:href (str "/property/" (:id property))} "Ver Detalles"]])

(defn property-card
  "Render a single property card with carousel and summary"
  [property]
  [:div.card.h-100.shadow-sm.border-0.rounded-3.overflow-hidden
   {:class "property-card-transition"}

   ;; Carousel area
   [:div.card-img-top.p-0
    (if (seq (:photos property))
      (property-carousel (:id property) (:photos property))
      [:div.p-0
       [:img (property-img-attrs "/images/placeholder_property.svg" "Imagen placeholder")]])]
   [:div.card-body.p-3
    (property-title property)
    [:p.card-text.text-muted.small.mb-3
     [:i.bi.bi-geo-alt-fill.text-primary.me-2]
     (format-property-address property)]

    (property-prices property)

    (property-badges property)

    (property-card-footer property)]])

(defn site-footer []
  [:footer
   [:div.container
    [:div.row
     [:div.col
      [:p.small (or (:site-name config) "Bienes Raices Mexicali")]]]]])

(defn site-footer-main []
  ;; Footer bar anchored to bottom; inner container uses theme background so it's the same width as property content
  [:footer.site-footer
   [:div.container-fluid.py-1 {:class "bg-primary text-white shadow-sm"}
    [:div.row.align-items-center
     ;; Left: site name + experience + contact
     [:div.col-md-6
      [:h6.fw-bold.mb-0 (or (:site-name config) "Bienes Raices Mexicali")]
      [:p.small.mb-0 (or (:site-experience config) "Más de 15 años ayudando a familias a encontrar su hogar ideal")]
      (when (:contact-phone config)
        [:p.small.mb-0.mt-1 [:i.bi.bi-telephone-fill.me-2] [:a.link-light {:href (str "tel:" (:contact-phone config))} (:contact-phone config)]])
      (when (:contact-email config)
        [:p.small.mb-0 [:i.bi.bi-envelope-fill.me-2] [:a.link-light {:href (str "mailto:" (:contact-email config))} (:contact-email config)]])]

     ;; Right: quick links + social
     [:div.col-md-6.text-md-end
      [:div.d-inline-block.me-3
       [:a.link-light.me-2 {:href "/"} "Inicio"]
       [:a.link-light {:href "#properties"} "Propiedades"]]
      (when (seq (:facebook-url config))
        [:a.text-white.me-3 {:href (:facebook-url config) :title "Facebook" :target "_blank"}
         [:i.bi.bi-facebook.fs-5]])
      (when (seq (:instagram-url config))
        [:a.text-white.me-3 {:href (:instagram-url config) :title "Instagram" :target "_blank"}
         [:i.bi.bi-instagram.fs-5]])
      (when (seq (:whatsapp-url config))
        [:a.text-white {:href (:whatsapp-url config) :title "WhatsApp" :target "_blank"}
         [:i.bi.bi-whatsapp.fs-5]])]]

    ;; Copyright row (smaller, subdued)
    [:div.row.mt-1
     [:div.col-12.text-center
      [:small.text-white-50 (str "© " (.. (java.time.LocalDate/now) getYear) " " (:company-name config) " - All rights reserved")]]]]])

(defn home-view
  "Render the home page with a list of properties (expects properties vector)"
  [properties estados municipios selected]
  [:div
   ;; Hero
   [:section.container-fluid.text-white.hero.bg-gradient.bg-primary
    [:style view-styles]
    [:div.container.mb-2
     [:h1.display-6.fw-bold.mb-1 (or (:site-moto config) "Encuentra el Hogar de tus Sueños")]
     [:p.lead.mb-2.d-none.d-sm-block (or (:site-experience config) "Propiedades exclusivas en las mejores ubicaciones con más de 15 años de experiencia")]

     ;; Filter form
     [:form.row.g-2.mt-3 {:method "get" :action "/"}
      [:div.col-md-4
       [:select.form-select {:name "estado" :onchange "onEstadoChange(this)"}
        [:option {:value ""} "Todos los Estados"]
        (for [e estados]
          ^{:key (:id e)} [:option {:value (str (:id e)) :selected (when (= (:id e) (:selected-estado selected)) "selected")} (:nombre e)])]]
      [:div.col-md-4
       [:div.d-flex {:style "gap: .5rem; align-items:center;"}
        [:select.form-select {:name "municipio" :data-selected (str (or (:selected-municipio selected) ""))}
         [:option {:value ""} "Todos los Municipios"]
         (for [m municipios]
           ^{:key (:id m)} [:option {:value (str (:id m)) :selected (when (= (:id m) (:selected-municipio selected)) "selected")} (:nombre m)])]
        [:div.spinner-border.spinner-border-sm.ms-2.d-none {:role "status" :aria-hidden "true" :data-spinner "municipio"}
         [:span.visually-hidden "Cargando..."]]]]

      [:div.col-md-2.d-flex.align-items-center
       [:button.btn.btn-light {:type "submit"} "Filtrar"]]
      [:div.col-md-2.d-flex.align-items-center
       [:p.mb-0.small.text-white-50 (str (count properties) " propiedades")]]]]
    ;; Properties list
    [:section#properties.container-fluid.py-4
     [:div.row.g-4.mb-4
      (for [property properties]
        ^{:key (:id property)} [:div.col-lg-3.col-md-6.mb-3 (property-card property)])]]

    ;; Small spacer so footer sits right after content
    [:script thumb-sync-js]
    [:script municipios-js]

    ;; Global loading overlay shown when changing estado (prevents blank page feeling)
    [:div#global-loading-overlay.position-fixed.top-0.start-0.w-100.h-100.d-flex.align-items-center.justify-content-center.bg-dark.bg-opacity-50.d-none
     [:div.text-center
      [:div.spinner-border.spinner-border-lg.text-light {:role "status" :aria-hidden "true"}]
      [:div.visually-hidden "Cargando..."]]]

    [:div.mt-2]

    ;; Footer — compact, color-matched and matching container width
    (site-footer-main)
    ;; End of page
    ]])

(defn property-detail-view
  "Property detail page with carousel and details"
  [property]
  (let [photos (:photos property)]
    [:div.container.py-5
     [:div.row.g-4
      [:div.col-lg-8
       (if (seq photos)
         (property-carousel (:id property) photos)
         [:div.p-0
          [:img (detail-img-attrs "/images/placeholder_property.svg" "Imagen placeholder")]])]
      [:style view-styles]
      [:script thumb-sync-js]

      [:div.col-lg-4
       [:h2.fw-bold (or (:titulo property) "Propiedad")]
       (when-let [sale (:precio_venta property)]
         [:h4.text-success (format-currency sale (:moneda property))])
       (when-let [rent (:precio_renta property)]
         [:h5.text-info (str (format-currency rent (:moneda property)) " /mes")])

       [:div.mt-3
        [:h6.fw-semibold "Detalles"]
        [:ul.list-unstyled
         (when (:recamaras property) [:li (str "Recámaras: " (:recamaras property))])
         (when (:banos_completos property) [:li (str "Baños: " (:banos_completos property))])
         (when (:construccion_m2 property) [:li (str "Construcción: " (:construccion_m2 property) " m²")])
         (when (:terreno_m2 property) [:li (str "Terreno: " (:terreno_m2 property) " m²")])]]

       [:div.mt-3
        [:h6.fw-semibold "Agente"]
        [:p.small (or (:agente_nombre property) "Agente Disponible")]
        (when (:agente_telefono property)
          [:p.small [:i.bi.bi-telephone-fill.me-2] [:a.link-dark {:href (str "tel:" (:agente_telefono property)) :aria-label "Llamar al agente (tel)"} (:agente_telefono property)]])
        (when (:agente_celular property)
          [:p.small [:i.bi.bi-phone.me-2] [:a.link-dark {:href (str "tel:" (:agente_celular property)) :aria-label "Llamar al agente (cel)"} (:agente_celular property)]])
        (when (:agente_email property)
          [:p.small [:i.bi.bi-envelope-fill.me-2] [:a.link-dark {:href (str "mailto:" (:agente_email property)) :aria-label "Enviar correo al agente"} (:agente_email property)]])
        [:div.mt-3
         [:a.btn.btn-secondary {:href "#" :onclick "history.back(); return false;"} "Regresar a Propiedades"]]]]]]))

(defn main-view
  "This creates the login form and we are passing the title from controller"
  [title]
  (let [href "/home/login"]
    (login-form title href)))

(defn change-password-view
  [title]
  (password-form title))
