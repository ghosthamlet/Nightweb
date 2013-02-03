(ns nightweb.torrent
  (:use [clojure.java.io :only [file input-stream]]
        [nightweb.io :only [base32-encode]]
        [nightweb.constants :only [torrent-ext]]))

(def manager nil)

(defn start-torrent-manager
  []
  (let [context (net.i2p.I2PAppContext/getGlobalContext)]
    (def manager (org.klomp.snark.SnarkManager. context))
    (.updateConfig manager
                   nil ;dataDir
                   true ;filesPublic
                   true ;autoStart
                   nil ;refreshDelay
                   nil ;startDelay
                   nil ;seedPct
                   nil ;eepHost
                   nil ;eepPort
                   nil ;i2cpHost
                   nil ;i2cpPort
                   nil ;i2cpOps
                   nil ;upLimit
                   nil ;upBW
                   false ;useOpenTrackers
                   true ;useDHT
                   nil) ;theme
    (.start manager false)))

(defn add-hash
  [path info-hash-bytes]
  (future
    (try
      (do
        (.addMagnet manager
                    (base32-encode info-hash-bytes)
                    info-hash-bytes
                    nil
                    false
                    true
                    (reify org.klomp.snark.CompleteListener
                      (torrentComplete [this snark]
                        (println "torrentComplete")
                        (.torrentComplete manager snark))
                      (updateStatus [this snark]
                        (println "updateStatus")
                        (.updateStatus manager snark))
                      (gotMetaInfo [this snark]
                        (println "gotMetaInfo")
                        (.gotMetaInfo manager snark path))
                      (fatal [this snark error]
                        (println "fatal" error)
                        (.fatal manager snark error))
                      (addMessage [this snark message]
                        (println "addMessage" message)
                        (.addMessage manager snark message))
                      (gotPiece [this snark]
                        (println "gotPiece")
                        (.gotPiece manager snark))
                      (getSavedTorrentTime [this snark]
                        (println "getSavedTorrentTime")
                        (.getSavedTorrentTime manager snark))
                      (getSavedTorrentBitField [this snark]
                        (println "getSavedTorrentBitField")
                        (.getSavedTorrentBitField manager snark)))
                    path)
        (println "Hash added to" path))
      (catch IllegalArgumentException iae
        (println "Invalid info hash")))))

(defn add-torrent
  ([path] (add-torrent path true))
  ([path overwrite?]
   (try
     (let [base-file (file path)
           root-path (.getParent base-file)
           torrent-file (file root-path (str (.getName base-file) torrent-ext))
           torrent-path (.getAbsolutePath torrent-file)
           listener (reify org.klomp.snark.StorageListener
                      (storageCreateFile [this storage file-name length]
                        (println "storageCreateFile" file-name))
                      (storageAllocated [this storage length]
                        (println "storageAllocated" length))
                      (storageChecked [this storage piece-num checked]
                        (println "storageChecked" piece-num))
                      (storageAllChecked [this storage]
                        (println "storageAllChecked"))
                      (storageCompleted [this storage]
                        (println "storageCompleted"))
                      (setWantedPieces [this storage]
                        (println "setWantedPieces"))
                      (addMessage [this message]
                        (println "addMessage" message)))
           storage (if (and (not overwrite?) (.exists torrent-file))
                     (org.klomp.snark.Storage.
                       (.util manager)
                       (org.klomp.snark.MetaInfo. (input-stream torrent-path))
                       listener)
                     (org.klomp.snark.Storage.
                       (.util manager) base-file nil false listener))
           _ (.close storage)
           meta-info (.getMetaInfo storage)
           bit-field (.getBitField storage)]
       (future
         (when (and overwrite? (.exists torrent-file))
           (.stopTorrent manager torrent-path true)
           (.delete torrent-file))
         (.addTorrent manager
                      meta-info
                      bit-field
                      torrent-path
                      false
                      root-path)
         (println "Torrent added to" torrent-path))
       (.getInfoHash meta-info))
     (catch java.io.IOException ioe
       (println "Error creating torrent for" path)
       nil))))
