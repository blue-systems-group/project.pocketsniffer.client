#ifndef _JNI_UTILS_H_
#define _JNI_UTILS_H_

#include <jni.h>

#define LOGV(tag,...) __android_log_print(ANDROID_LOG_VERBOSE, (tag), __VA_ARGS__)
#define LOGD(tag,...) __android_log_print(ANDROID_LOG_DEBUG  , (tag), __VA_ARGS__)
#define LOGI(tag,...) __android_log_print(ANDROID_LOG_INFO   , (tag), __VA_ARGS__)
#define LOGW(tag,...) __android_log_print(ANDROID_LOG_WARN   , (tag), __VA_ARGS__)
#define LOGE(tag,...) __android_log_print(ANDROID_LOG_ERROR  , (tag), __VA_ARGS__)

#define TAG "PocketSniffer-JNI"

#define _P_ __attribute__((__packed__))
#define STATIC_ASSERT(cond) typedef char static_assertion[(cond)?1:-1]


typedef struct obj_field {
    jfieldID id;
    const char* name;
    const char* type;
} obj_field_t;


jclass get_class(JNIEnv* env, const char* name);
jmethodID get_method(JNIEnv* env, jclass cls, const char* name, const char* sig);
jfieldID get_field(JNIEnv* env, jclass cls, const char* name, const char* type);
void release_ref(JNIEnv* env, jobject obj);
#endif /* _JNI_UTILS_H_ */
