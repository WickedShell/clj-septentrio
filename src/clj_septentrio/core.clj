(ns clj-septentrio.core
  (:require [clojure.core.async :refer [>!! close!]])
  (:import [CRC16CCITT]
           [java.io InputStream]
           [java.nio ByteBuffer ByteOrder]))

(defn- uint8 [x] (bit-and x 0xff))
(defn- uint16 [x] (bit-and x 0xffff))
(defn- uint32 [x] (bit-and x 0xffffffff))

(defn- ExtEventPVTGeodetic
  "Decodes a ExtEventPVTGeodetic (4038) block"
  [^ByteBuffer buffer length]
  (.position buffer 6)
  (let [TOW         (uint32 (.getInt    buffer))
        WNc         (uint16 (.getShort  buffer))
        Mode        (uint8  (.get       buffer))
        Error       (uint8  (.get       buffer))
        Latitude            (.getDouble buffer)
        Longitude           (.getDouble buffer)
        Height              (.getDouble buffer)
        Undulation          (.getFloat  buffer)
        Vn                  (.getFloat  buffer)
        Ve                  (.getFloat  buffer)
        Vu                  (.getFloat  buffer)
        COG                 (.getFloat  buffer)
        RxClkBias           (.getDouble buffer)
        RxClkDrift          (.getFloat  buffer)
        TimeSystem  (uint8  (.get       buffer))
        Datum       (uint8  (.get       buffer))
        NrSV        (uint8  (.get       buffer))
        WACorrInfo  (uint8  (.get       buffer))
        ReferenceID (uint16 (.getShort  buffer))
        MeanCorrAge (uint16 (.getShort  buffer))
        SignalInfo  (uint32 (.getInt    buffer))
        AlertFlag   (uint8  (.get       buffer))]
    ; FIXME: decode the R1 fields
    {:message :ExtEventPVTGeodetic
     :TOW TOW :WNc WNc :Mode Mode :Latitude Latitude :Longitude Longitude :Height Height :Undulation Undulation
     :Vn Vn :Ve Ve :Vu Vu :COG COG :RxClkBias RxClkBias :RxClkDrift RxClkDrift :TimeSystem TimeSystem
     :Datum Datum :NrSV NrSV :WACorrInfo WACorrInfo :ReferenceId ReferenceID :MeanCorrAge MeanCorrAge
     :SignalInfo SignalInfo :AlertFlag AlertFlag}))

(defn- decode
  "Decode a given message into a map and send it out a channel"
  [^ByteBuffer buffer length]
  (case (bit-and (.getShort buffer 2) 0x0fff)
    4038 (ExtEventPVTGeodetic buffer length)
    nil
    ))


(defn parse
  "Parse an input stream of SBF messages, send decoded messages on the provided channel.
  This runs until the input stream is closed, and will close the channel on completion."
  [^InputStream input channel]
  (let [byte-buffer (ByteBuffer/allocate 0xffff)
        buffer (.array byte-buffer)]
    (.order byte-buffer   ByteOrder/LITTLE_ENDIAN)

    (loop [state :preamble1]
          (case state
            :preamble1 (let [read-byte (.read input)]
                         (if (= read-byte 0x24)
                           (recur :preamble2)
                           (when (pos? read-byte)
                             (recur :preamble1))))
            :preamble2 (let [read-byte (.read input)]
                         (if (= read-byte 0x40)
                           (recur :crc)
                           (when (pos? read-byte)
                             (recur :preamble1))))
            :crc (when (= (.read input buffer 0 6) 6)
                   (recur :payload))
            :payload (let [length (- (.getShort byte-buffer 4) 8)]
                       (when (= (.read input buffer 6 length) length)
                         (recur :check-crc)))
            :check-crc (let [length (- (.getShort byte-buffer 4) 8)
                             read-crc (.getShort byte-buffer 0)
                             computed-crc (CRC16CCITT/computeCRC buffer 2 (+ length 6))]
                         (when (= read-crc computed-crc)
                           (when-let [message (decode byte-buffer length)]
                             (>!! channel message)))
                         (recur :preamble1))))
    (close! channel)))
