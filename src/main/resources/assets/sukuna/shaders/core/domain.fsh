#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D SceneDepth;
uniform sampler2D MirrorColor;
layout(std140) uniform DomainUniforms {
    mat4 InverseViewProjection;
    vec3 CameraRelative;
    vec4 DomainData;
    // Overlapping Unlimited Void: centre relative to the shrine, current radius (0 = none).
    vec4 VoidData;
    // Depth buffer value -> projection z: ndc = x + y * depth (26.3 depth is reversed: sky = 0).
    vec4 DepthMap;
};
layout(location=0) in vec2 screenUv;
layout(location=0) out vec4 fragColor;

float hash(vec3 p) {
    p = fract(p * .1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}
float noise(vec3 p) {
    vec3 i = floor(p), f = fract(p);
    f = f*f*(3.0-2.0*f);
    return mix(mix(mix(hash(i), hash(i+vec3(1,0,0)),f.x),
                   mix(hash(i+vec3(0,1,0)),hash(i+vec3(1,1,0)),f.x),f.y),
               mix(mix(hash(i+vec3(0,0,1)),hash(i+vec3(1,0,1)),f.x),
                   mix(hash(i+vec3(0,1,1)),hash(i+vec3(1,1,1)),f.x),f.y),f.z);
}
float cloud(vec3 p) {
    float result = 0.0, weight = .54;
    for (int i=0;i<4;i++) {
        result += weight * noise(p);
        p = p * 2.03 + vec3(7.1, 3.7, 5.2);
        weight *= .48;
    }
    return result;
}
float fbm(vec3 p) {
    float sum = 0.0, w = 0.5;
    for (int i = 0; i < 5; i++) {
        sum += w * noise(p);
        p = p * 2.03 + vec3(1.7, 9.2, 3.1);
        w *= 0.5;
    }
    return sum;
}
vec3 reconstruct(float d) {
    vec4 p = InverseViewProjection * vec4(screenUv*2.0-1.0, DepthMap.x + DepthMap.y * d, 1.0);
    return p.xyz / p.w;
}

// The crimson mist where the blood sky meets the water: the dome's waterline and the far water share it.
const vec3 MIST = vec3(.30, .026, .046);

// Blood-red sky: luminous crimson cloud banks with dark gaps between them, lit scarlet edges and
// thin veins, sinking into the crimson mist at the horizon.
vec3 sky(vec3 direction, float t) {
    vec3 p = direction * vec3(3.2, 2.2, 3.2);
    float broad = cloud(p + vec3(t*.020, -t*.010, 0));
    float banks = cloud(p*1.9 + vec3(broad*.9, t*.012, -t*.008));
    float detail = cloud(p*4.3 + vec3(0, t*.020, broad));
    float lit = smoothstep(.36, .76, broad*.55 + banks*.45);
    vec3 color = mix(vec3(.05, .0, .008), vec3(.62, .015, .05), lit);
    color = mix(color, vec3(1.0, .22, .12), pow(clamp(detail*lit*1.25 - .25, 0.0, 1.0), 2.0) * .55);
    color += vec3(.35, .01, .03) * pow(clamp(detail*1.3, 0.0, 1.0), 3.0) * .4;
    float h = clamp(direction.y, 0.0, 1.0);
    color *= .8 + .35*smoothstep(.0, .35, h) - .2*smoothstep(.6, 1.0, h);
    return mix(MIST, color, smoothstep(.0, .16, h));
}

// The cosmos inside Unlimited Void (the same sky its shell shows), for the Void's half of a clash.
vec3 space(vec3 d) {
    float neb = fbm(d * 2.2);
    float wisp = fbm(d * 4.6 + vec3(neb * 1.6));
    float band = smoothstep(0.42, 0.8, wisp);
    vec3 col = vec3(0.006, 0.010, 0.032);
    col += vec3(0.10, 0.16, 0.40) * neb * neb * neb * 1.3;
    col += vec3(0.32, 0.12, 0.42) * band * neb * 0.45;
    for (int layer = 0; layer < 2; layer++) {
        float scale = layer == 0 ? 90.0 : 190.0;
        vec3 g = d * scale;
        vec3 f = fract(g) - 0.5;
        float h = hash(floor(g) + float(layer) * 17.0);
        float size = 0.10 + 0.14 * fract(h * 7.13);
        float star = step(layer == 0 ? 0.955 : 0.93, h) * (1.0 - smoothstep(0.0, size, length(f)));
        vec3 tone = mix(vec3(0.72, 0.8, 1.0), vec3(1.0, 0.96, 0.92), fract(h * 13.7));
        col += star * tone * (layer == 0 ? 1.3 : 0.6);
    }
    vec3 axis = normalize(vec3(0.32, 0.86, 0.40));
    float across = dot(d, axis);
    float galaxy = exp(-across * across * 26.0);
    vec3 core = normalize(cross(axis, vec3(0.0, 0.0, 1.0)));
    float toward = 0.5 + 0.5 * dot(d, core);
    float lanes = smoothstep(0.45, 0.72, fbm(d * 7.5 + vec3(3.1, 0.7, 5.3))) * exp(-across * across * 90.0);
    float glow = galaxy * (0.35 + 0.65 * toward * toward) * (1.0 - 0.85 * lanes);
    col += mix(vec3(0.22, 0.24, 0.48), vec3(0.95, 0.90, 0.98), toward * galaxy) * glow * 0.55;
    col += vec3(1.0, 0.42, 0.72) * galaxy * smoothstep(0.55, 0.8, fbm(d * 3.4 + vec3(neb * 2.0))) * 0.22;
    col += vec3(0.92, 0.95, 1.0) * galaxy * smoothstep(0.62, 0.85, wisp) * 0.18;
    return col;
}

// Domain clash: signed distance (blocks) from the seam, the radical plane of the two spheres.
// Negative on the Void's side.
float seam(vec3 x) {
    if (VoidData.w <= 0.0) return 1e6;
    vec3 d = x - VoidData.xyz;
    float r = DomainData.x;
    float power = (dot(d, d) - VoidData.w * VoidData.w) - (dot(x, x) - r * r);
    return power / max(1e-3, 2.0 * length(VoidData.xyz));
}
// The line where the two domains meet: crimson on the shrine's side, white-blue on the Void's,
// a white-hot core between them, flickering along its length.
vec4 seamGlow(vec3 x, float s, float t, float viewDistance) {
    // Wider with distance, so the seam still reads as a bright line far across the domain.
    float a = abs(s) / (.7 + .03 * viewDistance);
    float flicker = .65 + .35 * noise(vec3(x.xz * .45, t * 4.0) + x.y * .3);
    float glow = exp(-a * 1.6) * flicker;
    vec3 side = s >= 0.0 ? vec3(1.0, .10, .16) : vec3(.55, .72, 1.0);
    vec3 color = mix(side, vec3(1.0, .93, .96), exp(-a * 6.0));
    return vec4(color, clamp(glow, 0.0, 1.0));
}

// The shrine's sky and water are drawn a little see-through.
const float SKY_OPACITY = .9;
const float WATER_OPACITY = .84;

void main() {
    vec3 ro = CameraRelative;
    vec3 rd = normalize(reconstruct(.0001));
    float depth = texture(SceneDepth, screenUv).r;
    vec3 scene = ro + reconstruct(depth);
    float sceneDistance = depth <= .000001 ? 1e6 : length(scene-ro);
    float r = DomainData.x;
    float t = DomainData.y;
    float b = dot(ro,rd), c = dot(ro,ro)-r*r;
    float discriminant = b*b-c;
    if (discriminant <= 0.0) discard;
    float nearHit = -b-sqrt(discriminant), farHit = -b+sqrt(discriminant);
    if (farHit <= 0.0) discard;
    // 只在扩散前沿覆盖的范围合成；外侧仍可看见环形展开。
    float shellDistance = nearHit > 0.0 ? nearHit : farHit;
    vec3 shell = ro + rd*shellDistance;
    float shellAlpha = smoothstep(0.0, 1.0, r) * smoothstep(0.0, 1.2, farHit-max(nearHit,0.0));
    float floorDistance = rd.y < -.00001 && ro.y > 0.0 ? -ro.y/rd.y : 1e6;
    vec3 floorPoint = ro + rd*floorDistance;
    float floorMask = (1.0-smoothstep(max(0.0,r-.2),r,length(floorPoint.xz)))
                    * smoothstep(0.0,.45,r);
    vec3 color;
    float alpha;
    // From outside, the dome's near surface hides the water within.
    bool outside = nearHit > 0.0;
    if (!outside && floorDistance < farHit && floorMask > .001) {
        // 不覆盖镜面之前的模型、人物或高地；地面共面误差按高度判断。
        float tolerance = .065 / max(.05,abs(rd.y));
        if (sceneDistance < floorDistance-tolerance) discard;
        float s = seam(floorPoint);
        vec2 p = floorPoint.xz;
        if (s < 0.0) {
            // The Void's half of a clash has no ground either: its cosmos goes on below.
            if (length(floorPoint - VoidData.xyz) > VoidData.w) discard;
            vec4 g = seamGlow(floorPoint, s, t, floorDistance);
            fragColor = vec4(mix(space(rd), g.rgb, g.a * .9), DomainData.z);
            return;
        }
        float dist = length(p);
        // Long, low swells only bend the reflection; the water mirrors the blood sky and the shrine.
        vec2 swell = vec2(noise(vec3(p*.11 + 3.1, t*.15)) - .5, noise(vec3(p*.11 - 7.3, t*.13)) - .5) * .16;
        vec3 reflected = normalize(vec3(rd.x + swell.x, -rd.y, rd.z + swell.y));
        float fresnel = .32 + .68 * pow(1.0 - clamp(-rd.y, 0.0, 1.0), 3.0);
        float depthTone = smoothstep(.2, .8, cloud(vec3(p*.05, t*.01)));
        color = mix(vec3(.002, .03, .04), vec3(.006, .21, .25), depthTone);
        color += vec3(.006, .16, .18) * pow(cloud(vec3(p*.18 + swell*6.0, t*.012)), 2.0);
        // Turquoise water mirroring the crimson clouds: the reflection grows towards the horizon.
        color = mix(color, sky(reflected, t) * vec3(.9, .82, .88) + vec3(.0, .04, .05), fresnel * .78);
        vec2 texel = 1.0 / vec2(textureSize(MirrorColor,0));
        vec4 mirror = texture(MirrorColor, clamp(screenUv + swell * texel * 4.0, texel, 1.0-texel));
        color = mix(color, mirror.rgb * vec3(.68, .86, .9), mirror.a * .86);
        // The far third of the water fades into the same crimson mist as the dome's waterline.
        color = mix(color, MIST, smoothstep(r * .62, r * .98, dist) * .85);
        alpha = floorMask * mix(WATER_OPACITY, 1.0, smoothstep(r * .6, r * .95, dist));
        if (s < 3.0) {
            vec4 g = seamGlow(floorPoint, s, t, floorDistance);
            color = mix(color, g.rgb, g.a * .9);
            alpha = max(alpha, g.a);
        }
    } else {
        if (sceneDistance < shellDistance-.08) discard;
        float s = seam(shell);
        if (s < 0.0) {
            // The Void's own shell shows here; only the seam's glow is laid over it.
            if (s < -3.0) discard;
            vec4 g = seamGlow(shell, s, t, shellDistance);
            if (g.a < .01) discard;
            fragColor = vec4(g.rgb, g.a * .9 * DomainData.z);
            return;
        }
        // 下半球维持冷色深渊，保证飞行俯视与外侧观察没有红色地板。
        vec3 direction = normalize(shell);
        color = direction.y >= 0.0 ? sky(direction,t)
              : mix(MIST, mix(vec3(.002,.02,.029),vec3(.004,.18,.23),cloud(direction*5.0+vec3(t*.018))), smoothstep(.0,.2,-direction.y));
        alpha = shellAlpha * SKY_OPACITY;
        if (outside) {
            // Seen from outside: a blood-red sphere whose rim burns brighter.
            float rim = pow(1.0 - abs(dot(direction, rd)), 3.0);
            color = sky(vec3(direction.x, abs(direction.y) * .7 + .3, direction.z), t) * 1.1 + vec3(.55, .03, .06) * rim;
            alpha = shellAlpha * mix(.94, 1.0, rim);
        }
        if (s < 3.0) {
            vec4 g = seamGlow(shell, s, t, shellDistance);
            color = mix(color, g.rgb, g.a * .9);
            alpha = max(alpha, g.a);
        }
    }
    fragColor = vec4(color, alpha * DomainData.z);
}
