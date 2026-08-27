#version 150

in vec2 texCoord;
out vec4 fragColor;

// --- 三层参数 ---
uniform vec3  layer1Color;
uniform vec3  layer2Color;
uniform vec3  layer3Color;

uniform float layer1Alpha;
uniform float layer2Alpha;
uniform float layer3Alpha;

// 层高度（厚度），数值越大层越宽（垂直方向）
uniform float layer1Height;
uniform float layer2Height;
uniform float layer3Height;

// 摆动幅度（上下偏移最大值）
uniform float layer1Amplitude;
uniform float layer2Amplitude;
uniform float layer3Amplitude;

// 摆动频率
uniform float layer1Frequency;
uniform float layer2Frequency;
uniform float layer3Frequency;

// 相位偏移（错开摆动）
uniform float layer1Phase;
uniform float layer2Phase;
uniform float layer3Phase;

// 全局时间
uniform float GameTime;

void main() {
    // 将垂直坐标映射到 [-0.5, 0.5]，使拖尾居中
    float v = texCoord.y - 0.5;

    // 计算三个层的垂直偏移（上下摆动）
    float offset1 = layer1Amplitude * sin(GameTime * 2000 + layer1Frequency * texCoord.x + layer1Phase);
    float offset2 = layer2Amplitude * sin(GameTime * 2000 + layer2Frequency * texCoord.x + layer2Phase);
    float offset3 = layer3Amplitude * sin(GameTime * 2000 + layer3Frequency * texCoord.x + layer3Phase);

    // 计算当前像素到每个层中心的距离（垂直方向）
    float dist1 = abs(v - offset1);
    float dist2 = abs(v - offset2);
    float dist3 = abs(v - offset3);

    // 半高度（边缘位置）
    float half1 = layer1Height * 0.5;
    float half2 = layer2Height * 0.5;
    float half3 = layer3Height * 0.5;

    // --- 硬边缘：在层内部为1，外部为0（用 step 实现）---
    float in1 = 1.0 - step(half1, dist1);  // dist < half 时 = 1，否则 = 0
    float in2 = 1.0 - step(half2, dist2);
    float in3 = 1.0 - step(half3, dist3);

    // （可选）如果你想保留一点点柔边，可以把 step 换成 smoothstep(0.99*half, half, dist)，但这里用硬边）

    // 每一层的最终颜色（乘以透明度，但透明度本身已经包含）
    vec4 col1 = vec4(layer1Color, layer1Alpha) * in1;
    vec4 col2 = vec4(layer2Color, layer2Alpha) * in2;
    vec4 col3 = vec4(layer3Color, layer3Alpha) * in3;

    // 加法混合，使叠加区域更亮
    fragColor = col1 + col2 + col3;
}