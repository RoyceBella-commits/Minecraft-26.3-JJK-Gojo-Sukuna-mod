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
vec3 reconstruct(float d) {
    vec4 p = InverseViewProjection * vec4(screenUv*2.0-1.0, d, 1.0);
    return p.xyz / p.w;
}
vec3 sky(vec3 direction, float t) {
    vec3 p = direction * vec3(4.0, 2.5, 4.0);
    float broad = cloud(p + vec3(t*.022, -t*.012, 0));
    float detail = cloud(p*2.0 + vec3(broad*.8, 0, t*.016));
    float veins = pow(clamp(detail*1.3,0.0,1.0), 2.3);
    vec3 color = mix(vec3(.085,.001,.008), vec3(.59,.008,.036), broad);
    color += vec3(.31,.007,.019) * veins;
    // 地平线压暗，天顶带连续流动的血红云光，不调制全屏亮度。
    color *= .72 + .28*smoothstep(-.1,.5,direction.y);
    return color;
}
// Domain clash: points on the Void's side of the seam (radical plane) belong to Unlimited Void.
bool voidSide(vec3 x) {
    if (VoidData.w <= 0.0) return false;
    vec3 d = x - VoidData.xyz;
    float r = DomainData.x;
    return dot(d, d) - VoidData.w * VoidData.w < dot(x, x) - r * r;
}
// The shrine's ground and sky are drawn a little see-through.
const float DOMAIN_OPACITY = 0.78;

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
    float floorMask = (1.0-smoothstep(max(0.0,r-1.25),r,length(floorPoint.xz)))
                    * smoothstep(0.0,.45,r);
    vec3 color;
    float alpha;
    if (floorDistance < farHit && floorMask > .001) {
        // 不覆盖镜面之前的模型、人物或高地；地面共面误差按高度判断。
        float tolerance = .065 / max(.05,abs(rd.y));
        if (sceneDistance < floorDistance-tolerance) discard;
        if (voidSide(floorPoint)) discard;
        vec2 p = floorPoint.xz;
        float broad = cloud(vec3(p*.16,t*.018));
        float flow = cloud(vec3(p*.32+vec2(broad*.7,t*.015),t*.012));
        color = mix(vec3(.002,.021,.030),vec3(.004,.29,.36),smoothstep(.15,.78,broad));
        color += vec3(.005,.12,.15)*pow(flow,2.0);
        float fresnel = pow(1.0-clamp(-rd.y,0.0,1.0),4.0);
        color += sky(vec3(rd.x,-rd.y,rd.z),t)*fresnel*.12;
        // 亚像素波纹只轻微扰动倒影，远处逐渐减弱，保持模型轮廓可辨。
        vec2 texel = 1.0 / vec2(textureSize(MirrorColor,0));
        vec2 distortion = vec2(sin(p.y*2.2+t*.7),cos(p.x*1.8-t*.5)) * texel * .65;
        vec4 mirror = texture(MirrorColor,clamp(screenUv+distortion,texel,1.0-texel));
        vec3 reflected = mirror.rgb * vec3(.65,.86,.9);
        color = mix(color, reflected, mirror.a*.86);
        float rim = exp(-abs(length(p)-(r-.5))*3.0)*(1.0-smoothstep(21.0,22.0,r));
        color += vec3(.04,.42,.49)*rim*.32;
        alpha = floorMask * DOMAIN_OPACITY;
    } else {
        if (sceneDistance < shellDistance-.08) discard;
        if (voidSide(shell)) discard;
        // 下半球维持冷色深渊，保证飞行俯视与外侧观察没有红色地板。
        vec3 direction = normalize(shell);
        color = direction.y >= 0.0 ? sky(direction,t)
              : mix(vec3(.002,.02,.029),vec3(.004,.18,.23),cloud(direction*5.0+vec3(t*.018)));
        alpha = shellAlpha * DOMAIN_OPACITY;
    }
    fragColor = vec4(color,alpha*DomainData.z);
}
