(ns honuscape.math
  (:require [clojure.core.matrix :as matrix :refer [mset identity-matrix]])
  (:import (java.lang Math)))

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
 (let [f (matrix/normalise (matrix/sub dir eye))
      s (matrix/normalise (matrix/cross up f))
      u (matrix/cross f s)
      mo (matrix/identity-matrix 4)
       mo (mset mo 0 0 (first s))
        mo (mset mo 1 0 (second s))
        mo (mset mo 2 0 (last s))

       mo (mset mo 0 1 (first u))
        mo (mset mo 1 1 (second u))
        mo (mset mo 2 1 (last u))

       mo (mset mo 0 2 (inv (first f)))
        mo (mset mo 1 2 (inv (second f)))
        mo (mset mo 2 2 (inv (last f)))]

       (matrix/transform mo (translate-left [(inv (first eye)) (inv (second eye)) (inv (last eye))]))
      ))
