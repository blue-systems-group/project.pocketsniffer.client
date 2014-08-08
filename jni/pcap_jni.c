#include <jni.h>
#include <endian.h>

#include "pcap_jni.h"
#include "jni_utils.h"

static custom_radiotap_header_t* parse_radiotap_header(void* ptr);
static dot11_header_t* parse_dot11_header(void* ptr);
static void get_mac_str(uint8_t* mac, char* buf);

static void dump_pkt(void* pkt, size_t len);

static JavaVM* g_vm;

static jclass g_packet_class;
static jmethodID g_packet_constructor;
static obj_field_t g_packet_fields[] = {
    { .name = "type", .type = "I" },
    { .name = "subtype", .type = "I" },
    { .name = "from_ds", .type = "Z" },
    { .name = "to_ds", .type = "Z" },
    { .name = "tv_sec", .type = "I" },
    { .name = "tv_usec", .type = "I" },
    { .name = "len", .type = "I" },
    { .name = "addr1", type = "Ljava/lang/String;" },
    { .name = "rssi", type = "I" },
    { .name = "freq", type = "I" },
#define PACKET_FILED_NUM  10
};

static jclass g_snif_task_class;
static jmethodID g_got_pkt;

jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
    g_vm = vm;

    JNIEnv* env;
    if ((g_vm->GetEnv((void**)&env, JNI_VERSION_1_6)) != JNI_OK) {
        LOGE(TAG, "Failed to get JNIEnv.");
        return JNI_ERR;
    }

    g_packet_class = get_class("edu/buffalo/cse/pocketsniffer/Packet");
    if (g_packet_class == NULL) {
        return JNI_ERR;
    }

    g_packet_constructor = get_method(env, packet_class, "<init>", "()V");
    if (g_packet_constructor == NULL) {
        return JNI_ERR;
    }

    for (int i = 0; i < PACKET_FILED_NUM; i++) {
        g_packet_fields[i].id = get_field(env, g_packet_class, g_packet_fields[i].name, g_packet_fields[i].type);
        if (g_packet_fields[i].id == NULL) {
            return JNI_ERR;
        }
    }

    g_snif_task_class = get_class(env, "edu/buffalo/cse/pocketsniffer/SnifTask");
    if (g_snif_task_class == NULL) {
        return JNI_ERR;
    }

    g_got_pkt = get_method(env, g_snif_task_class,  "gotPacket", "(edu/buffalo/cse/pocketsniffer/Packet)V");
    if (g_got_pkt == NULL) {
        return JNI_ERR;
    }

    return JNI_OK;
}


JNIEXPORT jobjectArray JNICALL Java_edu_buffalo_cse_pocketsniffer_SnifTask_parsePcap(JNIEnv* env, jobject this, jstring file)
{
    char* file_path = NULL;
    jobjectArray flows = NULL;

    file_path = (char*) (*env)->GetStringUTFChars(env, file, NULL);
    char err_buf[PCAP_ERRBUF_SIZE];
    pcap_t* handle = pcap_open_offline(file_path, err_buf);
    if (handle == NULL) {
        LOGE(TAG, "Failed to open pcap file: %s", file_path);
        goto parse_done;
    }
    LOGD(TAG, "Parsing pcap file %s ...", file_path);

    const u_char* pkt;
    struct pcap_pkthdr header;

    jobject packet = (*env)->NewObject(env, g_packet_class, g_packet_constructor);
    if (packet == NULL) {
        LOGE(TAG, "Failed to create Packet object.");
        return;
    }
    char mac_buf[32];

    while ((pkt = pcap_next(handle, &header)) != NULL) {
        custom_radiotap_header_t* radiotap_hdr = parse_radiotap_header(pkt);
        pkt += sizeof(custom_radiotap_header_t);
        dot11_header_t* dot11_hdr = parse_dot11_header(pkt);

        jint int_val = (jint) dot11_hdr->type;
        (*env)->SetIntField(env, packet, g_packet_fields[0].id, int_val);

        int_val = (jint) dot11_hdr->subtype;
        (*env)->SetIntField(env, packet, g_packet_fields[1].id, int_val);

        jboolean bool_val = (jboolean) FC_MASK_FROM_DS(dot11_hdr->frame_ctrl);
        (*env)->SetBooleanField(env, packet, g_packet_fields[2].id, bool_val);

        bool_val = (jboolean) FC_MASK_TO_DS(dot11_hdr->frame_ctrl);
        (*env)->SetBooleanField(env, packet, g_packet_fields[3].id, bool_val);

        int_val = (jint) header.ts.tv_sec;
        (*env)->SetIntField(env, packet, g_packet_fields[4].id, int_val);

        int_val = (jint) header.ts.tv_usec;
        (*env)->SetIntField(env, packet, g_packet_fields[5].id, int_val);

        int_val = (jint) DOT11_PKT_LEN(header.caplen);
        (*env)->SetIntField(env, packet, g_packet_fields[6].id, int_val);

        get_mac_str(dot11_hdr->addr1, mac_buf);
        jstring str_val = (*env)->NewStringUTF(env, mac_buf);
        (*env)->SetObjectField(env, packet, g_packet_fields[7].id, str_val);

        int_val = (jint) radiotap_hdr->rssi;
        (*env)->SetIntField(env, packet, g_packet_fields[8].id, int_val);

        int_val = (jint) radiotap_hdr->channel_mhz;
        (*env)->SetIntField(env, packet, g_packet_fields[9].id, int_val);

        (*env)->CallVoidMethod(env, this, g_got_pkt, packet);
    }

parse_done:
    if (file_path != NULL) {
        (*env)->ReleaseStringUTFChars(env, file, file_path);
    }
    return flows;
}

static void dump_pkt(void* pkt, size_t len)
{
    assert(pkt != NULL);

    uint8_t buf[1024];
    size_t i, _len;

    _len = len < sizeof(buf)/2? len: sizeof(buf)/2;

    for (i = 0; i < _len; i++) {
        sprintf(&(buf[i*2]), "%02x ", ((uint8_t*)pkt)[i]);
    }
    LOGD(TAG, "%s", buf);
}

static void parse_pkt_type(void* pkt, int* type, int* subtype)
{
    assert(pkt != NULL);
    assert(type != NULL);
    assert(subtype != NULL);

    uint16_t fc = letoh16(*(uint16_t*)pkt);
    *type = FC_GET_TYPE(fc);
    *subtype = FC_GET_SUBTYPE(fc);
}

static custom_radiotap_header_t* parse_radiotap_header(void* ptr)
{
    assert(ptr != NULL);

    custom_radiotap_header_t* hdr = (custom_radiotap_header_t*) ptr;
    hdr->it_len = letoh16(hdr->it_len);
    hdr->it_present = letoh32(hdr->it_present);
    hdr->channel_mhz = letoh32(hdr->channel_mhz);
    hdr->channel_flags = letoh32(hdr->channel_mhz);
    return hdr;
}

static dot11_header_t* parse_dot11_header(void* ptr)
{
    assert(ptr != NULL);

    dot11_header_t* hdr = (dot11_header_t*) ptr;
    hdr->frame_ctrl = letoh16(hdr->frame_ctrl);
    hdr->duration = letoh16(hdr->duration);
    return hdr;
}

static void get_mac_str(uint8_t* mac, char* buf) {
    assert(mac != NULL);
    assert(buf != NULL);

    for (int i = 0; i < MAC_LEN-1; i++) {
        buf += sprintf(buf, "%02X:", mac[i]);
    }
    sprintf(buf, "%02X", mac[MAC_LEN-1]);
}


