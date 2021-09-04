(ns athens.self-hosted.presence.events
  (:require
    [athens.self-hosted.presence.utils :as utils]
    [re-frame.core :as rf]))


(rf/reg-event-fx
  :presence/all-online
  (fn [_db [_ users]]
    {:fx [[:dispatch-n (mapv (fn [user-map]
                               [:presence/add-user user-map])
                             users)]]}))


;; TODO: what happens if existing user? overrides
(rf/reg-event-db
  :presence/add-user
  (fn [db [_ user]]
    (let [user (merge user {:color (rand-nth utils/PALETTE)})]
      ;; TODO: make sure usernames are unique
      (update-in db [:presence :users]
                 assoc (:username user) user))))


(rf/reg-event-db
  :presence/remove-user
  (fn [db [_ user]]
    (update-in db [:presence :users] dissoc (:username user))))


(rf/reg-event-db
  :presence/update-editing
  (fn [db [_ {:keys [username block-uid]}]]
    (update-in db [:presence :users username] assoc :block/uid block-uid)))


(rf/reg-event-db
 :presence/update-username
 (fn [db [_ {:keys [current-username new-username]}]]
   (-> db
       (update-in [:presence :users] assoc new-username (get-in db [:presence :users current-username]))
       (update-in [:presence :users new-username] assoc :username new-username)
       (update-in [:presence :users] dissoc current-username))))


(rf/reg-event-fx
  :presence/send-username
  (fn [_ [_ current-username new-username]]
    {:fx [[:presence/send-username! [current-username new-username]]]}))
