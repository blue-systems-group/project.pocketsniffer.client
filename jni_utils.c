#include "jni_utils.h"

jclass get_class(JNIEnv* env, const char* name)
{
    assert(env != NULL);
    assert(name != NULL);

    jclass cls = (*env)->FindClass(env, name);
    if (cls == NULL) {
        LOGE(TAG, "Failed to get class: %s", name);
        return NULL;
    }
    cls = (*env)->NewGlobalRef(env, cls);
    if (cls == NULL) {
        LOGE(TAG, "Failed to make global ref of class: %s", name);
        return NULL;
    }
    return cls;
}

jmethodID get_method(JNIEnv* env, jclass cls, const char* name, const char* sig)
{
    assert(env != NULL);
    assert(cls != NULL);
    assert(name != NULL);
    assert(sig != NULL);

    jmethodID id = (*env)->GetMethodID(env, cls, name, sig);
    if (id == NULL) {
        LOGE(TAG, "Failed to get method: %s", name);
        return NULL;
    }
    id = (*env)->NewGlobalRef(env, id);
    if (cls == NULL) {
        LOGE(TAG, "Failed to make global ref of method: %s", name);
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

    jfieldID id = (*env)->GetFieldID(env, cls, name, type);
    if (id == NULL) {
        LOGE(TAG, "Failed to get filed: %s", name);
        return NULL;
    }
    id = (*env)->NewGlobalRef(env, id);
    if (cls == NULL) {
        LOGE(TAG, "Failed to make global ref of field: %s", name);
        return NULL;
    }

    return id;
}

void release_ref(JNIEnv* env, jobject obj) {
    (*env)->DeleteGlobalRef(env, obj);
}
