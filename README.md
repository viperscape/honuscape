# honuscape

This is my pet project of OpenGL in Clojure using LWJGL, future builds hopefully will look more structured as well may include Android bindings and possibly a lein-droid implementation.

## Installation

Clone this repo, lein repl, and then "(run)"
Caution: this may take a minute to start up the first time

## Examples

```clojure
;;load in a mesh and prep the geometry for drawarray use

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

        indices-count (count vertices)

;;bind all that to a VAO, see core source


;;passes a uniform for a model matrix, take note the use of true, as we are using left-handed coordinates for the matrices:
(let [model (transform (translate-left [0.2 5 8]) (rotate-by-left 45 :z))]
     (GL20/glUniformMatrix4 (GL20/glGetUniformLocation p-id "model") true 
           (let [f (float-array (to-double-array model))] 
            (-> (BufferUtils/createFloatBuffer (count f))
                            (.put f)
                            (.flip) ))))

;;example view matrix
(let [view (lookat [0 22 -5] [0.2 5 8] [0 1 0])]
	;;set the view uniform)

;;remember, let the shader handle the primary matrix calculations per vertex, if possible


;;a convenience method for loading and setting collada
(add-asset! "resources/cone.dae")
```

### Bugs

Yes. Also, beware: lack of proper functional style, some nasty looking code and temporarily using globals; however I have basic but modern openGL usage with shaders and vaos.

### Future

This implementation is to grow and eventually include generic wrappers for LWJGL as well as Android (opengl es 3.0).

An idea is to build basic clojure maps that correspond to VAO generators and very basic shaders. The main file for support is Collada as it includes basic support for meshes & scenes, but further support for blender files would be nice. I should suck it up look in to implementing a java variation and simply do some java interop, rather than implement everything in clojure.

## License

Copyright © 2014 Chris Gill,

Distributed under the Eclipse Public License, the same as Clojure. 
Have fun with this code, please fork and push any updates you see fit.
