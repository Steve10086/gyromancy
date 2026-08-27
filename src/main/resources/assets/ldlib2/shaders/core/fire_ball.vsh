#version 330 core

#moj_import <fog.glsl>
#moj_import <photon:particle.glsl>
layout(location = 9) in float T;

uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;
uniform float GameTime;
uniform float WobbleSpeed;
uniform float WobbleIntensity;
uniform float WobbleFrequency;
uniform float WobbleAmount;

out float vertexDistance;
out vec2 texCoord0;
out vec4 vertexColor;
out float particleT;

ParticleData getParticleData2() {
    ParticleData data;

    #ifdef PARTICLE_MODEL_INSTANCE
    vec3 camPos = inverse(ModelViewMat)[3].xyz;
    vec3 dirToCam = normalize(camPos - iPos);
    vec3 right = normalize(cross(vec3(0, 1, 0), dirToCam));
    vec3 up = vec3(0, 1, 0);
    vec3 forward = cross(right, up);
    mat3 faceMat = mat3(right, up, forward);

    vec3 centeredPos = aPos - vec3(0.5);
    data.Position = faceMat * (centeredPos * iScale) + iPos;
    data.Normal = normalize(faceMat * aNormal);
    data.Color = vec4(iColor.rgb * aBrightness, iColor.a);
    data.UV = aUV;
    data.LightUV = ivec2((iLight >> 16) & 0xFFFF, iLight & 0xFFFF);
    #endif

    return data;
}

void main() {
    #ifdef PARTICLE_MODEL_INSTANCE

    ParticleData data = getParticleData2();

    #else

    ParticleData data = getParticleData();

    #endif

    vec3 vPos = data.Position;
    vec3 wPos = (ModelViewMat * vec4(data.Position, 1.0)).xyz;

    float wobbleValue = WobbleSpeed * GameTime * 2000. + wPos.y * WobbleIntensity;

    vec3 wobblePos = vec3(vPos.x + sin(wobbleValue) * WobbleFrequency, vPos.y, vPos.z + cos(wobbleValue) * WobbleFrequency);
    wobblePos = mix(wobblePos, vPos, WobbleAmount);

    gl_Position = ProjMat * ModelViewMat * vec4(wobblePos, 1.0);

    vertexDistance = fog_distance(wobblePos, FogShape);
    texCoord0 = data.UV;
    vertexColor = data.Color * texelFetch(Sampler2, data.LightUV / 16, 0);
    particleT = T;
}
