#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <cmath>
#include <cstring>
#include <algorithm>
#include <vector>
#include <queue>

#define LOG_TAG "ProEdit-Mask"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ── JNI: paintMask (manual brush) ─────────────────────────────────────────
// Paints a soft-edged circle into the mask byte array
extern "C" JNIEXPORT void JNICALL
Java_com_rohan_proedit_processing_NativeProcessor_paintMask(
        JNIEnv* env, jobject,
        jbyteArray maskArray, jint width, jint height,
        jfloat cx, jfloat cy, jfloat radius, jboolean add) {

    jbyte* mask = env->GetByteArrayElements(maskArray, nullptr);

    int x0 = std::max(0, (int)(cx - radius));
    int x1 = std::min(width  - 1, (int)(cx + radius));
    int y0 = std::max(0, (int)(cy - radius));
    int y1 = std::min(height - 1, (int)(cy + radius));

    const float innerR = radius * 0.75f; // hard core
    const float outerR = radius;

    for (int y = y0; y <= y1; y++) {
        for (int x = x0; x <= x1; x++) {
            float dx = x - cx, dy = y - cy;
            float dist = std::sqrt(dx*dx + dy*dy);
            if (dist > outerR) continue;

            // Soft feather at edges
            float alpha;
            if (dist <= innerR) {
                alpha = 1.f;
            } else {
                alpha = 1.f - (dist - innerR) / (outerR - innerR);
                alpha = std::max(0.f, std::min(1.f, alpha));
            }

            int idx = y * width + x;
            uint8_t cur = (uint8_t)mask[idx];
            if (add) {
                uint8_t nv = (uint8_t)(alpha * 255.f);
                mask[idx] = (jbyte)std::max((int)cur, (int)nv);
            } else {
                uint8_t nv = (uint8_t)((1.f - alpha) * 255.f);
                mask[idx] = (jbyte)std::min((int)cur, (int)nv);
            }
        }
    }

    env->ReleaseByteArrayElements(maskArray, mask, 0);
}

// ── JNI: smartBrushSelect ─────────────────────────────────────────────────
// Color-similarity BFS flood fill from touch point
// pixels: ARGB int array from Bitmap.getPixels()
extern "C" JNIEXPORT void JNICALL
Java_com_rohan_proedit_processing_NativeProcessor_smartBrushSelect(
        JNIEnv* env, jobject,
        jintArray pixelArray, jbyteArray maskArray,
        jint width, jint height,
        jfloat cx, jfloat cy, jfloat radius,
        jfloat tolerance, jboolean add) {

    jint*  pixels = env->GetIntArrayElements(pixelArray, nullptr);
    jbyte* mask   = env->GetByteArrayElements(maskArray,  nullptr);

    int sx = (int)cx, sy = (int)cy;
    if (sx < 0 || sx >= width || sy < 0 || sy >= height) {
        env->ReleaseIntArrayElements(pixelArray, pixels, JNI_ABORT);
        env->ReleaseByteArrayElements(maskArray, mask, JNI_ABORT);
        return;
    }

    // Seed color (ARGB: A=byte3, R=byte2, G=byte1, B=byte0 on Android)
    jint seed = pixels[sy * width + sx];
    float sR = (float)((seed >> 16) & 0xFF);
    float sG = (float)((seed >>  8) & 0xFF);
    float sB = (float)( seed        & 0xFF);

    float tolSq = tolerance * 441.f; // 441 = 3*147 ≈ sqrt(3)*255
    tolSq = tolSq * tolSq;

    float r2 = radius * radius;

    // BFS
    std::vector<bool> visited(width * height, false);
    std::queue<std::pair<int,int>> q;
    q.push({sx, sy});
    visited[sy * width + sx] = true;

    const int dx4[] = {1,-1,0,0};
    const int dy4[] = {0,0,1,-1};

    while (!q.empty()) {
        auto [x, y] = q.front(); q.pop();

        // Distance from brush centre
        float distSq = (x - cx)*(x - cx) + (y - cy)*(y - cy);
        float edgeFactor = 1.f;
        if (distSq > r2) {
            // Allow flood fill slightly beyond radius but reduce alpha
            if (distSq > r2 * 1.5f) continue;
            edgeFactor = 1.f - (std::sqrt(distSq) - radius) / (radius * 0.5f);
            edgeFactor = std::max(0.f, edgeFactor);
        }

        jint px  = pixels[y * width + x];
        float pR = (float)((px >> 16) & 0xFF);
        float pG = (float)((px >>  8) & 0xFF);
        float pB = (float)( px        & 0xFF);
        float colorDistSq = (pR-sR)*(pR-sR) + (pG-sG)*(pG-sG) + (pB-sB)*(pB-sB);

        if (colorDistSq > tolSq) continue;

        // Smooth alpha based on color distance
        float similarity = 1.f - std::sqrt(colorDistSq) / std::sqrt(tolSq + 1e-6f);
        similarity = std::max(0.f, std::min(1.f, similarity));
        uint8_t alpha = (uint8_t)(similarity * edgeFactor * 255.f);

        int idx = y * width + x;
        if (add) {
            mask[idx] = (jbyte)std::max((int)(uint8_t)mask[idx], (int)alpha);
        } else {
            uint8_t inv = 255 - alpha;
            mask[idx] = (jbyte)std::min((int)(uint8_t)mask[idx], (int)inv);
        }

        // Expand BFS
        for (int d = 0; d < 4; d++) {
            int nx = x + dx4[d], ny = y + dy4[d];
            if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue;
            if (visited[ny * width + nx]) continue;
            visited[ny * width + nx] = true;
            q.push({nx, ny});
        }
    }

    env->ReleaseIntArrayElements(pixelArray, pixels, JNI_ABORT);
    env->ReleaseByteArrayElements(maskArray, mask, 0);
}

// ── JNI: radialMask ───────────────────────────────────────────────────────
// Gradient from centre (full) to outerRadius (zero)
extern "C" JNIEXPORT void JNICALL
Java_com_rohan_proedit_processing_NativeProcessor_radialMask(
        JNIEnv* env, jobject,
        jbyteArray maskArray, jint width, jint height,
        jfloat centerX, jfloat centerY,
        jfloat innerRadius, jfloat outerRadius, jboolean inverted) {

    jbyte* mask = env->GetByteArrayElements(maskArray, nullptr);

    for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
            float dx = x - centerX, dy = y - centerY;
            float dist = std::sqrt(dx*dx + dy*dy);
            float alpha;
            if (dist <= innerRadius) {
                alpha = 1.f;
            } else if (dist >= outerRadius) {
                alpha = 0.f;
            } else {
                alpha = 1.f - (dist - innerRadius) / (outerRadius - innerRadius);
            }
            if (inverted) alpha = 1.f - alpha;
            mask[y * width + x] = (jbyte)(uint8_t)(alpha * 255.f);
        }
    }

    env->ReleaseByteArrayElements(maskArray, mask, 0);
}

// ── JNI: linearGradientMask ───────────────────────────────────────────────
// Full at start → zero at end, perpendicular to line direction
extern "C" JNIEXPORT void JNICALL
Java_com_rohan_proedit_processing_NativeProcessor_linearGradientMask(
        JNIEnv* env, jobject,
        jbyteArray maskArray, jint width, jint height,
        jfloat startX, jfloat startY, jfloat endX, jfloat endY) {

    jbyte* mask = env->GetByteArrayElements(maskArray, nullptr);

    float dx = endX - startX, dy = endY - startY;
    float len2 = dx*dx + dy*dy;
    if (len2 < 1e-6f) {
        env->ReleaseByteArrayElements(maskArray, mask, JNI_ABORT);
        return;
    }
    float len = std::sqrt(len2);

    for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
            float px = x - startX, py = y - startY;
            // Project onto line direction
            float t = (px*dx + py*dy) / len2;
            t = std::max(0.f, std::min(1.f, t));
            float alpha = 1.f - t;
            mask[y * width + x] = (jbyte)(uint8_t)(alpha * 255.f);
        }
    }

    env->ReleaseByteArrayElements(maskArray, mask, 0);
}

// ── JNI: colorRangeMask ───────────────────────────────────────────────────
// Select pixels by color similarity (LAB approximation using RGB distance)
extern "C" JNIEXPORT void JNICALL
Java_com_rohan_proedit_processing_NativeProcessor_colorRangeMask(
        JNIEnv* env, jobject,
        jintArray pixelArray, jbyteArray maskArray,
        jint width, jint height,
        jint tR, jint tG, jint tB, jfloat tolerance) {

    jint*  pixels = env->GetIntArrayElements(pixelArray, nullptr);
    jbyte* mask   = env->GetByteArrayElements(maskArray,  nullptr);

    float tolVal = tolerance * 442.f;

    for (int i = 0; i < width * height; i++) {
        jint px = pixels[i];
        float r = (float)((px >> 16) & 0xFF);
        float g = (float)((px >>  8) & 0xFF);
        float b = (float)( px        & 0xFF);
        float dist = std::sqrt((r-tR)*(r-tR) + (g-tG)*(g-tG) + (b-tB)*(b-tB));
        float alpha = std::max(0.f, 1.f - dist / (tolVal + 1e-6f));
        mask[i] = (jbyte)(uint8_t)(alpha * 255.f);
    }

    env->ReleaseIntArrayElements(pixelArray, pixels, JNI_ABORT);
    env->ReleaseByteArrayElements(maskArray, mask, 0);
}

// ── JNI: invertMask ───────────────────────────────────────────────────────
extern "C" JNIEXPORT void JNICALL
Java_com_rohan_proedit_processing_NativeProcessor_invertMask(
        JNIEnv* env, jobject, jbyteArray maskArray) {
    jsize len  = env->GetArrayLength(maskArray);
    jbyte* mask = env->GetByteArrayElements(maskArray, nullptr);
    for (jsize i = 0; i < len; i++) {
        mask[i] = (jbyte)(255 - (uint8_t)mask[i]);
    }
    env->ReleaseByteArrayElements(maskArray, mask, 0);
}

// ── JNI: clearMask ────────────────────────────────────────────────────────
extern "C" JNIEXPORT void JNICALL
Java_com_rohan_proedit_processing_NativeProcessor_clearMask(
        JNIEnv* env, jobject, jbyteArray maskArray, jboolean fillFull) {
    jsize len   = env->GetArrayLength(maskArray);
    jbyte* mask = env->GetByteArrayElements(maskArray, nullptr);
    jbyte val   = fillFull ? (jbyte)255 : (jbyte)0;
    std::memset(mask, val, len);
    env->ReleaseByteArrayElements(maskArray, mask, 0);
}
