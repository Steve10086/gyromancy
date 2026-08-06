#veil:buffer veil:camera VeilCamera

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec4 Color;

out vec4 vertexColor;
out vec2 texCoord;

void main() {
    gl_Position = VeilCamera.ProjMat * VeilCamera.ViewMat
            * vec4(Position - VeilCamera.CameraPosition, 1.0);
    vertexColor = Color;
    texCoord = UV0;
}
