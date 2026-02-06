#version 300 es
precision highp float;

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uTextureSharp;
uniform sampler2D uTextureBlur;

uniform float uAspectRatio;

uniform float uBlurStrength;
uniform float uDimLevel;
uniform float uEnableNoise;
uniform float uNoiseScale;
uniform float uNoiseStrength;

float random(vec2 co) {
    return fract(sin(dot(co.xy, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    float t = uBlurStrength;
    vec2 uv = vTexCoord;
    uv.x *= uAspectRatio;

    // --- 2. Compose Base Background ---
    // Phase 1: Sharp -> Frosted (0.0 to 1.0)
    float blurPhase = smoothstep(0.0, 1.0, t);


    vec3 sharp = texture(uTextureSharp, vTexCoord).rgb;
    vec3 frosted = texture(uTextureBlur, vTexCoord).rgb;

    // Start with Sharp -> Frosted
    vec3 currentBg = mix(sharp, frosted, blurPhase);

    // --- 3. Blob Layering (The "Old Good Way") ---
    vec3 finalColor = currentBg;

    finalColor = mix(finalColor, vec3(0.0), uDimLevel * t);

    if (uEnableNoise > 0.5) {
        vec2 grainUV = floor(uv * uNoiseScale);
        float noise = random(grainUV);
        float noiseVisibility = smoothstep(0.4, 1.0, t);
        finalColor += vec3(noise * uNoiseStrength * noiseVisibility);
    }

    fragColor = vec4(finalColor, 1.0);
}