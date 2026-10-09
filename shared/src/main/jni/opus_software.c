#include <jni.h>
#include <dlfcn.h>
#include <stdint.h>
#include <stdlib.h>
#include <android/log.h>

#define OPUS_TAG "xcertplay-opus"

typedef int opus_int32;
typedef struct OpusDecoder OpusDecoder;

typedef OpusDecoder *(*decoder_create_fn)(opus_int32 sample_rate, int channels, int *error);
typedef int (*decoder_decode_fn)(
        OpusDecoder *decoder,
        const unsigned char *data,
        opus_int32 data_length,
        int16_t *output,
        int frame_size,
        int decode_fec);
typedef void (*decoder_destroy_fn)(OpusDecoder *decoder);

/**
 * The channel count travels with the handle because opus_decode takes its frame size in samples
 * per channel, while JNI only reports the output array's total length.
 */
typedef struct {
    OpusDecoder *decoder;
    int channels;
} software_decoder;

static decoder_create_fn decoder_create;
static decoder_decode_fn decoder_decode;
static decoder_destroy_fn decoder_destroy;

/**
 * Resolved at runtime instead of link time: the Opus binary arrives as a prebuilt shared library
 * with no import library, and resolving lazily keeps this module loadable when it is absent.
 */
JNIEXPORT jboolean JNICALL
Java_com_shilapi_xcertplay_media_SoftwareOpusNative_initialize(JNIEnv *env, jclass clazz) {
    (void) env;
    (void) clazz;
    if (decoder_decode != NULL) return JNI_TRUE;
    void *library = dlopen("libopus.so", RTLD_NOW | RTLD_LOCAL);
    if (library == NULL) {
        __android_log_print(ANDROID_LOG_WARN, OPUS_TAG, "libopus.so unavailable: %s", dlerror());
        return JNI_FALSE;
    }
    decoder_create = (decoder_create_fn) dlsym(library, "opus_decoder_create");
    decoder_decode = (decoder_decode_fn) dlsym(library, "opus_decode");
    decoder_destroy = (decoder_destroy_fn) dlsym(library, "opus_decoder_destroy");
    if (decoder_create == NULL || decoder_decode == NULL || decoder_destroy == NULL) {
        __android_log_print(ANDROID_LOG_WARN, OPUS_TAG, "libopus.so is missing decoder symbols");
        decoder_create = NULL;
        decoder_decode = NULL;
        decoder_destroy = NULL;
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

JNIEXPORT jlong JNICALL
Java_com_shilapi_xcertplay_media_SoftwareOpusNative_create(
        JNIEnv *env, jclass clazz, jint sample_rate, jint channels) {
    (void) env;
    (void) clazz;
    if (decoder_create == NULL) return 0;
    software_decoder *state = (software_decoder *) calloc(1, sizeof(software_decoder));
    if (state == NULL) return 0;
    int error = 0;
    state->decoder = decoder_create((opus_int32) sample_rate, (int) channels, &error);
    if (state->decoder == NULL) {
        __android_log_print(ANDROID_LOG_WARN, OPUS_TAG, "opus_decoder_create failed error=%d", error);
        free(state);
        return 0;
    }
    state->channels = channels > 0 ? (int) channels : 1;
    return (jlong) (intptr_t) state;
}

JNIEXPORT jint JNICALL
Java_com_shilapi_xcertplay_media_SoftwareOpusNative_decode(
        JNIEnv *env, jclass clazz, jlong handle, jbyteArray data, jint offset, jint length,
        jshortArray output) {
    (void) clazz;
    software_decoder *state = (software_decoder *) (intptr_t) handle;
    if (decoder_decode == NULL || state == NULL || state->decoder == NULL) return -1;
    jsize capacity = (*env)->GetArrayLength(env, output);
    int frame_size = (int) (capacity / (state->channels > 0 ? state->channels : 1));
    if (frame_size <= 0) return -1;
    /*
     * Plain element access rather than GetPrimitiveArrayCritical: Dalvik aborts the process if any
     * JNI call happens inside a critical section, and the frames here are a couple of kilobytes,
     * so avoiding one copy is not worth that hazard.
     */
    const jbyte *input = (*env)->GetByteArrayElements(env, data, NULL);
    if (input == NULL) return -1;
    jshort *output_elements = (*env)->GetShortArrayElements(env, output, NULL);
    if (output_elements == NULL) {
        (*env)->ReleaseByteArrayElements(env, data, (jbyte *) input, JNI_ABORT);
        return -1;
    }
    int samples = decoder_decode(
            state->decoder,
            (const unsigned char *) (input + offset),
            (opus_int32) length,
            (int16_t *) output_elements,
            frame_size,
            0);
    (*env)->ReleaseShortArrayElements(env, output, output_elements, samples > 0 ? 0 : JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, data, (jbyte *) input, JNI_ABORT);
    return (jint) samples;
}

JNIEXPORT void JNICALL
Java_com_shilapi_xcertplay_media_SoftwareOpusNative_destroy(
        JNIEnv *env, jclass clazz, jlong handle) {
    (void) env;
    (void) clazz;
    software_decoder *state = (software_decoder *) (intptr_t) handle;
    if (state == NULL) return;
    if (decoder_destroy != NULL && state->decoder != NULL) decoder_destroy(state->decoder);
    free(state);
}
