#version 300 es
precision highp float;

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uTextureSharp;
uniform sampler2D uTextureBlur;

// Reverted to 16 (Performance + Aesthetic of the "Good" version)
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
    vec2 uv = vTexCoord;
    uv.x *= uAspectRatio;

    // --- 1. Background Calculation (Muddy Colors) ---
    vec3 cloudSum = vec3(0.0);
    float cloudWeight = 0.0;

    // We calculate the background 'Cloud' using a very wide falloff
    for(int i = 0; i < MAX_BLOBS; i++) {
        if (i >= uBlobCount) break;
        vec2 pos = uBlobPositions[i];
        pos.x *= uAspectRatio;
        float dist = length(uv - pos);

        // Power 2.0 creates a very wide, muddy gradient that fills the screen
        float w = uBlobSizes[i] / (pow(dist, 2.0) + 0.05);
        cloudSum += uBlobColors[i] * w;
        cloudWeight += w;
    }

    vec3 muddyBackground = vec3(0.0);
    if (cloudWeight > 0.0) {
        muddyBackground = cloudSum / cloudWeight;
    }

    // --- 2. Compose Base Background ---
    // Phase 1: Sharp -> Frosted (0.0 to 0.2)
    float blurPhase = smoothstep(0.0, 0.2, t);

    // Phase 2: Frosted -> Muddy Background (0.18 to 0.8)
    // FIX: Extended the fade duration significantly (was 0.5).
    // This creates a super smooth transition from "Frosted Glass" to "Moving Colors".
    float cloudMorph = smoothstep(0.18, 0.5, t);

    vec3 sharp = texture(uTextureSharp, vTexCoord).rgb;
    vec3 frosted = texture(uTextureBlur, vTexCoord).rgb;

    // Start with Sharp -> Frosted
    vec3 currentBg = mix(sharp, frosted, blurPhase);

    // If movement started, slowly fade to Muddy Background
    if (t > 0.18) {
        currentBg = mix(currentBg, muddyBackground, cloudMorph);
    }

    // --- 3. Blob Layering (The "Old Good Way") ---
    vec3 finalColor = currentBg;

    // Phase 3: Blob Appearance
    float blobOpacity = smoothstep(0.15, 0.3, t);

    if (blobOpacity > 0.01 && uBlobCount > 0) {
        for(int i = 0; i < MAX_BLOBS; i++) {
            if (i >= uBlobCount) break;

            vec2 pos = uBlobPositions[i];
            pos.x *= uAspectRatio;

            vec2 delta = uv - pos;
            float dist = length(delta);
            float radius = uBlobSizes[i];

            // Soft Blob Logic (Original)
            float effectiveRadius = radius;
            float alpha = 1.0 - smoothstep(0.0, effectiveRadius, dist);

            alpha *= blobOpacity;

            // Soft Additive Mixing (The look you liked)
            if (alpha > 0.0) {
                finalColor = mix(finalColor, uBlobColors[i], alpha);
            }
        }
    }

    finalColor = mix(finalColor, vec3(0.0), uDimLevel * t);

    fragColor = vec4(finalColor, 1.0);
}