#version 300 es
precision highp float;

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uTextureSharp;
#define MAX_BLOBS 32
uniform vec3 uBlobColors[MAX_BLOBS];
uniform vec2 uBlobPositions[MAX_BLOBS];
uniform float uBlobSizes[MAX_BLOBS]; // Mass
uniform int uBlobCount;
uniform float uAspectRatio;

uniform float uBlurStrength;
uniform float uDimLevel;

void main() {
    float t = uBlurStrength;

    // --- LIQUID RECONSTRUCTION (Shepard's Method) ---
    vec3 weightedColorSum = vec3(0.0);
    float totalWeight = 0.0;

    vec2 uv = vTexCoord;
    uv.x *= uAspectRatio;

    for(int i = 0; i < MAX_BLOBS; i++) {
        if (i >= uBlobCount) break;

        vec2 blobPos = uBlobPositions[i];
        blobPos.x *= uAspectRatio;

        float dist = length(uv - blobPos);

        // Weight calculation (1/dist^3)
        float weight = uBlobSizes[i] / (pow(dist, 3.0) + 0.002);

        weightedColorSum += uBlobColors[i] * weight;
        totalWeight += weight;
    }

    vec3 liquidColor = vec3(0.0);
    if (totalWeight > 0.0) {
        liquidColor = weightedColorSum / totalWeight;
    }

    // --- TIMING LOGIC ---
    // 0.0 -> 0.2: Fade from Sharp to Liquid (Static Frosted Look)
    // 0.2 -> 1.0: Liquid moves (Positions updated by Renderer)
    float morphPhase = smoothstep(0.0, 0.2, t);

    // Dimming applied to background liquid
    liquidColor = mix(liquidColor, vec3(0.0), uDimLevel * t);

    vec3 sharp = texture(uTextureSharp, vTexCoord).rgb;

    // Mix Sharp Photo -> Liquid Field
    vec3 finalColor = mix(sharp, liquidColor, morphPhase);

    fragColor = vec4(finalColor, 1.0);
}