#version 300 es
precision highp float;

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uTextureSharp;
uniform float uAspectRatio;
uniform float uBlurStrength; // Acts as transition: 1.0 = Halftone, 0.0 = Sharp
uniform float uDimLevel;

uniform float uEnableNoise;
uniform float uNoiseScale;
uniform float uNoiseStrength;

// How large the halftone dots are (in pixels relative to screen height)
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
    // Adjust for aspect ratio to keep dots perfectly circular
    centerUV.x *= uAspectRatio;
    vec2 rotUV = rotate2d(angle) * centerUV;

    // Scale to the grid based on screen height
    vec2 gridUV = rotUV * texSize.y / DOT_SIZE;
    vec2 localUV = fract(gridUV) - 0.5;

    // Distance from the center of the current cell
    float dist = length(localUV);

    // Calculate radius based on the ink amount.
    // Max radius to fully fill the cell corners is approx 0.707
    float radius = sqrt(value) * 0.75;

    // Smooth edge for anti-aliasing the dots
    float edge = max(0.05, 1.0 / DOT_SIZE);
    float binaryDot = smoothstep(radius + edge, radius - edge, dist);

    // The Magic Transition:
    // When transition = 1.0, it renders binary comic-book dots.
    // As transition -> 0.0, the dots expand and soften exactly into the continuous photographic tones.
    return mix(value, binaryDot, transition);
}

void main() {
    // t = 1.0 (Locked/Halftone), t = 0.0 (Unlocked/Sharp)
    float t = clamp(uBlurStrength, 0.0, 1.0);

    vec3 sharp = texture(uTextureSharp, vTexCoord).rgb;
    // Convert RGB to CMY (Cyan, Magenta, Yellow) for subtractive print mixing
    vec3 cmy = 1.0 - sharp;

    vec2 texSize = vec2(textureSize(uTextureSharp, 0));

    // Generate rotated grid for each print channel (C=15°, M=75°, Y=0°)
    float cDot = halftoneChannel(vTexCoord, radians(15.0), cmy.r, texSize, t);
    float mDot = halftoneChannel(vTexCoord, radians(75.0), cmy.g, texSize, t);
    float yDot = halftoneChannel(vTexCoord, radians(0.0), cmy.b, texSize, t);

    // Convert CMY back to RGB
    vec3 finalColor = 1.0 - vec3(cDot, mDot, yDot);

    // Apply dimming logic for lock screen readability
    finalColor = mix(finalColor, vec3(0.0), uDimLevel * t);

    // Optional Film Grain
    if (uEnableNoise > 0.5) {
        vec2 noiseUV = vTexCoord;
        noiseUV.x *= uAspectRatio;
        vec2 grainUV = floor(noiseUV * uNoiseScale);
        float noise = random(grainUV);
        float noiseVisibility = smoothstep(0.4, 1.0, t);
        finalColor += vec3((noise - 0.5) * uNoiseStrength * noiseVisibility);
    }

    fragColor = vec4(finalColor, 1.0);
}