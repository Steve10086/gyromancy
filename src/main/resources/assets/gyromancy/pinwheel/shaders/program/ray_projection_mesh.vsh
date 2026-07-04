#veil:buffer veil:camera VeilCamera

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;

uniform vec3 SourceCenter;
uniform vec3 SourceU;
uniform vec3 SourceV;
uniform vec3 BeamWorld;
uniform vec4 EffectTint;

out vec4 beamColor;
out float beamT;

void main() {
    vec3 world = SourceCenter
            + SourceU * Position.x
            + SourceV * Position.y
            + BeamWorld * Position.z;

    gl_Position = VeilCamera.ProjMat * VeilCamera.ViewMat * vec4(world - VeilCamera.CameraPosition, 1.0);
    beamColor = Color * EffectTint;
    beamT = Position.z;
}
