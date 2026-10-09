#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:dynamictransforms.glsl>


layout(location=0) in vec2 uv;
layout(location=1) in vec4 parameters;
layout(location=0) out vec4 fragColor;

float hash3(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}

float noise3(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash3(i), hash3(i + vec3(1, 0, 0)), f.x),
                   mix(hash3(i + vec3(0, 1, 0)), hash3(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(hash3(i + vec3(0, 0, 1)), hash3(i + vec3(1, 0, 1)), f.x),
                   mix(hash3(i + vec3(0, 1, 1)), hash3(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}

float fbm3(vec3 p) {
    float sum = 0.0;
    float w = 0.5;
    for (int i = 0; i < 5; i++) {
        sum += w * noise3(p);
        p = p * 2.03 + vec3(1.7, 9.2, 3.1);
        w *= 0.5;
    }
    return sum;
}

// Deep space seen from inside Unlimited Void: dark blue, drifting blue-violet nebula, dense stars.
vec3 space(vec3 d) {
    float neb = fbm3(d * 2.2);
    float wisp = fbm3(d * 4.6 + vec3(neb * 1.6));
    float band = smoothstep(0.42, 0.8, wisp);
    vec3 col = vec3(0.006, 0.010, 0.032);
    col += vec3(0.10, 0.16, 0.40) * neb * neb * neb * 1.3;
    col += vec3(0.32, 0.12, 0.42) * band * neb * 0.45;
    for (int layer = 0; layer < 2; layer++) {
        float scale = layer == 0 ? 90.0 : 190.0;
        vec3 g = d * scale;
        vec3 cell = floor(g);
        vec3 f = fract(g) - 0.5;
        float h = hash3(cell + float(layer) * 17.0);
        float size = 0.10 + 0.14 * fract(h * 7.13);
        float star = step(layer == 0 ? 0.955 : 0.93, h) * (1.0 - smoothstep(0.0, size, length(f)));
        vec3 tone = mix(vec3(0.72, 0.8, 1.0), vec3(1.0, 0.96, 0.92), fract(h * 13.7));
        col += star * tone * (layer == 0 ? 1.3 : 0.6);
    }
    // A galaxy band across the whole sky: dense pale starlight with dark dust lanes, pearl-white
    // and pink ribbons, brightest towards its core.
    vec3 axis = normalize(vec3(0.32, 0.86, 0.40));
    float across = dot(d, axis);
    float galaxy = exp(-across * across * 26.0);
    vec3 core = normalize(cross(axis, vec3(0.0, 0.0, 1.0)));
    float toward = 0.5 + 0.5 * dot(d, core);
    float dust = fbm3(d * 7.5 + vec3(3.1, 0.7, 5.3));
    float lanes = smoothstep(0.45, 0.72, dust) * exp(-across * across * 90.0);
    float glow = galaxy * (0.35 + 0.65 * toward * toward) * (1.0 - 0.85 * lanes);
    col += mix(vec3(0.22, 0.24, 0.48), vec3(0.95, 0.90, 0.98), toward * galaxy) * glow * 0.55;
    float ribbon = fbm3(d * 3.4 + vec3(neb * 2.0));
    col += vec3(1.0, 0.42, 0.72) * galaxy * smoothstep(0.55, 0.8, ribbon) * 0.22;
    col += vec3(0.92, 0.95, 1.0) * galaxy * smoothstep(0.62, 0.85, wisp) * 0.18;
    // Fine star dust packed into the band.
    vec3 g = d * 420.0;
    float h = hash3(floor(g));
    float speck = step(0.86, h) * (1.0 - smoothstep(0.0, 0.32, length(fract(g) - 0.5)));
    col += vec3(0.9, 0.92, 1.0) * speck * galaxy * (0.5 + 0.5 * toward);
    return col;
}

// Swirling accretion vortex around the black hole (polar + log-spiral coordinates, fbm wisps).
float vortex(float ang, float r, float offset) {
    float spiral = ang + 2.4 * log(max(r, 0.05));
    vec3 q = vec3(cos(spiral) * 2.6, sin(spiral) * 2.6, r * 9.0 + offset);
    return fbm3(q);
}

void main() {
    float age = parameters.r;
    float mode = floor(parameters.g * 8.0 + 0.5);
    float seed = parameters.b * 19.0;
    vec2 p = uv * 2.0 - 1.0;
    // Gojo bodies are tagged with green = 250/255 (mode 8 from a slash blade has exactly 1.0).
    if (mode >= 7.5 && parameters.g < 0.995) {
        // Alpha channel selects: blue orb / white star streak / red orb / purple orb / Void shell.
        float kind = parameters.a;
        float r = length(p);
        if (kind > 0.79) {
            // Void shell; kind 0.8..1.0 carries visibility (open / shatter fade).
            float vis = clamp((kind - 0.8) / 0.2, 0.0, 1.0);
            if (age < 0.5) {
                // Seen from outside: a black sphere.
                fragColor = vec4(vec3(0.0, 0.0, 0.004), 0.97 * vis) * ColorModulator;
                return;
            }
            // Seen from inside: uv carry longitude / latitude over the whole sphere.
            float th = uv.y * 3.14159265;
            float ph = uv.x * 6.28318531;
            vec3 dir = vec3(sin(th) * cos(ph), cos(th), sin(th) * sin(ph));
            fragColor = vec4(space(dir), 0.985 * vis) * ColorModulator;
            return;
        }
        if (kind > 0.3 && kind < 0.4) {
            // Black hole seen face-on: age = visibility, seed channel = looping phase.
            float phase = parameters.b;
            float ang = atan(p.y, p.x);
            if (r > 1.0) discard;
            float horizon = 0.29;
            float spin = 1.3 / (r + 0.2);
            float p1 = fract(phase);
            float p2 = fract(phase + 0.5);
            float w1 = 1.0 - abs(2.0 * p1 - 1.0);
            float n = mix(vortex(ang - spin * p2 * 6.2831853, r, 5.3), vortex(ang - spin * p1 * 6.2831853, r, 0.0), w1);
            float inner = smoothstep(horizon - 0.01, horizon + 0.05, r);
            float dr = (r - 0.40) / 0.24;
            float body = inner * exp(-dr * dr) * 1.25 + inner * 0.22 * (1.0 - smoothstep(0.5, 0.95, r));
            float wisps = body * (0.3 + 1.1 * n * n);
            vec3 col = mix(vec3(0.34, 0.44, 0.72), vec3(0.95, 0.97, 1.0), clamp(n * 1.3 - 0.2, 0.0, 1.0)) * wisps;
            float dring = (r - 0.44) / 0.006;
            float dglow = (r - 0.44) / 0.035;
            float ring = exp(-dring * dring) * 1.3 + exp(-dglow * dglow) * 0.3;
            col += vec3(1.0, 0.98, 0.95) * ring;
            float edge = 1.0 - smoothstep(0.78, 1.0, r);
            float alphaBh = clamp(max(wisps * 1.2, ring), 0.0, 1.0) * edge;
            // Nothing escapes the horizon: it hides whatever is behind it.
            float hole = 1.0 - smoothstep(horizon - 0.01, horizon + 0.01, r);
            col *= 1.0 - hole;
            alphaBh = max(alphaBh, hole);
            alphaBh *= clamp(age, 0.0, 1.0);
            if (alphaBh < 0.004) discard;
            fragColor = vec4(col, alphaBh) * ColorModulator;
            return;
        }
        if (kind > 0.15 && kind < 0.3) {
            // Star streak: bright head at u = 1, fading tail toward u = 0; white or magenta.
            float across = exp(-p.y * p.y * 22.0);
            float along = pow(clamp(uv.x, 0.0, 1.0), 2.2);
            float hx = (uv.x - 0.93) * 9.0;
            float head = exp(-hx * hx) * exp(-p.y * p.y * 8.0);
            float a = clamp((across * along + head * 0.9) * age, 0.0, 1.0);
            if (a < 0.004) discard;
            vec3 tint = parameters.b > 0.62 ? vec3(1.0, 0.45, 0.78) : vec3(0.92, 0.96, 1.0);
            fragColor = vec4(tint * (0.7 + head), a) * ColorModulator;
            return;
        }
        vec3 col = kind < 0.15 ? vec3(0.16, 0.42, 1.0) : (kind < 0.6 ? vec3(1.0, 0.12, 0.14) : vec3(0.62, 0.18, 1.0));
        float core = exp(-r * r * 10.0);
        float glow = exp(-r * 2.8) * (1.0 - smoothstep(0.82, 1.0, r));
        float swirl = 0.5 + 0.5 * sin(atan(p.y, p.x) * 5.0 - age * 25.0 + r * 12.0);
        vec3 rgbo = col * glow * (0.65 + 0.35 * swirl) * 1.6 + vec3(1.0) * core;
        // Seed channel dims the orb when it would fill the viewer's screen.
        float alphao = clamp(max(core, glow * 0.95), 0.0, 1.0) * (1.0 - parameters.b * 0.85);
        if (alphao < 0.004) discard;
        fragColor = vec4(rgbo, alphao) * ColorModulator;
        return;
    }
    // Black Flash variants are tagged with alpha < 0.75, crimson clash slashes with 0.75..0.95
    // (normal blades use 1.0).
    bool blackFlash = parameters.a < 0.75;
    bool crimson = parameters.a >= 0.75 && parameters.a < 0.95;
    if (mode >= 4.0) {
        float t = age * 6.283185;
        float wave = sin(p.x*17.0-t*3.0+seed)*.12 + sin(p.x*37.0+t*2.0)*.055;
        float envelope = pow(max(0.0,1.0-abs(p.x)),.5);
        float flame = abs(p.y-wave*envelope);
        float core = exp(-flame*22.0/max(.1,envelope));
        float tongue = exp(-flame*5.0/max(.1,envelope))*(.7+.3*sin(p.x*29.0-t*4.0+seed));
        vec3 rgb = vec3(1.0,.18,.012)*tongue + vec3(1.0,.77,.22)*core;
        float alpha = max(core,tongue*.85)*envelope;
        if(mode==5.0){
            float radius=length(p);float ripple=sin(atan(p.y,p.x)*12.0+t*2.0)*.025+sin(radius*35.0-t*3.0)*.02;
            alpha=(1.0-smoothstep(.78,.99,radius+ripple))*.95;
            rgb=vec3(.012,.009,.025)+vec3(.12,.035,.17)*pow(max(0.0,sin(radius*28.0-t*2.0)),12.0)*.4;
        }
        if(mode==6.0){rgb=vec3(.06,.42,1.0)*tongue+vec3(.82,.96,1.0)*core; alpha=max(core,tongue*.9)*envelope;}
        if(mode==7.0){float d=abs(length(p)-(.12+age*.85));alpha=exp(-d*65.0)*(1.0-age);rgb=vec3(.85,.93,1.0);
            if(blackFlash){float glow=exp(-d*22.0);alpha=max(exp(-d*50.0),glow*.75)*(1.0-age);rgb=mix(vec3(.9,.03,.06),vec3(.01,0.0,0.0),exp(-d*90.0));}}
        if(alpha<.004)discard;fragColor=vec4(rgb,alpha)*ColorModulator;return;
    }
    float taper = pow(max(0.0, 1.0 - abs(p.x)), 0.62);
    float open = smoothstep(0.0, 0.07, age);
    float close = 1.0 - smoothstep(0.58, 1.0, age);
    float bend = 0.025 * sin(p.x * 8.0 + seed) * taper;
    float y = p.y - bend;
    float width = (mode == 2.0 ? 0.20 : 0.26) * taper * (0.35 + 0.65 * open) * close;
    float d = abs(y) - width;
    float aa = max(fwidth(d) * 0.8, 0.0015);
    float body = 1.0 - smoothstep(-aa, aa, d);
    float edge = 1.0 - smoothstep(aa, aa * 2.5 + 0.015 * taper, abs(d));
    float halo = exp(-max(d, 0.0) * 22.0) * (1.0 - body) * taper;
    float filament = exp(-abs(y + width * 0.35) / max(aa, 0.006)) * 0.48 * body;
    float ripple = 0.98 + 0.02 * sin(p.x * 92.0 - age * 28.0 + seed);
    vec3 rim = blackFlash ? vec3(1.0, 0.06, 0.08) : mix(vec3(1.0, 0.93, 0.89), vec3(0.75, 0.92, 1.0), step(1.5, mode));
    vec3 rgb = vec3(0.017, 0.009, 0.027);
    rgb = mix(rgb, rim, max(edge, filament) * ripple);
    rgb += halo * vec3(0.36, 0.12, 0.22) * 0.55;
    if (crimson) {
        rgb = mix(vec3(0.92, 0.03, 0.08), vec3(1.0, 0.93, 0.93), clamp(max(edge * 0.6, filament * 1.6), 0.0, 1.0));
        rgb += halo * vec3(0.7, 0.0, 0.05);
    }
    if (mode == 2.0) {
        float red = exp(-abs(y - width - 0.045 * taper) / max(aa, 0.012));
        float cyan = exp(-abs(y + width + 0.045 * taper) / max(aa, 0.012));
        rgb += vec3(0.9, 0.06, 0.13) * red * 0.55;
        rgb += vec3(0.04, 0.65, 0.85) * cyan * 0.55;
    }
    float sweep = 1.0 - smoothstep(-1.0 + age * 32.0, -0.7 + age * 32.0, p.x);
    float alpha = max(body, max(edge, halo * 0.24)) * close * sweep;
    alpha *= smoothstep(0.0, 0.012, taper) * (blackFlash || crimson ? 1.0 : parameters.a);
    if (alpha < 0.004) discard;
    fragColor = vec4(rgb, alpha) * ColorModulator;
}
