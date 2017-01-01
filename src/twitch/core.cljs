(ns twichtv.core
  (:require
   [cljs-http.client :as http]
   [clojure.string :as s]
   [reagent.core :as reagent :refer [atom]]
   [cljs.core.async :refer [<! chan sliding-buffer put! close! timeout]])
  (:require-macros
   [cljs.core.async.macros :refer [go-loop go]]))

(enable-console-print!)

(defonce active-page (atom :all))
(defonce temp-active-page (atom nil))

(defonce client-id "gehcd34l5ilaqlmkqct6ul54138t09")
(def api-streams "https://api.twitch.tv/kraken/streams")
(def api-users "https://api.twitch.tv/kraken/users")
(def test-users ["freecodecamp"  "storbeck"  "terakilobyte"  "habathcx" "RobotCaleb" "thomasballinger" "noobs2ninjas" "beohoff" "rikurikuriku" "chaio7891" "summit1g" "xchocobars"])
(defonce live-streams (atom {}))
(defonce user-profs (atom {}))

(defn active? [state]
  (or (= @active-page state)
      (= @temp-active-page state)))

(defn get-live-streams []
  (println "get-streams")
  (go
    (let [resp (<! (http/get api-streams
                             {:headers {"client-id" client-id}
                              :with-credentials? false
                              :query-params {"channel" (s/join "," test-users)}}))]
      (doseq [stream (-> resp :body :streams)]
        (swap! live-streams assoc (-> stream :channel :name) (stream :channel))))))



(defn live? [user]
  (some (partial = user) (keys @live-streams)))

(defn get-all-users []
  (go
    (doseq [user test-users]
      (let [resp (<! (http/get (str api-users "/" user)
                               {:headers {"client-id" client-id}
                                :with-credentials? false}))]
        (swap! user-profs assoc user (:body resp))))))

(defn streams-list []
  (fn [page all-profs]
    (let [profs (case page
                  :all @user-profs
                  :online (filter (comp live? first) all-profs)
                  :offline (filter (comp (complement live?) first) all-profs))
          profs (sort-by first profs)]
      [:div.streams
       (doall
        (for [[user prof] profs
              :let [this-live? (live? user)]]
          ^{:key user}
          [:div.row.streams-holder
           [:div.col-lg-6.col-lg-offset-3.col-sm-10.col-sm-offset-1.streamers {:class (if this-live? "online" "offline")}
            [:a {:href (str "https://www.twitch.tv/" user) :target "_blank"}
             [:div.row
              [:div.col-md-4
               (when (prof :logo)
                 [:img.logo {:src (prof :logo)}])]
              [:div.col-md-2.user
               (prof :display_name)]
              [:div.col-md-6 {:style {:font-style "italic"}}
               (if this-live?
                 (get-in @live-streams [user :game])
                 "offline")]]]]]))])))

(defn my-app []
  [:div.container-fluid
   [:div.row
    [:div.col-lg-6.col-lg-offset-3.col-sm-10.col-sm-offset-1.title
     [:h1.title "twitch.tv streamers"]
     [:div.menu
      [:a {:href "javascript:;"}
       [:div#all {:class (if (active? :all) :active :inactive)
                  :on-click #(reset! active-page :all)
                  :on-mouse-over #(reset! temp-active-page :all)
                  :on-mouse-out #(reset! temp-active-page nil)}
        "all"]]
      [:a {:href "javascript:;"}
       [:div#online {:class (if (active? :online) :active :inactive)
                     :on-click #(reset! active-page :online)
                     :on-mouse-over #(reset! temp-active-page :online)
                     :on-mouse-out #(reset! temp-active-page nil)}
        "online"]]
      [:a {:href "javascript:;"}
       [:div#offline {:class (if (active? :offline) :active :inactive)
                      :on-click #(reset! active-page :offline)
                      :on-mouse-over #(reset! temp-active-page :offline)
                      :on-mouse-out #(reset! temp-active-page nil)}
        "offline"]]]]]
   [streams-list @active-page @user-profs]])

(defn main []
  (get-live-streams) 
  (get-all-users)
  (reagent/render [#'my-app] (.getElementById js/document "app")))

(main)

