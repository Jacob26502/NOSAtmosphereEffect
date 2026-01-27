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

    // --- 1. Background ---
    float blurPhase = smoothstep(0.0, 0.3, t);
    vec3 sharp = texture(uTextureSharp, vTexCoord).rgb;
    vec3 blur = texture(uTextureBlur, vTexCoord).rgb;
    vec3 finalColor = mix(sharp, blur, blurPhase);

    // --- 2. Blob Layering ---
    // Fade in: 0.3 -> 0.8
    float globalOpacity = smoothstep(0.3, 0.8, t);

    // Time variable for animating the shape wobble
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
            float distortion = sin(angle * 3.0 + timePhase + float(i)) * 0.02 +
            cos(angle * 5.0 - timePhase) * 0.02;

            // FIX 1: Clamp effective radius.
            // Prevents negative radius when blobs are tiny (start of animation),
            // which stops "weird lines" caused by math errors.
            float effectiveRadius = max(0.001, radius + distortion);

            // FIX 2: Correct smoothstep order.
            // smoothstep is UNDEFINED if edge0 >= edge1.
            // We must use (low, high) and invert the result (1.0 - x).
            // This creates a perfectly smooth, stable soft circle.
            float alpha = 1.0 - smoothstep(effectiveRadius * 0.4, effectiveRadius, dist);

            alpha *= globalOpacity;

            // Layering
            if (alpha > 0.0) {
                finalColor = mix(finalColor, uBlobColors[i], alpha);
            }
        }
    }

    // --- 3. Dimming ---
    finalColor = mix(finalColor, vec3(0.0), uDimLevel * t);
    fragColor = vec4(finalColor, 1.0);
}