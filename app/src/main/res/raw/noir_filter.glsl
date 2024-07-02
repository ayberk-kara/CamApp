#version 300 es
precision mediump float;

in vec2 v_TexCoord;
out vec4 outColor;

uniform sampler2D u_Texture;

void main() {
    vec4 color = texture(u_Texture, v_TexCoord);
    float gray = dot(color.rgb, vec3(0.299, 0.587, 0.114));
    outColor = vec4(vec3(gray), color.a);
}
