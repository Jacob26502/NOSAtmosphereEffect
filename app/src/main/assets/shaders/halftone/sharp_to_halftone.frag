#version 300 es
precision highp float;

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uTextureSharp;
uniform float uAspectRatio;
uniform float uBlurStrength; // 1.0 = Sharp, 0.0 = Halftone
uniform float uDimLevel;
uniform float uEnableNoise;
uniform float uNoiseScale;
uniform float uNoiseStrength;

const float DOT_SIZE = 12.0;

float random(vec2 co) {
    return fract(sin(dot(co.xy, vec2(12.9898, 78.233))) * 43758.5453);
}

mat2 rotate2d(float angle) {
    float s = sin(angle);
    float c = cos(angle);
    return mat2(c, -s, s, c);
}

float halftoneChannel(vec2 uv, float angle, float value, vec2 texSize, float transition) {
    vec2 centerUV = uv - 0.5;
    centerUV.x *= uAspectRatio;
    vec2 rotUV = rotate2d(angle) * centerUV;
    vec2 gridUV = rotUV * texSize.y / DOT_SIZE;
    vec2 localUV = fract(gridUV) - 0.5;
    float dist = length(localUV);
    float radius = sqrt(value) * 0.75;
    float edge = max(0.05, 1.0 / DOT_SIZE);
    float binaryDot = smoothstep(radius + edge, radius - edge, dist);
    return mix(value, binaryDot, transition);
}

void main() {
    // Reverse logic: uBlurStrength goes 1.0 -> 0.0 on unlock.
    // For reverse, 1.0 is Sharp, 0.0 is Halftone.
    float effectStrength = 1.0 - clamp(uBlurStrength, 0.0, 1.0);

    vec3 sharp = texture(uTextureSharp, vTexCoord).rgb;
    vec3 cmy = 1.0 - sharp;
    vec2 texSize = vec2(textureSize(uTextureSharp, 0));

    float cDot = halftoneChannel(vTexCoord, radians(15.0), cmy.r, texSize, effectStrength);
    float mDot = halftoneChannel(vTexCoord, radians(75.0), cmy.g, texSize, effectStrength);
    float yDot = halftoneChannel(vTexCoord, radians(0.0), cmy.b, texSize, effectStrength);

    vec3 finalColor = 1.0 - vec3(cDot, mDot, yDot);

    // Dim applies based on the effect strength
    finalColor = mix(finalColor, vec3(0.0), uDimLevel * effectStrength);

    if (uEnableNoise > 0.5) {
        vec2 noiseUV = vTexCoord;
        noiseUV.x *= uAspectRatio;
        vec2 grainUV = floor(noiseUV * uNoiseScale);
        float noise = random(grainUV);
        float noiseVisibility = smoothstep(0.4, 1.0, effectStrength);
        finalColor += vec3((noise - 0.5) * uNoiseStrength * noiseVisibility);
    }

    fragColor = vec4(finalColor, 1.0);
}