#include <assert.h>
#include <sys/types.h>
#include <unistd.h>
#include <android/log.h>

#include "jni_utils.h"

jclass get_class(JNIEnv* env, const char* name)
{
    assert(env != NULL);
    assert(name != NULL);

    LOGD(TAG, "Getting %s class ...", name);

    jclass local = (*env)->FindClass(env, name);
    if (local == NULL) {
        LOGE(TAG, "Failed to get class: %s", name);
        return NULL;
    }
    jclass global = (*env)->NewGlobalRef(env, local);
    if (global == NULL) {
        LOGE(TAG, "Failed to make global ref of class: %s", name);
        return NULL;
    }
    (*env)->DeleteLocalRef(env, local);
    return global;
}

jmethodID get_method(JNIEnv* env, jclass cls, const char* name, const char* sig)
{
    assert(env != NULL);
    assert(cls != NULL);
    assert(name != NULL);
    assert(sig != NULL);

    LOGD(TAG, "Getting %s method ...", name);

    jmethodID id = (*env)->GetMethodID(env, cls, name, sig);
    if (id == NULL) {
        LOGE(TAG, "Failed to get method: %s", name);
        return NULL;
    }
    return id;
}

jfieldID get_field(JNIEnv* env, jclass cls, const char* name, const char* type)
{
    assert(env != NULL);
    assert(cls != NULL);
    assert(name != NULL);
    assert(type != NULL);

    LOGD(TAG, "Getting %s:%s field ...", name, type);

    jfieldID id = (*env)->GetFieldID(env, cls, name, type);
    if (id == NULL) {
        LOGE(TAG, "Failed to get filed: %s", name);
        return NULL;
    }
    return id;
}

void release_ref(JNIEnv* env, jobject obj) {
    (*env)->DeleteGlobalRef(env, obj);
}
