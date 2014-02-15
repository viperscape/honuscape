(ns honuscape.collada
  (:require [clojure.xml :as xml]
            [clojure.zip :as zip])
  )

;lazy parse using data.xml
;(use 'clojure.data.xml)
(use 'clojure.data.zip.xml)
;(def ldae (let [input-xml (java.io.StringReader. (slurp "C:\\Users\\Chris\\honuscape\\resources\\pid.dae"))] ;wouldn't be a slurp
 ;     (parse input-xml)))


(defn s-to-num [s & ntype]
  "convert string of numbers, spaced evenly, to a numbers vec;
  some reason getnumeric does not play well with map;
  omission of type (via a key) results in float, else use :int"
  (vec (map #((if (some #{:int} ntype) (fn [x] (Integer. x)) (fn [x] (Float. x))) %) (clojure.string/split s #" "))))


(defn load-model [filename]
(let
  [dae (xml/parse (java.io.ByteArrayInputStream. (.getBytes (slurp filename))))

  zdae (zip/xml-zip dae)

  modified (xml-> zdae :asset :modified text)
  up_axis (apply str(xml-> zdae :asset :up_axis text))
  lights (xml-> zdae :library_lights :light #(take 8 %)) ; example of taking maximum 8 lights
  materials (xml-> zdae :library_materials)
  animations (xml-> zdae :library_animations)

  meshname filename;;(apply str (clojure.string/split (last (clojure.string/split (apply str filename) #"/")) #".dae"))

 semantics (into {}  (let [l (first (xml-> zdae :library_geometries))]
    (for [m (xml-> l :geometry)] ;for each geom mesh
     {(keyword (apply str (xml-> m (attr :id))))
     (into {}  (for [i (xml-> m :mesh :polylist :input)]
      {(keyword (apply str (xml-> i (attr :semantic)))) 
      (apply str (rest (apply str (xml-> i (attr :source)))))})) }))) ;;removes # too

  geometries
   (let [l (first (xml-> zdae :library_geometries))]
    (for [m (xml-> l :geometry)] ;for each geom mesh
     {(keyword (apply str (xml-> m (attr :id))))
      (into {} (cons {:indices (s-to-num (apply str (xml-> m :mesh :polylist :p text)) :int)}
     (merge (for [s (xml-> m :mesh :source)] ;positions, normals
      (let [sa (s-to-num (apply str (xml-> s :float_array text)) :float) ;;source's float array
            sem (apply str (xml-> s (attr :id)))
            semantic (keyword (apply str (xml-> m (attr :id))))] 
      (if (.contains  (apply str(xml-> m :mesh :vertices :input (attr :source))) sem) {:positions sa}
        (if (.contains sem ((semantics semantic) :NORMAL)) {:normals sa}
          (if ((semantics semantic) :TEXCOORD)
            (if (.contains sem ((semantics semantic):TEXCOORD)) {:texcoords sa}))
            )))))))}
     ))

   ;_ (prn semantics)


; (let [l (first (xml-> zdae :library_geometries))]
;    (for [m (xml-> l :geometry)] ;for each geom mesh
;    ;  (apply str(xml-> m :mesh :vertices (attr :id))))))
;    (for [s (xml-> m :mesh :source)]
;      (let [sa  (s-to-num (apply str (xml-> s :float_array text)) :float)
;            sem (apply str (xml-> s (attr :id)))] ;source's float array
;              (prn sem (.contains  (apply str(xml-> m :mesh :vertices :input (attr :source))) sem)) ))));) {:positions sa}))))))


   ;; old shit below

  ;mesh-xml (first (xml-> (first (xml-> (first (xml-> zdae :library_geometries)) :geometry)) :mesh))

  ;mesh-text
  ;  {:v (xml-> mesh-xml :source :float_array text)
  ;   :p (xml-> mesh-xml :polylist :p text)
  ;   :pc (xml-> mesh-xml :polylist :vcount text)}

;;(println (vec (xml-> mesh-xml :polylist :vcount text)))

  ;convert text to individual numbers
  ;probably should use an read-eval instead
  ;mesh {:p (vec (map #(Integer. %) (clojure.string/split (first(mesh-text :p)) #" ")))
  ;      :v (flatten (vec  (map #(Float. %) (clojure.string/split (first(mesh-text :v)) #" "))))
  ;      :pc (vec (map #(Integer. %) (clojure.string/split (first (mesh-text :pc)) #" ")))}



 ;verts
 ; (let [i (map #(first %) (partition 2 (mesh :p)))
 ;      v (partition 3 (mesh :v))
 ;      verts (map #(nth v %) i)]

 ;  (if (= "Z_UP" up_axis)
    ;;prep coordinate system
 ;   (for [vert verts]
 ;      [(first vert) (last vert) (* -1 (second vert))])
 ;   verts
 ;   ))
  ] ;;end sweeping let

  {;return a big map of all our goodies
   :geometries geometries
   :name meshname
   :modified modified ;;use meta instead?
   :lights lights
   :materials materials
   :up_axis up_axis
   }
))


(defn prep-coords [va]
  "takes in seq grouped by 3, returns openGL friendly/left-handed vec"
  (for [vert va]
       [(first vert) (last vert) (* -1 (second vert))]))

(defn map-to [i a]
  "maps indices to an array"
  (try (map #(nth a %) i)
    (catch Exception e (str "error in map-to" (.getMessage e) )))) ;;clueless why not exceptions are caught

(defn prep-drawarray [m up]
  "preps array for drawarray use, so no indices needed
  preps with coordinate system, if needed (specified with :up_axis key \"Z_UP\")"

(if (= "Z_UP" up) (prn "coords flipped!"))

(let [v (partition 3 (m :positions))
      n (partition 3 (m :normals))]

 (if (not (m :texcoords))
  (let [i (partition 2 (m :indices))
        vi (map #(first %) i)
        ni (map #(second %) i)
        
        va (map-to vi v);vertex array mapped
        na (map-to ni n)
        ]

    (if (= "Z_UP" up) ;;prep coordinate system
       {:va (prep-coords va), :na (prep-coords na)}
       {:va va,:na na}))

  ;; if texture coordinates
  (let [;i (partition 8 (m :indices))
        ;s (map #(nth % 2) i)
        ;t (map #(nth % 5) i)
        ;uvi (apply map vector [s t])
        i (partition 3 (m :indices)) ;;;(map #(group-by second %) i3)
        vi (map #(first %) i)
        ;_ (prn (count vi))
        ;vi (flatten (for [j i3] (map #(first %) j)))
        ni (map #(second %) i);(flatten (for [j i3] (map #(second %) j)))
        uvi (map #(last %) i);(for [j i3] (filter identity (for [k j] (nth k 2 nil))))
        va (map-to vi v);vertex array mapped
        na (map-to ni n)
        uva (map-to uvi (partition 2 (m :texcoords)))
        ;_ (prn (take 3 va) (take 3 na))
        ];uva (map-to uvi (partition 2 (m :texcoords)))]
;[(last i3) (last (vi))]
   
   (if (= "Z_UP" up) ;;prep coordinate system
    {:va (prep-coords va), :na (prep-coords na), :uva uva}
   ; {:va va, :na na, :uva uva}
    {:va va, :na na, :uva uva}))

  )))



(defn prep-geoms [geoms]
  "preps all included :geometries for simple vertex array use in glDrawArray"
  (into {} (for [g (geoms :geometries)]
    (let [geom (first (keys g))] {geom (prep-drawarray (g geom) (:up_axis geoms))}))))


;(let [mesh (let [m (load-model "resources\\radar2b.dae")] (prep-geoms m))]
 ;   (flatten (for [n mesh] (:va (val n)))))






;(partition 3 ((load-model) :v))

;(partition 3 (partition 2 ((load-model):p)))


 ;(let [verts (let [i (map #(first %) (partition 2 ((load-model):p)))
  ;    v (partition 3 ((load-model) :v))]

  ;(map #(nth v %) i))]

   ;prep coordinate syste,
   ;(for [vert verts]
   ;  [(first vert) (last vert) (* -1 (second vert))]))

;(map #(second %) (partition 2 ((load-model):p)))

; (let [m (load-model)
;      v  (partition 3 (m :v))
;      i (m :pi)]
;
;   (vec (flatten (map #(nth v %) i))))



