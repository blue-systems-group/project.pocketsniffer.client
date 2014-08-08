#include <jni>

#ifndef _JNI_UTILS_H_
#define _JNI_UTILS_H_

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
