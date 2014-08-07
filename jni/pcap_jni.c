#include <jni.h>

#include <string.h>
#include <stdio.h>
#include <android/log.h>
#include <errno.h>
#include <pcap.h>
#include <arpa/inet.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <unistd.h>


#define LOGV(tag,...) __android_log_print(ANDROID_LOG_VERBOSE, tag, __VA_ARGS__)
#define LOGD(tag,...) __android_log_print(ANDROID_LOG_DEBUG  , tag, __VA_ARGS__)
#define LOGI(tag,...) __android_log_print(ANDROID_LOG_INFO   , tag, __VA_ARGS__)
#define LOGW(tag,...) __android_log_print(ANDROID_LOG_WARN   , tag, __VA_ARGS__)
#define LOGE(tag,...) __android_log_print(ANDROID_LOG_ERROR  , tag, __VA_ARGS__)

#define TAG "PocketSniffer-JNI"

JNIEXPORT jobjectArray JNICALL Java_edu_buffalo_cse_pocketsniffer_SnifTask_parsePcap(JNIEnv* env, jobject this, jstring file)
{
    char* tag, file_path;
    jclass task_class, traffic_class;
    char err_buf[PCAP_ERRBUF_SIZE];
    pcap_t* handle = NULL;;
    jobjectArray flows = NULL;
    const u_char* packet;
    struct pcap_pkthdr header;

    traffic_class = (*env)->FindClass(env, "edu/buffalo/cse/pocketsniffer/TrafficFlow");
    if (traffic_class == NULL) {
        LOGE(TAG, "Failed to get TrafficFlow class.");
        goto parse_done;
    }

    file_path = (*env)->GetStringUTFChars(env, file, NULL);
    handle = pcap_open_offline(file_path, err_buf);
    if (handle == NULL) {
        LOGE(TAG, "Failed to open pcap file: %s", file_path);
        goto parse_done;
    }

    while ((packet = pcap_next(handle, &header)) != NULL) {

    }

parse_done:
    (*env)->ReleaseStringUTFChars(env, file, file_path);
    return flows;
}
