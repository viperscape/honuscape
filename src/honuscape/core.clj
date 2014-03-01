(ns honuscape.core
  (:require [clojure.pprint :as pprint]
            [honuscape.collada]
            [clojure.tools.nrepl :as nrepl]
            [clojure.core.async :as async :refer [go <! <!! >! >!! timeout alts!!]]
            [honuscape.math :as glmath]
            [clojure.core.matrix :as matrix :refer [mset identity-matrix]])
  (:import (java.nio ByteBuffer FloatBuffer)
           (org.lwjgl BufferUtils)
           (org.lwjgl.input Keyboard)
           (org.lwjgl.opengl ContextAttribs Display DisplayMode GL11 GL12 GL15 GL20 GL30 PixelFormat)
           (org.lwjgl.util.glu GLU Project))
  (:gen-class))

(declare server globals)
(defonce entities (ref [])) ;;will store vao info for accessing during draw state
(defonce assets (ref {})) ;;holds asset mesh data

;(use '[clojure.tools.nrepl.server :only (start-server stop-server)])
;(defonce server (start-server :port 7888))

(defonce fns (ref [])) ;;fns to be eval in gl thread, like: (glfn fns (destroy)) ;;destroy gl contexts safely from outside
(defmacro glfn [r fn]
  "adds the fn to the list of fn's which get eval 
  during opengl runtime in the opengl thread, quote the fn"
  `(dosync (ref-set ~r (conj @fns (delay ~fn)))))

(defn do-glfn []
  "gets called repeatedly during update loop, 
  gives gl context outside of thread; see glfn"
  (if-not (empty? @fns)

    (try
     (doall (map deref @fns))
     (dosync (ref-set fns []))
     (catch Exception e (str "exception: " (.getMessage e)) ))
    ))



(defn build-vao! [mesh]
  (prn "building vao")
  (let [vertices (float-array
                   (flatten (for [n mesh] 
                    (:va (val n)))))
        vertices-buffer (-> (BufferUtils/createFloatBuffer (count vertices))
                            (.put vertices)
                            (.flip))

        normals (float-array
                   (flatten (for [n mesh] 
                    (:na (val n)))))
        normals-buffer (-> (BufferUtils/createFloatBuffer (count normals))
                            (.put normals)
                            (.flip))

        indices-count (count vertices); (count indices)

        ;; create & bind Vertex Array Object
        vao (GL30/glGenVertexArrays)
        _ (GL30/glBindVertexArray vao)
        ;; create & bind Vertex Buffer Object for vertices
        vbo (GL15/glGenBuffers)
        _ (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER vbo)
        _ (GL15/glBufferData GL15/GL_ARRAY_BUFFER vertices-buffer GL15/GL_STATIC_DRAW)
        _ (GL20/glVertexAttribPointer 0 3 GL11/GL_FLOAT false 0 0)
        _ (GL20/glEnableVertexAttribArray 0)
       ; _ (GL20/glVertexAttribPointer 1  3 GL11/GL_FLOAT false 0 0)
        _ (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER 0)

        ;;todo: do this properly! should compact and bind once  
        vbo-norms (GL15/glGenBuffers)
        _ (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER vbo-norms)
        _ (GL15/glBufferData GL15/GL_ARRAY_BUFFER normals-buffer GL15/GL_STATIC_DRAW)
        _ (GL20/glVertexAttribPointer 1 3 GL11/GL_FLOAT false 0 0)
        _ (GL20/glEnableVertexAttribArray 1)
        _ (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER 0)

        
        ;; deselect the VAO
        _ (GL30/glBindVertexArray 0)

        _ (println "build-vao errors?" (GL11/glGetError))]
        {:vao vao, :indices-count indices-count, :vbo vbo, :vbo-norms vbo-norms}
))



(defn clear-assets! [] (dosync (ref-set assets {})))
(defn clear-entities! [] (dosync (ref-set entities {})))

(defn load-asset [a] ;;blocking
  (let [mesh (honuscape.collada/load-model a)]
    (Thread/sleep 250);;just a simulation here
    mesh))


(defn add-asset! [a] ;;blocks
  "adds assets, which eventually update asset ref
  when realized: returns the newly loaded asset name"
  (let [m (load-asset a)
        mn (:name m)]
      (dosync (alter assets conj {mn m}))
      mn))

(defn add-entity-mesh! [m transform]
  (if-let [mesh (get @assets m)]
    (dosync (alter entities conj 
      (merge (build-vao!(honuscape.collada/prep-geoms mesh))
      {:model transform ;(transform (translate-left [0.2 5 8]) (rotate-by-left 45 :z))
      :name (:name mesh)} )))
  {:error "cannot get mesh"}))

(def c (async/chan))
(defn peek! [ch]
  "quickly looks at a channel for new incoming data, pulls it if it exists, quits otherwise;"
  (let [[m ch] (alts!! [c (timeout 1)])] m))

(defn drain-alt! [ch mbuff]
  (remove nil? (repeatedly mbuff #(peek! ch))))

(defn drain! ([ch] (drain! ch nil)) ([ch mbuff] 
  "drains a channel with peek!, optionally specify max buffer size to quit draining at"
  (loop [m (peek! ch)
       ml []
       n (or mbuff 2)]
       (if-not m 
         ml
         (if-not (> n 1)
          (conj ml m)
          (recur (peek! ch), (conj ml m), (if mbuff (dec n) n)) )))))

(def cf (future (>!! c (add-asset! "resources/cone.dae"))))

(defn async-add-ent []
  (if-let [mesh 
          (doall (let [[m ch] (async/alts!! [c (async/timeout 1)])] m))] 
    (do (println "adding entity! " (str mesh))
    (add-entity-mesh! mesh (matrix/transform (glmath/translate-left [0.2 5 8]) (glmath/rotate-by-left 45 :z))))))

(defn init-window
  [width height title]
  (let [pixel-format (PixelFormat.)
        context-attributes (-> (ContextAttribs. 3 2)
                               (.withForwardCompatible true)
                               (.withProfileCore true))
        current-time-millis (System/currentTimeMillis)]
    (def globals (ref {:width width
                       :height height
                       :title title
                       :angle 0.0
                       :last-time current-time-millis
                       ;; geom ids
                       ;:vao-id 0
                       ;:vbo-id 0
                       ;:vboc-id 0
                       ;:vboi-id 0
                       ;:indices-count 0
                       ;; shader program ids
                       :vs-id 0
                       :fs-id 0
                       :p-id 0
                       ::angle-loc 0}))
    (Display/setDisplayMode (DisplayMode. width height))
    (Display/setTitle title)
    (Display/create pixel-format context-attributes)))


(defn load-shader [shader-str shader-type]
  (let [shader-id (GL20/glCreateShader shader-type)
        _ (GL20/glShaderSource shader-id shader-str)
        _ (println "init-shaders glShaderSource errors?" (GL11/glGetError))
        _ (GL20/glCompileShader shader-id)
        _ (println "init-shaders glCompileShader errors?" (GL11/glGetError))
        ]
    shader-id))

(defn init-shaders []
  (let [vs-id (load-shader (slurp "resources/shaders/default.vert") GL20/GL_VERTEX_SHADER)
        fs-id (load-shader (slurp "resources/shaders/default.frag") GL20/GL_FRAGMENT_SHADER)
        p-id (GL20/glCreateProgram)
        _ (GL20/glAttachShader p-id vs-id)
        _ (GL20/glAttachShader p-id fs-id)

        _ (GL20/glLinkProgram p-id)

        _ (println "link status? " (GL20/glGetProgram p-id GL20/GL_LINK_STATUS))
        _ (GL20/glUseProgram p-id)
        _ (println "init-shaders use errors?" (GL11/glGetError))
        angle-loc (GL20/glGetUniformLocation p-id "angle")
        _ (GL20/glUniformMatrix4 (GL20/glGetUniformLocation p-id "projection") false 
           (let [f (float-array (matrix/to-double-array (:projection @globals)))] ;float-array, flatten
            (-> (BufferUtils/createFloatBuffer (count f))
                            (.put f)
                            (.flip))))
        _ (GL20/glUniformMatrix4 (GL20/glGetUniformLocation p-id "view") true 
           (let [f (float-array (matrix/to-double-array (:view @globals)))] 
            (-> (BufferUtils/createFloatBuffer (count f))
                            (.put f)
                            (.flip))))
       ; _ (GL20/glUniformMatrix4 (GL20/glGetUniformLocation p-id "model") true 
       ;    (let [f (float-array (to-double-array (:model @globals)))] 
       ;     (-> (BufferUtils/createFloatBuffer (count f))
       ;                     (.put f)
       ;                     (.flip))))
        ]
    (dosync (ref-set globals
                     (assoc @globals
                       :vs-id vs-id
                       :fs-id fs-id
                       :p-id p-id
                       :angle-loc angle-loc
                       )))
    ))


(defn init-gl []
  (let [{:keys [width height]} @globals]
    (println
      "(GL v.):" (GL11/glGetString GL11/GL_VERSION)
      ", (shader v.):" (GL11/glGetString GL20/GL_SHADING_LANGUAGE_VERSION))
    (println "Max vbo verts " (GL12/GL_MAX_ELEMENTS_VERTICES) 
      " & indices " (GL12/GL_MAX_ELEMENTS_INDICES))
    (GL11/glClearColor 0.1 0.1 0.1 0.0)
    (GL11/glViewport 0 0 width height)


    (dosync (ref-set globals
      (assoc @globals 
        :projection (let [halfw 1.0
          halfh (float (/ halfw (/ width height)))]
          (glmath/perspective 45 (float (/ width height)) 0.1 1000.0))
        :view (glmath/lookat [0 22 -5] [0.2 5 8] [0 1 0]) ;(translate [0 0 -5]);
        ;:model (transform (translate-left [0.2 5 8]) (rotate-by-left 45 :z))
        )))

    ;(init-buffers)
    (println "init shaders, pls wait") 
    (init-shaders))
    (GL20/glUseProgram (@globals :p-id))

  ;  (let [lbuff (float-array '(0.2 0.2 0.2 1.0))]
  ;   (dosync (ref-set globals
  ;    (assoc @globals :mylight 
  ;      (-> (BufferUtils/createFloatBuffer (count lbuff))
  ;          (.put lbuff)
  ;          (.flip))))))

    ;;example to load a mesh
    ;(add-entity-mesh! "resources/cube.dae", (matrix/transform (translate-left [0.2 5 8]) (rotate-by-left 45 :z)))
    )

(defn draw []
  (let [{:keys [;width height 
                angle angle-loc
                p-id ;vao-id ;vboi-id
                ;indices-count indices
                ;mylight mylight
                ]} @globals
                ;w2 (/ width 2.0)
                ;h2 (/ height 2.0)
                ]
    (GL11/glClear (bit-or GL11/GL_COLOR_BUFFER_BIT  GL11/GL_DEPTH_BUFFER_BIT))

    (GL11/glEnable GL11/GL_CULL_FACE)
    (GL11/glEnable GL11/GL_DEPTH_TEST)

    ;(GL20/glUseProgram p-id)
    (GL20/glUniform1f angle-loc angle)

    (doseq [n @entities]
      (GL30/glBindVertexArray (n :vao))
      (GL11/glDrawArrays GL11/GL_TRIANGLES 0 (n :indices-count))
      (GL20/glUniformMatrix4 (GL20/glGetUniformLocation p-id "model") true 
           (let [f (float-array (matrix/to-double-array (n :model)))] 
            (-> (BufferUtils/createFloatBuffer (count f))
                            (.put f)
                            (.flip))))
      (GL30/glBindVertexArray 0))
    
    ;(GL20/glUseProgram 0)
    ;(println "draw errors?" (GL11/glGetError))
    ))

(defn update
  []
  (let [{:keys [width height angle last-time]} @globals
        cur-time (System/currentTimeMillis)
        delta-time (- cur-time last-time)
        next-angle (+ (* delta-time 0.09) angle)
        next-angle (if (>= next-angle 360.0)
                     (- next-angle 360.0)
                     next-angle)
        ]
        (dosync (ref-set globals
                     (assoc @globals
                       :angle next-angle
                       :last-time cur-time
                          )))

    (do (Keyboard/next) 
        (if (Keyboard/getEventKeyState) ;;geteventkey sticks around
          (if (= (Keyboard/getEventKey) Keyboard/KEY_UP) ;;pressing up?
                (do (prn "pressing up")
                  (prn angle)))))
  (draw))
  (do-glfn)
  (async-add-ent))


(defn destroy-gl []
  (let [{:keys [p-id vs-id fs-id vao-id vbo-id vboc-id vboi-id]} @globals]
    ;; Delete the shaders
    (GL20/glUseProgram 0)
    (GL20/glDetachShader p-id vs-id)
    (GL20/glDetachShader p-id fs-id)

    (GL20/glDeleteShader vs-id)
    (GL20/glDeleteShader fs-id)
    (GL20/glDeleteProgram p-id)

    (doseq [n @entities]
      (GL30/glBindVertexArray (n :vao))

      ;; Disable the VAO attributes list
      (GL20/glDisableVertexAttribArray 0) ;verts
      (GL20/glDisableVertexAttribArray 1) ;norms

      ;; Delete the VBO
      (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER 0)
      (GL15/glDeleteBuffers (n :vbo))
      (GL15/glDeleteBuffers (n :vbo-norms))

      ;; Delete the VAO
      (GL30/glBindVertexArray 0)
      (GL30/glDeleteVertexArrays (n :vao)))
    ))



(defn destroy [] 
  (destroy-gl)
  (Display/destroy))

(defn run []
  (init-window 800 600 "LWJGL")
  (init-gl)
  (while (not (Display/isCloseRequested))
    (#'update)
    (Display/update)
    (Display/sync 60))
  (destroy-gl)
  (Display/destroy))


(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  ;; work around dangerous default behaviour in Clojure
  (alter-var-root #'*read-eval* (constantly false))
  (run))

