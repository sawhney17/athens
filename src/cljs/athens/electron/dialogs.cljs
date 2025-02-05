(ns athens.electron.dialogs
  (:require
    [athens.electron.utils :as utils]
    [re-frame.core :as rf]))


(def electron (js/require "electron"))
(def remote (.. electron -remote))
(def fs (js/require "fs"))
(def dialog (.. remote -dialog))
(def path (js/require "path"))


(rf/reg-event-fx
  :fs/open-dialog
  (fn [_ {:keys [location]}]
    (js/alert (str (if location
                     (str "No DB found at " location ".")
                     "No DB found.")
                   "\nPlease open or create a new db."))
    {:dispatch-n [[:modal/toggle]]}))


(defn graph-already-exists-alert
  [{:keys [base-dir name]}]
  (js/alert (str "Directory " base-dir " already contains the " name " graph, sorry.")))


(def open-dir-opts
  (clj->js {:defaultPath (utils/default-dbs-dir)
            :properties  ["openDirectory"]}))


(defn move-dialog!
  "If new-dir/athens already exists, no-op and alert user.
  Else copy db to new db location. When there is an images folder, copy /images folder and all images.
    file:// image urls in block/string don't get updated, so if original images are deleted, links will be broken."
  []
  (let [res     (.showOpenDialogSync dialog open-dir-opts)
        new-dir (first res)]
    (when new-dir
      (let [{name            :name
             curr-images-dir :images-dir
             curr-db-path    :db-path
             curr-base-dir   :base-dir
             :as             curr-db} @(rf/subscribe [:db-picker/selected-db])
            ;; Merge the new local db info into the current db to preserve any other information there.
            {new-base-dir   :base-dir
             new-images-dir :images-dir
             new-db-path    :db-path
             :as            new-db}   (merge curr-db (utils/local-db (.resolve path new-dir name)))]
        (if (utils/local-db-dir-exists? new-db)
          (graph-already-exists-alert new-db)
          (do (.mkdirSync fs new-base-dir)
              (.copyFileSync fs curr-db-path new-db-path)
              (when (.existsSync fs curr-images-dir)
                (.mkdirSync fs new-images-dir)
                (let [imgs (->> (.readdirSync fs curr-images-dir)
                                array-seq
                                (map (fn [x]
                                       [(.join path curr-images-dir x)
                                        (.join path new-images-dir x)])))]
                  (doseq [[curr new] imgs]
                    (.copyFileSync fs curr new))))
              (.rmSync fs curr-base-dir #js {:recursive true :force true})
              (rf/dispatch [:db-picker/remove-db curr-db])
              (rf/dispatch [:db-picker/add-and-select-db new-db])))))))


(defn open-dialog!
  "Allow user to open db elsewhere from filesystem."
  []
  (let [res         (.showOpenDialogSync dialog open-dir-opts)
        db-location (first res)]
    (when (and db-location (.existsSync fs db-location))
      (rf/dispatch [:db-picker/add-and-select-db (utils/local-db db-location)]))))


(defn create-dialog!
  "Create a new database."
  [db-name]
  (let [res         (.showOpenDialogSync dialog open-dir-opts)
        db-location (first res)]
    (when (and db-location (not-empty db-name))
      (let [base-dir (.resolve path db-location db-name)
            local-db (utils/local-db base-dir)]
        (if (utils/local-db-dir-exists? local-db)
          (graph-already-exists-alert local-db)
          (rf/dispatch [:fs/create-and-watch local-db]))))))


(defn- delete-msg-prompt
  [{:keys [name base-dir url] :as db}]
  (let [remote-db? (utils/remote-db? db)
        part-1 (str "Confirm removing \"" name "\" from the list?\n\n")
        part-2 (if remote-db?
                 (str "The data will still remain at remote server " url ".")
                 (str "The files will still remain locally on disk at \"" base-dir "\"."))]
    (str part-1 part-2)))


(defn delete-dialog!
  "Delete an existing database. Select the first db of the remaining ones if user is deleting the currently selected db."
  [{:keys [id] :as db}]
  (let [remote-db?       (utils/remote-db? db)
        confirmation-msg (delete-msg-prompt db)
        current-db-id    (-> @(rf/subscribe [:db-picker/selected-db])
                             :id)
        delete-current-db? (= id current-db-id)]
    (when (.confirm js/window confirmation-msg)
      (when remote-db?
        (rf/dispatch [:remote/disconnect!]))
      (rf/dispatch [:db-picker/remove-db db])
      (when delete-current-db?
        (rf/dispatch [:db-picker/select-most-recent-db])))))
