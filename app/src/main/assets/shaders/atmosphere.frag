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

void main() {
    float t = uBlurStrength;

    // --- 1. Background (Blur Phase: 0.0 -> 0.2) ---
    // Background completes its blur at 0.2
    float blurPhase = smoothstep(0.0, 0.2, t);

    vec3 sharp = texture(uTextureSharp, vTexCoord).rgb;
    vec3 blur = texture(uTextureBlur, vTexCoord).rgb;
    vec3 finalColor = mix(sharp, blur, blurPhase);

    // --- 2. Blob Layering (Overlap Phase: 0.1 -> 0.6) ---
    // NEW: We start fading in the blobs at 0.1.
    // This creates an overlap where blobs appear BEFORE the background is fully blurred.
    float globalOpacity = smoothstep(0.1, 0.6, t);

    if (globalOpacity > 0.01 && uBlobCount > 0) {
        vec2 uv = vTexCoord;
        uv.x *= uAspectRatio;

        for(int i = 0; i < MAX_BLOBS; i++) {
            if (i >= uBlobCount) break;

            vec2 pos = uBlobPositions[i];
            pos.x *= uAspectRatio;

            vec2 delta = uv - pos;
            float dist = length(delta);
            float radius = uBlobSizes[i];

            // Clean circle (No wobble)
            float effectiveRadius = radius;

            // Soft edge
            float alpha = 1.0 - smoothstep(effectiveRadius * 0.4, effectiveRadius, dist);

            alpha *= globalOpacity;

            if (alpha > 0.0) {
                finalColor = mix(finalColor, uBlobColors[i], alpha);
            }
        }
    }

    // --- 3. Dimming ---
    finalColor = mix(finalColor, vec3(0.0), uDimLevel * t);
    fragColor = vec4(finalColor, 1.0);
}