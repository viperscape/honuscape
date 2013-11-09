(ns honuscape.core
  (:require [clojure.pprint :as pprint]
            [honuscape.collada]
            [honuscape.window]
            [clojure.tools.nrepl :as nrepl])
  (:import (java.nio ByteBuffer FloatBuffer)
           (org.lwjgl BufferUtils)
           (org.lwjgl.input Keyboard)
           (org.lwjgl.opengl ContextAttribs Display DisplayMode GL11 GL12 GL15 GL20 GL30 PixelFormat)
           (org.lwjgl.util.glu GLU Project)
           (java.lang Math))
  (:refer-clojure :exclude [* - + == /])
  (:use clojure.core.matrix)
  (:use clojure.core.matrix.operators)
  (:gen-class))

(declare server globals lookat projection perspective inv translate translate-left rotate-by rotate-by-left)


(use '[clojure.tools.nrepl.server :only (start-server stop-server)])
(defonce server (start-server :port 7888))

(defonce fns (ref [])) ;;fns to be eval
(defn glfn [fun]
  "adds the fn to the list of fn's which get eval 
  during opengl runtime in the opengl thread"
  (dosync (ref-set fns (conj @fns fun))))
(defn do-glfn []
  (try 
    (do (map eval @fns) (dosync (ref-set fns [])))
    (catch Exception e (str "My Exception: " (.getMessage e)))))



(defn build-vao [mesh]
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
        vbo-na (GL15/glGenBuffers)
        _ (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER vbo-na)
        _ (GL15/glBufferData GL15/GL_ARRAY_BUFFER normals-buffer GL15/GL_STATIC_DRAW)
        _ (GL20/glVertexAttribPointer 1 3 GL11/GL_FLOAT false 0 0)
        _ (GL20/glEnableVertexAttribArray 1)
        _ (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER 0)

        
        ;; deselect the VAO
        _ (GL30/glBindVertexArray 0)

        _ (println "build-vao errors?" (GL11/glGetError))]
        [vao indices-count]
))

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
                       :vao-id 0
                       :vbo-id 0
                       :vboc-id 0
                       :vboi-id 0
                       :indices-count 0
                       ;; shader program ids
                       :vs-id 0
                       :fs-id 0
                       :p-id 0
                       ::angle-loc 0}))
    (Display/setDisplayMode (DisplayMode. width height))
    (Display/setTitle title)
    (Display/create pixel-format context-attributes)))

(defn init-buffers
  []
  (let [mesh (honuscape.collada/prep-geoms
              (honuscape.collada/load-model "C:\\users\\chris\\honuscape\\resources\\radar2b.dae"))
        vertices (float-array
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
        vao-id (GL30/glGenVertexArrays)
        _ (GL30/glBindVertexArray vao-id)
        ;; create & bind Vertex Buffer Object for vertices
        vbo-id (GL15/glGenBuffers)
        _ (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER vbo-id)
        _ (GL15/glBufferData GL15/GL_ARRAY_BUFFER vertices-buffer GL15/GL_STATIC_DRAW)
        _ (GL20/glVertexAttribPointer 0 3 GL11/GL_FLOAT false 0 0)
        _ (GL20/glEnableVertexAttribArray 0)
       ; _ (GL20/glVertexAttribPointer 1  3 GL11/GL_FLOAT false 0 0)
        _ (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER 0)

;;todo: do this properly! should compact and bind once  
        vbo-idna (GL15/glGenBuffers)
        _ (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER vbo-idna)
        _ (GL15/glBufferData GL15/GL_ARRAY_BUFFER normals-buffer GL15/GL_STATIC_DRAW)
        _ (GL20/glVertexAttribPointer 1 3 GL11/GL_FLOAT false 0 0)
        _ (GL20/glEnableVertexAttribArray 1)
        _ (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER 0)

        
        ;; deselect the VAO
        _ (GL30/glBindVertexArray 0)

        _ (println "init-buffers errors?" (GL11/glGetError))
        ]
    (dosync (ref-set globals
                     (assoc @globals
                       :vao-id vao-id
                       :vbo-id vbo-id
                       :indices-count indices-count
  )))))


(def vs-shader
  (str "#version 400
 
layout (location = 0) in vec3 in_Position;
layout (location = 1) in vec3 in_Normal;
uniform float angle;
uniform mat4 model;
uniform mat4 view;
uniform mat4 projection;
out vec3 pass_Color;

 
void main(void)
{
  mat4x4 amat = mat4x4(0.0);
  float a = angle*(3.1415926535/180);
  amat[0] = vec4( cos(a), sin(a), 0.0, 0.0);
  amat[2] = vec4(-sin(a), cos(a), 0.0, 0.0);
  amat[1] = vec4(0.0, 0.0, 1.0, 0.0);
  amat[3] = vec4(0.0, 0.0, 0.0, 0.0);
  
  mat4 mv = view * model;
  vec3 mv_v = vec3(amat * vec4(in_Position, 1.0));
  vec3 mv_n = vec3(amat * vec4(in_Normal, 0.0));
  vec3 lightpos = vec3(0.0, 3.0, 0.0);
  mat4 mvp = projection * mv;
  

  float distance = length(lightpos - mv_v);
  vec3 lightvec = normalize(lightpos - mv_v);
  float diffuse = max(dot(mv_n, lightvec), 0.1);
  diffuse = diffuse * (1.0 / (1.0 + (0.25 * distance * distance)));
  pass_Color = vec3(0.3,0.6,0.2) * diffuse;
  gl_Position = mvp * vec4(in_Position, 1.0);
}"))

(def fs-shader
  (str "#version 400
 
precision highp float;
 
in vec3 pass_Color;

out vec4 out_Color;
 
void main(void)
{
        out_Color = vec4(pass_Color,1.0);
}"))


(defn load-shader
  [shader-str shader-type]
  (let [shader-id (GL20/glCreateShader shader-type)
        _ (GL20/glShaderSource shader-id shader-str)
        _ (println "init-shaders glShaderSource errors?" (GL11/glGetError))
        _ (GL20/glCompileShader shader-id)
        _ (println "init-shaders glCompileShader errors?" (GL11/glGetError))
        ]
    shader-id))


(defn init-shaders
  []
  (let [vs-id (load-shader vs-shader GL20/GL_VERTEX_SHADER)
        fs-id (load-shader fs-shader GL20/GL_FRAGMENT_SHADER)
        p-id (GL20/glCreateProgram)
        _ (GL20/glAttachShader p-id vs-id)
        _ (GL20/glAttachShader p-id fs-id)

        _ (GL20/glLinkProgram p-id)

        _ (println "link status? " (GL20/glGetProgram p-id GL20/GL_LINK_STATUS))
        _ (GL20/glUseProgram p-id)
        _ (println "init-shaders use errors?" (GL11/glGetError))
        angle-loc (GL20/glGetUniformLocation p-id "angle")
        _ (GL20/glUniformMatrix4 (GL20/glGetUniformLocation p-id "projection") false 
           (let [f (float-array (to-double-array (:projection @globals)))] ;float-array, flatten
            (-> (BufferUtils/createFloatBuffer (count f))
                            (.put f)
                            (.flip))))
        _ (GL20/glUniformMatrix4 (GL20/glGetUniformLocation p-id "view") true 
           (let [f (float-array (to-double-array (:view @globals)))] 
            (-> (BufferUtils/createFloatBuffer (count f))
                            (.put f)
                            (.flip))))
        _ (GL20/glUniformMatrix4 (GL20/glGetUniformLocation p-id "model") true 
           (let [f (float-array (to-double-array (:model @globals)))] 
            (-> (BufferUtils/createFloatBuffer (count f))
                            (.put f)
                            (.flip))))
        ]
    (dosync (ref-set globals
                     (assoc @globals
                       :vs-id vs-id
                       :fs-id fs-id
                       :p-id p-id
                       :angle-loc angle-loc
                       )))
    ))


(defn init-gl
  []
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
          (perspective 45 (float (/ width height)) 0.1 1000.0))
        :view (lookat [0 22 -5] [0.2 5 8] [0 1 0]) ;(translate [0 0 -5]);
        :model (transform (translate-left [0.2 5 8]) (rotate-by-left 45 :z))
        )))

    (init-buffers)
    (init-shaders))

    (let [lbuff (float-array '(0.2 0.2 0.2 1.0))]
     (dosync (ref-set globals
      (assoc @globals :mylight 
        (-> (BufferUtils/createFloatBuffer (count lbuff))
            (.put lbuff)
            (.flip))))))
    )

(defn draw
  []
  (let [{:keys [width height angle angle-loc
                p-id vao-id ;vboi-id
                indices-count indices
                mylight mylight]} @globals
                w2 (/ width 2.0)
                h2 (/ height 2.0)]
    (GL11/glClear (bit-or GL11/GL_COLOR_BUFFER_BIT  GL11/GL_DEPTH_BUFFER_BIT))

    (GL11/glEnable GL11/GL_CULL_FACE)
    (GL11/glEnable GL11/GL_DEPTH_TEST)

    (GL20/glUseProgram p-id)
    (GL20/glUniform1f angle-loc angle)

    (GL30/glBindVertexArray vao-id)
    (GL11/glDrawArrays GL11/GL_TRIANGLES 0 indices-count)

    (GL30/glBindVertexArray 0)
    (GL20/glUseProgram 0)
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
  (draw)))

(defn destroy-gl
  []
  (let [{:keys [p-id vs-id fs-id vao-id vbo-id vboc-id vboi-id]} @globals]
    ;; Delete the shaders
    (GL20/glUseProgram 0)
    (GL20/glDetachShader p-id vs-id)
    (GL20/glDetachShader p-id fs-id)

    (GL20/glDeleteShader vs-id)
    (GL20/glDeleteShader fs-id)
    (GL20/glDeleteProgram p-id)

    ;; Select the VAO
    (GL30/glBindVertexArray vao-id)

    ;; Disable the VBO index from the VAO attributes list
    (GL20/glDisableVertexAttribArray 0)
   ; (GL20/glDisableVertexAttribArray 1)

    ;; Delete the vertex VBO
    (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER 0)
    (GL15/glDeleteBuffers vbo-id)

    ;; Delete the VAO
    (GL30/glBindVertexArray 0)
    (GL30/glDeleteVertexArrays vao-id)
    ))



(defn destroy [] 
  (destroy-gl)
  (Display/destroy))

(defn run
  []
  (init-window 800 600 "LWJGL")
  (init-gl)
  (while (not (Display/isCloseRequested))
    ;(do-glfn)
    (update)
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


(defn inv [n] "returns the inverse, (* -1 n)" (* -1 n))

(defn projection [l r b t, n f, aspect fov]
  "builds a perspective-projection matrix"
  (let [m (identity-matrix 4)
        yscale (float (/ 1 (Math/tan (* (/ fov 2) (/ Math/PI 180)))))
        xscale (float (/ yscale aspect))
        m (mset m 0 0 xscale);(float (/ (* 2 n) (- r l))))
        m (mset m 1 1 yscale);(float (/ (* 2 n) (- t b))))
        m (mset m 2 2 (inv (float (/ (+ f n) (- f n)))))
        m (mset m 2 3 -1)
        m (mset m 3 2 (inv (float (/ (* 2 f n) (- f n)))))
        ;m (mset m 2 0 (float (/ (+ r l) (- r l))))
        ;m (mset m 2 1 (float (/ (+ t b) (- t b))))
        m (mset m 3 3 0)]
      m
  ))

(defn perspective [fov aspect near far]
  "builds a perspective-projection matrix"
  (let [half (float (Math/tan (* (/ fov 2) (/ Math/PI 180))))
        height (float (* near half))
        width (float (* aspect height))]
       (projection (inv width) width (inv height) height near far aspect fov)
  ))

(defn translate [v]
  "builds a translation matrix, takes a 3d vector"
  (let [m (identity-matrix 4)
        m (mset m 3 0 (first v))
        m (mset m 3 1 (second v))
        m (mset m 3 2 (last v))]
        m))

(defn translate-left [v]
  "builds a translation matrix, takes a 3d vector"
  (let [m (identity-matrix 4)
        m (mset m 0 3 (first v))
        m (mset m 1 3 (second v))
        m (mset m 2 3 (last v))]
        m))

(defn rotate-by [angle axis]
  "returns a rotation matrix, specify angle and axis
  TODO: fixme b/c I rotate improperly"
  (let [ca (Math/cos angle)
        sa (Math/sin angle)]
    (if (= :x axis) 
      (let [m (identity-matrix 4)
        m (mset m 1 1 ca)
        m (mset m 1 2 sa)
        m (mset m 2 1 (inv sa))
        m (mset m 2 2 ca)] m)
     (if (= :y axis) 
      (let [m (identity-matrix 4)
        m (mset m 0 0 ca)
        m (mset m 0 2 (inv sa))
        m (mset m 2 0 sa)
        m (mset m 2 2 ca)] m)
      (if (= :z axis) 
      (let [m (identity-matrix 4)
        m (mset m 0 0 ca)
        m (mset m 0 1 sa)
        m (mset m 1 0 (inv sa))
        m (mset m 1 1 ca)] m))))
  ))

(defn rotate-by-left [angle axis]
  "returns a rotation matrix, specify angle and axis
  TODO: turn this in to a switch statement"
  (let [ca (Math/cos angle)
        sa (Math/sin angle)]
    (if (= :x axis) 
      (let [m (identity-matrix 4)
        m (mset m 1 1 ca)
        m (mset m 1 2 (inv sa))
        m (mset m 2 1 sa)
        m (mset m 2 2 ca)] m)
     (if (= :y axis) 
      (let [m (identity-matrix 4)
        m (mset m 0 0 ca)
        m (mset m 0 2 sa)
        m (mset m 2 0 (inv sa))
        m (mset m 2 2 ca)] m)
      (if (= :z axis) 
      (let [m (identity-matrix 4)
        m (mset m 0 0 ca)
        m (mset m 0 1 (inv sa))
        m (mset m 1 0 sa)
        m (mset m 1 1 ca)] m))))
  ))

(defn lookat [eye dir up]
  "performs lookat matrix operation, takes 3 vectors of size 3
  TODO: prep this properly for right-hand openGL friendly matrix, and run GL_FALSE instead"
 (let [f (normalise (- dir eye))
      s (normalise (cross up f))
      u (cross f s)
      mo (identity-matrix 4)
       mo (mset mo 0 0 (first s))
        mo (mset mo 1 0 (second s))
        mo (mset mo 2 0 (last s))

       mo (mset mo 0 1 (first u))
        mo (mset mo 1 1 (second u))
        mo (mset mo 2 1 (last u))

       mo (mset mo 0 2 (inv (first f)))
        mo (mset mo 1 2 (inv (second f)))
        mo (mset mo 2 2 (inv (last f)))]

       (transform mo (translate-left [(inv (first eye)) (inv (second eye)) (inv (last eye))]))
      ))