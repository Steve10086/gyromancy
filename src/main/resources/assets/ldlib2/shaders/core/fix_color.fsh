#version 330 core

#moj_import <fog.glsl>

uniform sampler2D MaterialTexture;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform vec4 ColorHDR;

in float vertexDistance;
in vec2 texCoord0;
in float vertexAlpha;

out vec4 fragColor;

void main() {
    vec4 material = texture(MaterialTexture, texCoord0);
    vec3 color = material.rgb * ColorHDR.rgb * ColorHDR.a;
    float alpha = material.a * vertexAlpha * ColorModulator.a;

    fragColor = linear_fog(vec4(color, alpha), vertexDistance, FogStart, FogEnd, FogColor);
}
