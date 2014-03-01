#version 400

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
}