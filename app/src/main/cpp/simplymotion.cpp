#include <jni.h>

#include <string>
#include <jni.h>
#include <android/log.h>
#include <android/bitmap.h>
#include <algorithm>

#define  LOG_TAG    "native_md"
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)

#define BLACK 0xFF000000

const int offset[] = {-1, 0, 1};
const float gaussKernel[] = {0.06277703, 0.21074773, 0.50055313,
                             0.8411289, 1.0, 0.8411289,
                             0.50055313, 0.21074773, 0.06277703
};

int compare(const int *number1, const int *number2) {
    return *number1 - *number2;
}

int weighted_sum(
        const int *pixels,
        unsigned int x,
        unsigned int y,
        unsigned int width) {
    float result = 0.0;
    int index = 0;

    for (int i: offset) {
        unsigned int xPos = x + i;
        unsigned int kernelXPos = i + 1;

        for (int j: offset) {
            unsigned int idx = xPos + width * (y + j);
            unsigned int kernelIndex = kernelXPos + 3 * (j + 1);

            result += (float) pixels[idx] * gaussKernel[kernelIndex];
        }
    }

    return (int) (result / 9);
}

int median_pixel(const int *pixels,
                 unsigned int x,
                 unsigned int y,
                 unsigned int width,
                 int *pixel_buffer) {
    int index = 0;
    for (int i: offset) {
        unsigned int xPos = x + i;

        for (int j: offset) {
            unsigned idx = xPos + width * (y + j);
            pixel_buffer[index++] = pixels[idx];
        }
    }

    std::qsort(pixel_buffer, 9, sizeof(int),
               reinterpret_cast<int (*)(const void *, const void *)>(compare));

    return pixel_buffer[4];
}

extern "C"
JNIEXPORT void JNICALL
Java_io_iskopasi_simplymotion_MotionAnalyzer_blurMedianNative(JNIEnv *env, jobject thiz,
                                                              jobject src,
                                                              jobject dst,
                                                              jint left,
                                                              jint top,
                                                              jint right,
                                                              jint bottom) {
    int pixel_buffer[9];
    AndroidBitmapInfo info;
    int *pixels_src;
    int *pixels_dst;
    unsigned int width;

    AndroidBitmap_getInfo(env, src, &info);
    AndroidBitmap_lockPixels(env, src, (void **) &pixels_src);
    AndroidBitmap_lockPixels(env, dst, (void **) &pixels_dst);

    width = info.width;

    for (int x = left; x < right; x++) {
        for (int y = top; y < bottom; y++) {
            unsigned int dst_index = x + (width * y);

            pixels_dst[dst_index] = median_pixel(pixels_src, x, y, width, pixel_buffer);
        }
    }

    AndroidBitmap_unlockPixels(env, src);
    AndroidBitmap_unlockPixels(env, dst);
}

extern "C"
JNIEXPORT void JNICALL
Java_io_iskopasi_simplymotion_MotionAnalyzer_adaptiveThresholdNative(JNIEnv *env, jobject thiz,
                                                                     jobject src,
                                                                     jobject dst,
                                                                     jint left,
                                                                     jint top,
                                                                     jint right,
                                                                     jint bottom,
                                                                     jint constant,
                                                                     jint detect_color) {
    int pixel_buffer[9];
    AndroidBitmapInfo info;
    int *pixels_src;
    int *pixels_dst;
    unsigned int width;

    AndroidBitmap_getInfo(env, src, &info);
    AndroidBitmap_lockPixels(env, src, (void **) &pixels_src);
    AndroidBitmap_lockPixels(env, dst, (void **) &pixels_dst);

    width = info.width;

    for (int x = left; x < right; x++) {
        for (int y = top; y < bottom; y++) {
            unsigned int index = x + (width * y);
            unsigned int threshold = weighted_sum(pixels_src, x, y, width);
            unsigned int pixel = pixels_src[index];

            if (pixel > threshold - constant)
                pixels_dst[index] = detect_color;
            else
                pixels_dst[index] = BLACK;
        }
    }

    AndroidBitmap_unlockPixels(env, src);
    AndroidBitmap_unlockPixels(env, dst);
}