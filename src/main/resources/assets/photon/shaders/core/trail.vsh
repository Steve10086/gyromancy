#version 150

// --- 输入变量 (由Minecraft和Photon提供) ---
in vec3 Position;          // 顶点位置
in vec2 UV0;               // 纹理坐标

// --- 输出变量 (传递给片元着色器) ---
out vec2 texCoord;

// --- 统一变量 (在Photon编辑器中可调) ---
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

void main() {
    // 将纹理坐标传递给片元着色器
    texCoord = UV0;

    // 计算顶点的最终位置
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}