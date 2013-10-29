# honuscape

This is a first attempt at getting OpenGL in Clojure, future builds hopefully will look more structured as well may include Android bindings and a lein-droid implementation.

## Installation

Clone this repo and (run)

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

;;remember, let the shader handle the primary matrix calculations per vertex
```

### Bugs

yes, and total lack of proper functional style, some nasty looking code, and basic but modern openGL usage.

### Future

This implementation is to grow and eventually include generic wrappers for LWJGL as well as Android (opengl es 3.0)
The idea is to build basic clojure maps that correspond to VAO generators and very basic shaders. The main file for support is Collada as it includes basic support for meshes, scenes

## License

Copyright © 2013 Chris Gill,
 -that's me

Distributed under the Eclipse Public License, the same as Clojure. 
Have fun with this code, please fork and push any updates you see fit. This implementation is to grow and eventually include generic wrappers for LWJGL as well as Android (opengl es 3.0)
