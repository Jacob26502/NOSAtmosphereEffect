#version 300 es
precision highp float;

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uTextureSharp;
uniform sampler2D uTextureBlur;

// Controls the transition (0.0 = Sharp, 1.0 = Fully Blurred)
uniform float uBlurStrength;
uniform float uDimLevel;

// Noise Params
uniform float uEnableNoise;
uniform float uNoiseScale;
uniform float uNoiseStrength;

float random(vec2 co) {
    return fract(sin(dot(co.xy, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    float t = clamp(uBlurStrength, 0.0, 1.0);

    vec3 sharp = texture(uTextureSharp, vTexCoord).rgb;
    vec3 frosted = texture(uTextureBlur, vTexCoord).rgb;

    // Simple Linear Interpolation for Frosted Effect
    vec3 finalColor = mix(sharp, frosted, t);

    // Apply Dimming (Only applied as blur increases)
    finalColor = mix(finalColor, vec3(0.0), uDimLevel * t);

    // Apply Film Grain / Noise
    if (uEnableNoise > 0.5) {
        vec2 grainUV = floor(vTexCoord * uNoiseScale);
        float noise = random(grainUV);

        // Noise becomes more visible as image blurs
        float noiseVisibility = smoothstep(0.0, 0.8, t);
        finalColor += vec3(noise * uNoiseStrength * noiseVisibility);
    }

    fragColor = vec4(finalColor, 1.0);
}