/*
 * JNI wrapper around LAME 3.100.
 *
 * This is a corrected version of the classic `Mp3Converter` wrapper:
 *   - JNI symbols for encode()/flush() previously used a mangled name
 *     (`_encode`/`_flush`) that never matched the Java declarations,
 *     so the streaming API always threw UnsatisfiedLinkError.
 *   - close() previously declared JNIEXPORT jstring but never returned,
 *     so on arm64 the stub fell off the end and trapped (SIGTRAP),
 *     crashing the app.
 *
 * Design notes:
 *   - A single global lame session is held per process. Call init() to
 *     configure it (also frees any previous session) and close() to free it.
 *   - encode()/flush() stream PCM → MP3 in place via GetByteArrayElements
 *     (no copies, no leaks).
 */
#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include "lamemp3/lame.h"

#define BUFFER_SIZE 8192

static lame_global_flags *sLame = NULL;

static void resetLame() {
    if (sLame != NULL) {
        lame_close(sLame);
        sLame = NULL;
    }
}

static void lameInit(jint inSampleRate,
                     jint channel, jint mode, jint outSampleRate,
                     jint outBitRate, jint quality) {
    resetLame();

    lame_global_flags *gfp = lame_init();
    if (gfp == NULL) return;

    lame_set_in_samplerate(gfp, inSampleRate);
    lame_set_num_channels(gfp, channel);
    lame_set_out_samplerate(gfp, outSampleRate);
    lame_set_brate(gfp, outBitRate);
    lame_set_quality(gfp, quality);

    // mode: 0 = CBR, 1 = VBR(ABR), 2 = VBR(MTRH)
    if (mode == 0) {
        lame_set_VBR(gfp, vbr_off);
    } else if (mode == 1) {
        lame_set_VBR(gfp, vbr_abr);
    } else {
        lame_set_VBR(gfp, vbr_mtrh);
    }

    if (lame_init_params(gfp) < 0) {
        lame_close(gfp);
        return;
    }
    sLame = gfp;
}

//
// Java_jaygoo_library_converter_Mp3Converter_<methods>
//

extern "C" JNIEXPORT void JNICALL
Java_jaygoo_library_converter_Mp3Converter_init(JNIEnv*, jclass, jint inSampleRate,
                                                jint channel, jint mode, jint outSampleRate,
                                                jint outBitRate, jint quality) {
    lameInit(inSampleRate, channel, mode, outSampleRate, outBitRate, quality);
}

extern "C" JNIEXPORT jint JNICALL
Java_jaygoo_library_converter_Mp3Converter_encode(
        JNIEnv *env, jclass, jshortArray bufferLeft, jshortArray bufferRight,
        jint samples, jbyteArray mp3buf) {
    if (sLame == NULL) return -3;  // lame_init_params() not called

    jshort *left = env->GetShortArrayElements(bufferLeft, NULL);
    if (left == NULL) return -2;
    jshort *right = env->GetShortArrayElements(bufferRight, NULL);
    if (right == NULL) {
        env->ReleaseShortArrayElements(bufferLeft, left, 0);
        return -2;
    }

    const jsize size = env->GetArrayLength(mp3buf);
    unsigned char *out = (unsigned char *) env->GetByteArrayElements(mp3buf, NULL);
    int result = (out == NULL) ? -2
                                : lame_encode_buffer(sLame, left, right, samples, out, size);

    env->ReleaseShortArrayElements(bufferLeft, left, 0);
    env->ReleaseShortArrayElements(bufferRight, right, 0);
    if (out != NULL) env->ReleaseByteArrayElements(mp3buf, (jbyte *) out, 0);
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_jaygoo_library_converter_Mp3Converter_flush(JNIEnv *env, jclass, jbyteArray mp3buf) {
    if (sLame == NULL) return -3;

    const jsize size = env->GetArrayLength(mp3buf);
    unsigned char *out = (unsigned char *) env->GetByteArrayElements(mp3buf, NULL);
    int result = (out == NULL) ? -2 : lame_encode_flush(sLame, out, size);
    if (out != NULL) env->ReleaseByteArrayElements(mp3buf, (jbyte *) out, 0);
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_jaygoo_library_converter_Mp3Converter_close(JNIEnv*, jclass) {
    resetLame();
}

// Convenience one-shot: PCM(M4A/S16LE interleaved stereo) file → MP3 file.
// init() should be called first to choose bitrate/channels; if not, defaults apply.
extern "C" JNIEXPORT void JNICALL
Java_jaygoo_library_converter_Mp3Converter_convertMp3(JNIEnv *env, jclass,
                                                      jstring jInputPath, jstring jMp3Path) {
    const char *inPath = env->GetStringUTFChars(jInputPath, NULL);
    const char *outPath = env->GetStringUTFChars(jMp3Path, NULL);
    if (inPath == NULL || outPath == NULL) {
        if (inPath) env->ReleaseStringUTFChars(jInputPath, inPath);
        if (outPath) env->ReleaseStringUTFChars(jMp3Path, outPath);
        return;
    }

    if (sLame == NULL) lameInit(44100, 2, 0, 44100, 192, 5);

    if (sLame != NULL) {
        FILE *fin = fopen(inPath, "rb");
        FILE *fout = fopen(outPath, "wb");
        if (fin != NULL && fout != NULL) {
            short inputBuffer[BUFFER_SIZE * 2];
            unsigned char mp3Buffer[BUFFER_SIZE + (BUFFER_SIZE / 4) + 7200];
            int read;
            while ((read = (int) fread(inputBuffer, sizeof(short) * 2, BUFFER_SIZE, fin)) != 0) {
                int write = lame_encode_buffer_interleaved(
                        sLame, inputBuffer, read, mp3Buffer, (int) sizeof(mp3Buffer));
                if (write > 0) fwrite(mp3Buffer, 1, (size_t) write, fout);
            }
            int end = lame_encode_flush(sLame, mp3Buffer, (int) sizeof(mp3Buffer));
            if (end > 0) fwrite(mp3Buffer, 1, (size_t) end, fout);

            fclose(fout);
            fclose(fin);
        } else {
            if (fin) fclose(fin);
            if (fout) fclose(fout);
        }
        resetLame();
    }

    env->ReleaseStringUTFChars(jInputPath, inPath);
    env->ReleaseStringUTFChars(jMp3Path, outPath);
}

extern "C" JNIEXPORT jlong JNICALL
Java_jaygoo_library_converter_Mp3Converter_getConvertBytes(JNIEnv*, jclass) {
    return sLame == NULL ? -1L : 0L;
}

extern "C" JNIEXPORT jstring JNICALL
Java_jaygoo_library_converter_Mp3Converter_getLameVersion(JNIEnv *env, jclass) {
    return env->NewStringUTF(get_lame_version());
}