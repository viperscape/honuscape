;project template from https://github.com/rogerallen/hello_lwjgl/blob/master/project.clj

(require 'leiningen.core.eval)

(def LWJGL-CLASSIFIER
  "Per os native code classifier"
  {:macosx "natives-osx"
   :linux "natives-linux"
   :windows "natives-windows"}) ;; TESTME

(defn lwjgl-classifier
  "Return the os-dependent lwjgl native-code classifier"
  []
  (let [os (leiningen.core.eval/get-os)]
    (get LWJGL-CLASSIFIER os)))

(defproject honuscape "0.1.0-SNAPSHOT"
  :description "LWJGL Clojure Framework"
  :url "http://github.com/viperscape"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
  [org.clojure/core.async "0.1.242.0-44b1e3-alpha"]
 		             [org.clojure/data.xml "0.0.7"]
                 [org.clojure/data.zip "0.1.1"]
                 [org.lwjgl.lwjgl/lwjgl "2.8.5"]
                 [org.lwjgl.lwjgl/lwjgl_util "2.8.5"]
                 [org.lwjgl.lwjgl/lwjgl-platform "2.8.5"
                  :classifier ~(lwjgl-classifier)
                  :native-prefix ""]
                  [net.mikera/core.matrix "0.11.0"]
                  [org.clojure/tools.nrepl "0.2.3"]]
  :main honuscape.core)

