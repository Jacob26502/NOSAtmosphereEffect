#version 300 es
precision highp float;

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uTextureSharp;
uniform sampler2D uTextureBlur;

#define MAX_BLOBS 16
uniform vec3 uBlobColors[MAX_BLOBS];
uniform vec2 uBlobPositions[MAX_BLOBS];
uniform float uBlobSizes[MAX_BLOBS];
uniform int uBlobCount;
uniform float uAspectRatio;

uniform float uBlurStrength;
uniform float uDimLevel;

// Simple pseudo-random function for noise
float rand(vec2 co){
    return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    float t = uBlurStrength;

    // --- 1. Background ---
    float blurPhase = smoothstep(0.0, 0.3, t);
    vec3 sharp = texture(uTextureSharp, vTexCoord).rgb;
    vec3 blur = texture(uTextureBlur, vTexCoord).rgb;
    vec3 finalColor = mix(sharp, blur, blurPhase);

    // --- 2. Blob Layering ---
    // Fade in: 0.3 -> 0.8
    float globalOpacity = smoothstep(0.3, 0.8, t);

    // Time variable for animating the shape wobble
    // We use blurStrength as a proxy for time progress if needed,
    // but ideally we'd pass a uTime uniform.
    // Here we can use uBlurStrength to drive the distortion phase.
    float timePhase = t * 10.0;

    if (globalOpacity > 0.01 && uBlobCount > 0) {
        vec2 uv = vTexCoord;
        uv.x *= uAspectRatio;

        for(int i = 0; i < MAX_BLOBS; i++) {
            if (i >= uBlobCount) break;

            vec2 pos = uBlobPositions[i];
            pos.x *= uAspectRatio;

            // Calculate Angle for shape distortion
            vec2 delta = uv - pos;
            float angle = atan(delta.y, delta.x);
            float dist = length(delta);
            float radius = uBlobSizes[i];

            // SHAPE DISTORTION:
            // Add sine waves to the radius based on angle.
            // This makes it look like an amoeba/liquid rather than a perfect circle.
            // 3.0, 5.0 are frequencies. timePhase makes it rotate/undulate.
            float distortion = sin(angle * 3.0 + timePhase + float(i)) * 0.02 +
            cos(angle * 5.0 - timePhase) * 0.02;

            // Apply distortion to the effective radius check
            float effectiveRadius = radius + distortion;

            // Smooth border
            float alpha = smoothstep(effectiveRadius, effectiveRadius * 0.4, dist);

            alpha *= globalOpacity;

            // Layering (No color mixing)
            if (alpha > 0.0) {
                finalColor = mix(finalColor, uBlobColors[i], alpha);
            }
        }
    }

    // --- 3. Dimming ---
    finalColor = mix(finalColor, vec3(0.0), uDimLevel * t);

    fragColor = vec4(finalColor, 1.0);
}