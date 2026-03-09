#version 300 es
precision highp float;

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uTextureSharp;
uniform float uAspectRatio;
uniform float uBlurStrength; // 1.0 = Halftone, 0.0 = Sharp
uniform float uDimLevel;

// NEW UNIFORMS
uniform float uDotSize;
uniform float uGrayscale;

float random(vec2 co) {
    return fract(sin(dot(co.xy, vec2(12.9898, 78.233))) * 43758.5453);
}

mat2 rotate2d(float angle) {
    float s = sin(angle);
    float c = cos(angle);
    return mat2(c, -s, s, c);
}

// Generates just the dot structure
float halftoneChannel(vec2 uv, float angle, float value, vec2 texSize, float dotSize) {
    vec2 centerUV = uv - 0.5;
    centerUV.x *= uAspectRatio;
    vec2 rotUV = rotate2d(angle) * centerUV;

    vec2 gridUV = rotUV * texSize.y / dotSize;
    vec2 localUV = fract(gridUV) - 0.5;

    float dist = length(localUV);
    float radius = sqrt(value) * 0.75;
    float edge = max(0.05, 1.0 / dotSize);

    return smoothstep(radius + edge, radius - edge, dist);
}

void main() {
    float t = clamp(uBlurStrength, 0.0, 1.0);
    vec3 sharp = texture(uTextureSharp, vTexCoord).rgb;
    vec2 texSize = vec2(textureSize(uTextureSharp, 0));

    vec3 halftoneOutput;

    // Check if user set Dot Size to 0 (Continuous Tones instead of Dots)
    if (uDotSize == 0.0) {
        if (uGrayscale > 0.5) {
            // Apply continuous Grayscale
            float luma = dot(sharp, vec3(0.299, 0.587, 0.114));
            halftoneOutput = vec3(luma);
        } else {
            // Apply continuous Color (No effect)
            halftoneOutput = sharp;
        }
    } else {
        if (uGrayscale > 0.5) {
            float luma = dot(sharp, vec3(0.299, 0.587, 0.114));
            float kDot = halftoneChannel(vTexCoord, radians(45.0), 1.0 - luma, texSize, uDotSize);
            halftoneOutput = vec3(1.0 - kDot);
        } else {
            vec3 cmy = 1.0 - sharp;
            float cDot = halftoneChannel(vTexCoord, radians(15.0), cmy.r, texSize, uDotSize);
            float mDot = halftoneChannel(vTexCoord, radians(75.0), cmy.g, texSize, uDotSize);
            float yDot = halftoneChannel(vTexCoord, radians(0.0), cmy.b, texSize, uDotSize);
            halftoneOutput = 1.0 - vec3(cDot, mDot, yDot);
        }
    }

    vec3 finalColor = mix(sharp, halftoneOutput, t);

    finalColor = mix(finalColor, vec3(0.0), uDimLevel * t);

    fragColor = vec4(finalColor, 1.0);
}