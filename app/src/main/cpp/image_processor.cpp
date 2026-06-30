#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <cmath>
#include <cstring>
#include <algorithm>
#include <vector>

#define LOG_TAG "ProEdit"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

static inline uint8_t clampByte(float v) {
    return static_cast<uint8_t>(v < 0.f ? 0.f : v > 255.f ? 255.f : v);
}

// ── HSL conversion ─────────────────────────────────────────────────────────

static void rgbToHsl(float r, float g, float b, float& h, float& s, float& l) {
    r /= 255.f; g /= 255.f; b /= 255.f;
    float mx = std::max({r, g, b}), mn = std::min({r, g, b});
    float d = mx - mn;
    l = (mx + mn) * 0.5f;
    if (d < 1e-6f) { h = s = 0.f; return; }
    s = d / (1.f - std::fabs(2.f * l - 1.f));
    if (mx == r)       h = 60.f * std::fmod((g - b) / d, 6.f);
    else if (mx == g)  h = 60.f * ((b - r) / d + 2.f);
    else               h = 60.f * ((r - g) / d + 4.f);
    if (h < 0.f) h += 360.f;
}

static float hue2rgb(float p, float q, float t) {
    if (t < 0.f) t += 1.f;
    if (t > 1.f) t -= 1.f;
    if (t < 1.f/6.f) return p + (q - p) * 6.f * t;
    if (t < 0.5f)    return q;
    if (t < 2.f/3.f) return p + (q - p) * (2.f/3.f - t) * 6.f;
    return p;
}

static void hslToRgb(float h, float s, float l, float& r, float& g, float& b) {
    if (s < 1e-6f) { r = g = b = l * 255.f; return; }
    float q = l < 0.5f ? l * (1.f + s) : l + s - l * s;
    float p = 2.f * l - q;
    h /= 360.f;
    r = hue2rgb(p, q, h + 1.f/3.f) * 255.f;
    g = hue2rgb(p, q, h)            * 255.f;
    b = hue2rgb(p, q, h - 1.f/3.f) * 255.f;
}

// ── Box blur for sharpness ──────────────────────────────────────────────────

static std::vector<uint8_t> boxBlur3(const uint8_t* src, int width, int height, int stride) {
    std::vector<uint8_t> out(width * height * 4, 0);
    for (int y = 1; y < height - 1; y++) {
        for (int x = 1; x < width - 1; x++) {
            float sr = 0, sg = 0, sb = 0;
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    const uint8_t* p = src + (y + dy) * stride + (x + dx) * 4;
                    sr += p[0]; sg += p[1]; sb += p[2];
                }
            }
            int idx = (y * width + x) * 4;
            out[idx]   = (uint8_t)(sr / 9.f);
            out[idx+1] = (uint8_t)(sg / 9.f);
            out[idx+2] = (uint8_t)(sb / 9.f);
            out[idx+3] = 255;
        }
    }
    return out;
}

// ── JNI: applyAdjustmentsInPlace ───────────────────────────────────────────
// bitmap  : mutable ARGB_8888 — modified in-place
// mask    : byte[] of size w*h, 0=skip 255=full, nullable
// All float params: -1.0 to +1.0 range
extern "C" JNIEXPORT void JNICALL
Java_com_rohan_proedit_processing_NativeProcessor_applyAdjustmentsInPlace(
        JNIEnv* env, jobject,
        jobject bitmap,
        jbyteArray maskArray,
        jfloat brightness, jfloat contrast,  jfloat saturation,
        jfloat temperature,jfloat highlights, jfloat shadows,
        jfloat sharpness,  jfloat ambiance) {

    AndroidBitmapInfo info;
    void* pixels = nullptr;
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) { LOGE("getInfo failed"); return; }
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("applyAdjustments: unsupported format %d (need RGBA_8888)", info.format);
        return;
    }
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) { LOGE("lock failed"); return; }

    const int W = (int)info.width;
    const int H = (int)info.height;
    const int stride = (int)info.stride;

    // Mask
    jbyte* mask = nullptr;
    if (maskArray != nullptr) {
        mask = env->GetByteArrayElements(maskArray, nullptr);
    }

    // Pre-compute blur for sharpness
    std::vector<uint8_t> blurred;
    if (std::fabs(sharpness) > 0.01f) {
        blurred = boxBlur3((uint8_t*)pixels, W, H, stride);
    }

    const float briVal   = brightness  * 128.f;
    const float conFact  = contrast > 0.f ? (1.f + contrast * 3.f) : (1.f + contrast);
    const float tempVal  = temperature * 30.f;
    const float satScale = 1.f + saturation * 2.f;

    for (int y = 0; y < H; y++) {
        uint8_t* row = (uint8_t*)pixels + y * stride;
        for (int x = 0; x < W; x++) {
            uint8_t* p = row + x * 4;

            // Mask alpha 0.0–1.0
            float ma = mask ? ((uint8_t)mask[y * W + x]) / 255.f : 1.f;
            if (ma < 0.004f) continue;

            float r = p[0], g = p[1], b = p[2];

            // ── Brightness ────────────────────────────────────────────────
            r += briVal * ma;
            g += briVal * ma;
            b += briVal * ma;

            // ── Contrast ──────────────────────────────────────────────────
            if (std::fabs(conFact - 1.f) > 0.01f) {
                float cf = 1.f + (conFact - 1.f) * ma;
                r = (r - 128.f) * cf + 128.f;
                g = (g - 128.f) * cf + 128.f;
                b = (b - 128.f) * cf + 128.f;
            }

            // ── Temperature (warm/cool) ───────────────────────────────────
            if (std::fabs(tempVal) > 0.5f) {
                r += tempVal * ma;
                b -= tempVal * ma;
            }

            r = std::max(0.f, std::min(255.f, r));
            g = std::max(0.f, std::min(255.f, g));
            b = std::max(0.f, std::min(255.f, b));

            // ── Saturation (HSL) ──────────────────────────────────────────
            if (std::fabs(saturation) > 0.01f) {
                float h, s, l;
                rgbToHsl(r, g, b, h, s, l);
                float ns = s * (1.f + (satScale - 1.f) * ma);
                ns = std::max(0.f, std::min(1.f, ns));
                float nr, ng, nb;
                hslToRgb(h, ns, l, nr, ng, nb);
                r = r + (nr - r) * ma;
                g = g + (ng - g) * ma;
                b = b + (nb - b) * ma;
            }

            // ── Highlights & Shadows ──────────────────────────────────────
            if (std::fabs(highlights) > 0.01f || std::fabs(shadows) > 0.01f) {
                float lum  = (0.299f * r + 0.587f * g + 0.114f * b) / 255.f;
                float lumN = lum * lum; // quadratic: weights bright pixels

                if (std::fabs(highlights) > 0.01f) {
                    float hf = lumN * highlights * 60.f * ma;
                    r += hf; g += hf; b += hf;
                }
                if (std::fabs(shadows) > 0.01f) {
                    float invLumN = (1.f - lum) * (1.f - lum);
                    float sf = invLumN * shadows * 60.f * ma;
                    r += sf; g += sf; b += sf;
                }
            }

            // ── Sharpness (unsharp mask) ──────────────────────────────────
            if (!blurred.empty() && x > 0 && x < W-1 && y > 0 && y < H-1) {
                int idx = (y * W + x) * 4;
                float amt = sharpness * 1.5f * ma;
                r += (r - blurred[idx])   * amt;
                g += (g - blurred[idx+1]) * amt;
                b += (b - blurred[idx+2]) * amt;
            }

            // ── Ambiance (local contrast boost) ──────────────────────────
            if (std::fabs(ambiance) > 0.01f) {
                float lum = (0.299f * r + 0.587f * g + 0.114f * b) / 255.f;
                float delta = (lum - 0.5f) * ambiance * 50.f * ma;
                r += delta; g += delta; b += delta;
            }

            p[0] = clampByte(r);
            p[1] = clampByte(g);
            p[2] = clampByte(b);
        }
    }

    if (mask) env->ReleaseByteArrayElements(maskArray, mask, JNI_ABORT);
    AndroidBitmap_unlockPixels(env, bitmap);
}

// ── JNI: applyFilterInPlace ────────────────────────────────────────────────
// filterIndex: 0=Original,1=Vivid,2=Dramatic,3=B&W,4=Cinematic,5=Aqua,6=Matte,7=Golden
extern "C" JNIEXPORT void JNICALL
Java_com_rohan_proedit_processing_NativeProcessor_applyFilterInPlace(
        JNIEnv* env, jobject, jobject bitmap, jint filterIndex) {

    AndroidBitmapInfo info;
    void* pixels = nullptr;
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) return;
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) { LOGE("applyFilter: unsupported format %d", info.format); return; }
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) return;

    const int W = (int)info.width, H = (int)info.height, stride = (int)info.stride;

    for (int y = 0; y < H; y++) {
        uint8_t* row = (uint8_t*)pixels + y * stride;
        for (int x = 0; x < W; x++) {
            uint8_t* p = row + x * 4;
            float r = p[0], g = p[1], b = p[2];

            switch (filterIndex) {
                case 1: { // Vivid: boost saturation + contrast
                    float h, s, l;
                    rgbToHsl(r, g, b, h, s, l);
                    s = std::min(1.f, s * 1.5f);
                    float nr, ng, nb; hslToRgb(h, s, l, nr, ng, nb);
                    r = (nr - 128.f) * 1.2f + 128.f;
                    g = (ng - 128.f) * 1.2f + 128.f;
                    b = (nb - 128.f) * 1.2f + 128.f;
                    break;
                }
                case 2: { // Dramatic: high contrast, slight desaturation
                    r = (r - 128.f) * 1.5f + 128.f;
                    g = (g - 128.f) * 1.5f + 128.f;
                    b = (b - 128.f) * 1.5f + 128.f;
                    float h, s, l; rgbToHsl(r, g, b, h, s, l);
                    s *= 0.7f;
                    float nr, ng, nb; hslToRgb(h, s, l, nr, ng, nb);
                    r = nr; g = ng; b = nb;
                    break;
                }
                case 3: { // B&W
                    float lum = 0.299f*r + 0.587f*g + 0.114f*b;
                    r = g = b = lum;
                    break;
                }
                case 4: { // Cinematic: teal shadows, orange highlights
                    float lum = (0.299f*r + 0.587f*g + 0.114f*b) / 255.f;
                    // Teal shadows
                    r -= (1.f - lum) * 15.f;
                    b += (1.f - lum) * 20.f;
                    // Orange highlights
                    r += lum * 20.f;
                    b -= lum * 15.f;
                    break;
                }
                case 5: { // Aqua: boost blues/teals
                    b = std::min(255.f, b * 1.2f + 15.f);
                    g = std::min(255.f, g * 1.05f + 5.f);
                    r = std::max(0.f, r * 0.95f);
                    break;
                }
                case 6: { // Matte: lifted blacks, flat look
                    r = r * 0.85f + 30.f;
                    g = g * 0.85f + 25.f;
                    b = b * 0.85f + 35.f;
                    break;
                }
                case 7: { // Golden hour: warm tones
                    r = std::min(255.f, r + 20.f);
                    g = std::min(255.f, g + 10.f);
                    b = std::max(0.f, b - 15.f);
                    break;
                }
                default: break;
            }

            p[0] = clampByte(r);
            p[1] = clampByte(g);
            p[2] = clampByte(b);
        }
    }
    AndroidBitmap_unlockPixels(env, bitmap);
}

// ── JNI: getMaskOverlay ────────────────────────────────────────────────────
// Returns ARGB int array for red overlay visualization
extern "C" JNIEXPORT jintArray JNICALL
Java_com_rohan_proedit_processing_NativeProcessor_getMaskOverlay(
        JNIEnv* env, jobject,
        jbyteArray maskArray, jint width, jint height) {

    jint total = width * height;
    jintArray result = env->NewIntArray(total);
    if (!result) return nullptr;

    jbyte* mask = env->GetByteArrayElements(maskArray, nullptr);
    std::vector<jint> overlay(total);

    for (int i = 0; i < total; i++) {
        uint8_t m = (uint8_t)mask[i];
        if (m > 0) {
            // Red semi-transparent: 0xAARRGGBB
            int alpha = (int)(m * 0.55f);
            overlay[i] = (alpha << 24) | 0x00CC2200;
        } else {
            overlay[i] = 0x00000000;
        }
    }

    env->SetIntArrayRegion(result, 0, total, overlay.data());
    env->ReleaseByteArrayElements(maskArray, mask, JNI_ABORT);
    return result;
}
