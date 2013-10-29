(ns honuscape.obj)

(defn load-model [file]

(let [obj  (clojure.string/split (slurp file) #"\no ")
	  header (nth obj 0)
  	  fobjs (for [n (rest obj)] (clojure.string/split n #"\n"))
	  nobjs (for [n fobjs] (for [i n] (clojure.string/split i #" ")))
	  meshes (for [n nobjs]
		{(apply str (first n)) {:va (vec (map #(Float. %) (flatten 
		  (let [m (clojure.walk/keywordize-keys (group-by first n))] 
			(for [f (:f m)] (map #(rest (nth (:v m) (dec (Integer. %)))) (rest f)))))))}})
	  ]
	  meshes))

;C:\\Users\\Chris\\honuscape\\resources\\cube.obj