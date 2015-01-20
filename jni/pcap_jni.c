#include <jni.h>
#include <endian.h>
#include <stdbool.h>
#include <assert.h>
#include <android/log.h>
#include <pcap.h>
#include <unistd.h>

#include "pcap_jni.h"
#include "jni_utils.h"
#include "crc.h"
#include "coffeecatch.h"

static custom_radiotap_header_t* parse_radiotap_header(const uint8_t* ptr);
static dot11_header_t* parse_dot11_header(const uint8_t* ptr);
static void get_mac_str(uint8_t* mac, char* buf);
static int get_ssid(const uint8_t* pkt, size_t pkt_len, char* buf, size_t buf_len);

static void dump_pkt(const uint8_t* pkt, size_t len);

#define PACKET_CLASS        "edu/buffalo/cse/pocketsniffer/interfaces/Packet"
#define SNIFTASK_CLASS      "edu/buffalo/cse/pocketsniffer/tasks/SnifTask"

static JavaVM* g_vm;

static jclass g_packet_class;
static jmethodID g_packet_constructor;
static obj_field_t g_packet_fields[] = {
    { .name = "type",       .type = "I" }, // 0
    { .name = "subtype",    .type = "I" }, // 1
    { .name = "from_ds",    .type = "Z" }, // 2
    { .name = "to_ds",      .type = "Z" }, // 3
    { .name = "tv_sec",     .type = "I" }, // 4
    { .name = "tv_usec",    .type = "I" }, // 5
    { .name = "len",        .type = "I" }, // 6
    { .name = "addr1",      .type = "Ljava/lang/String;" }, // 7
    { .name = "addr2",      .type = "Ljava/lang/String;" }, // 8
    { .name = "rssi",       .type = "I" }, // 9
    { .name = "freq",       .type = "I" }, // 10
    { .name = "retry",      .type = "Z" }, // 11
#define PACKET_FILED_NUM  12
};

static jclass g_snif_task_class;
static jmethodID g_got_pkt;

jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
    LOGD(TAG, "===== Loading Pcap lib =====");
    g_vm = vm;

    JNIEnv* env;
    if (((*g_vm)->GetEnv(g_vm, (void**)&env, JNI_VERSION_1_6)) != JNI_OK) {
        LOGE(TAG, "Failed to get JNIEnv.");
        return JNI_ERR;
    }

    g_packet_class = get_class(env, PACKET_CLASS);
    if (g_packet_class == NULL) {
        return JNI_ERR;
    }

    g_packet_constructor = get_method(env, g_packet_class, "<init>", "()V");
    if (g_packet_constructor == NULL) {
        return JNI_ERR;
    }

    for (int i = 0; i < PACKET_FILED_NUM; i++) {
        g_packet_fields[i].id = get_field(env, g_packet_class, g_packet_fields[i].name, g_packet_fields[i].type);
        if (g_packet_fields[i].id == NULL) {
            return JNI_ERR;
        }
    }

    g_snif_task_class = get_class(env, SNIFTASK_CLASS);
    if (g_snif_task_class == NULL) {
        return JNI_ERR;
    }

    g_got_pkt = get_method(env, g_snif_task_class,  "gotPacket", "(L" PACKET_CLASS ";)V");
    if (g_got_pkt == NULL) {
        return JNI_ERR;
    }

    return JNI_VERSION_1_6;
}


JNIEXPORT jboolean JNICALL Java_edu_buffalo_cse_pocketsniffer_tasks_SnifTask_parsePcap(JNIEnv* env, jobject this, jstring file)
{
    jboolean ret = false;

    char* file_path = (char*) (*env)->GetStringUTFChars(env, file, NULL);
    char err_buf[PCAP_ERRBUF_SIZE];
    pcap_t* handle = pcap_open_offline(file_path, err_buf);
    if (handle == NULL) {
        LOGE(TAG, "Failed to open pcap file %s: %s", file_path, err_buf);
        goto parse_done;
    }

    jobject packet = (*env)->NewObject(env, g_packet_class, g_packet_constructor);
    if (packet == NULL) {
        LOGE(TAG, "Failed to create Packet object.");
        goto parse_done;
    }

    LOGD(TAG, "Parsing pcap file %s ...", file_path);

    char buf[64];
    jint int_val;
    jboolean bool_val;
    jstring str_val;
    size_t pkt_len;
    const uint8_t* pkt;
    struct pcap_pkthdr header;

    while ((pkt = pcap_next(handle, &header)) != NULL) {
        if (header.len != header.caplen) {
            LOGE(TAG, "Ignoring truncated packet.");
            continue;
        }

        custom_radiotap_header_t* radiotap_hdr = parse_radiotap_header(pkt);
        pkt += sizeof(custom_radiotap_header_t);
        dot11_header_t* dot11_hdr = parse_dot11_header(pkt);

        pkt_len = DOT11_PKT_LEN(header.len);

        // type
        int_val = (jint) FC_TYPE(dot11_hdr->frame_ctrl);
        (*env)->SetIntField(env, packet, g_packet_fields[0].id, int_val);

        // subtype
        int_val = (jint) FC_SUBTYPE(dot11_hdr->frame_ctrl);
        (*env)->SetIntField(env, packet, g_packet_fields[1].id, int_val);

        // from_ds
        bool_val = (jboolean) FC_FROM_DS(dot11_hdr->frame_ctrl);
        (*env)->SetBooleanField(env, packet, g_packet_fields[2].id, bool_val);

        // to_ds
        bool_val = (jboolean) FC_TO_DS(dot11_hdr->frame_ctrl);
        (*env)->SetBooleanField(env, packet, g_packet_fields[3].id, bool_val);

        // tv_sec
        int_val = (jint) header.ts.tv_sec;
        (*env)->SetIntField(env, packet, g_packet_fields[4].id, int_val);

        // tv_usec
        int_val = (jint) header.ts.tv_usec;
        (*env)->SetIntField(env, packet, g_packet_fields[5].id, int_val);

        // len
        int_val = (jint) pkt_len;
        (*env)->SetIntField(env, packet, g_packet_fields[6].id, int_val);

        // addr1
        get_mac_str(dot11_hdr->addr1, buf);
        str_val = (*env)->NewStringUTF(env, buf);
        (*env)->SetObjectField(env, packet, g_packet_fields[7].id, str_val);
        (*env)->DeleteLocalRef(env, str_val);

        // addr2
        if (FC_TYPE(dot11_hdr->frame_ctrl) != DOT11_TYPE_CTRL) {
            get_mac_str(dot11_hdr->addr2, buf);
            str_val = (*env)->NewStringUTF(env, buf);
            (*env)->SetObjectField(env, packet, g_packet_fields[8].id, str_val);
            (*env)->DeleteLocalRef(env, str_val);
        }
        else {
            (*env)->SetObjectField(env, packet, g_packet_fields[8].id, NULL);
        }

        // rssi
        int_val = (jint) radiotap_hdr->rssi;
        (*env)->SetIntField(env, packet, g_packet_fields[9].id, int_val);

        // freq
        int_val = (jint) radiotap_hdr->channel_mhz;
        (*env)->SetIntField(env, packet, g_packet_fields[10].id, int_val);

        // retry
        bool_val = (jboolean) FC_RETRY(dot11_hdr->frame_ctrl);
        (*env)->SetBooleanField(env, packet, g_packet_fields[11].id, bool_val);

        (*env)->CallVoidMethod(env, this, g_got_pkt, packet);
    }
    ret = true;
    LOGD(TAG, "Finish parsing pcap file %s", file_path);

parse_done:
    if (file_path != NULL) {
        (*env)->ReleaseStringUTFChars(env, file, file_path);
    }
    return ret;
}

static void dump_pkt(const uint8_t* pkt, size_t len)
{
    assert(pkt != NULL);

    uint8_t buf[1024];
    size_t i, _len;

    _len = len < sizeof(buf)/3? len: sizeof(buf)/3;

    for (i = 0; i < _len; i++) {
        sprintf(&(buf[i*3]), "%02x ", pkt[i]);
    }
    LOGD(TAG, "%s", buf);
}

static custom_radiotap_header_t* parse_radiotap_header(const uint8_t* ptr)
{
    assert(ptr != NULL);

    custom_radiotap_header_t* hdr = (custom_radiotap_header_t*) ptr;
    hdr->it_len = letoh16(hdr->it_len);
    hdr->it_present = letoh32(hdr->it_present);
    hdr->channel_mhz = letoh32(hdr->channel_mhz);
    hdr->channel_flags = letoh32(hdr->channel_mhz);
    return hdr;
}

static dot11_header_t* parse_dot11_header(const uint8_t* ptr)
{
    assert(ptr != NULL);

    dot11_header_t* hdr = (dot11_header_t*) ptr;
    hdr->frame_ctrl = letoh16(hdr->frame_ctrl);
    hdr->duration = letoh16(hdr->duration);
    return hdr;
}


/** Convert MAC address to it's string form.
 * 
 * @mac: pointer to the mac bytes array.
 * @buf: buffer to receive the string MAC.
 *
 */
static void get_mac_str(uint8_t* mac, char* buf)
{
    assert(mac != NULL);
    assert(buf != NULL);

    for (int i = 0; i < MAC_LEN-1; i++) {
        buf += sprintf(buf, "%02X:", mac[i]);
    }
    sprintf(buf, "%02X", mac[MAC_LEN-1]);
}


/** Get SSID from beacon frame.
 *
 * @pkt: should be a point to the start of a beacon frame.
 * @pkt_len: length of the packet, including FCS.
 * @buf: buffer to receive the SSID.
 * @buf_len: size of the buffer, should be large enough.
 *
 * Return the length of SSID, excluding trailing '\0' on success, or -1
 * otherwise.
 */
static int get_ssid(const uint8_t* pkt, size_t pkt_len, char* buf, size_t buf_len)
{
    assert(pkt != NULL);
    assert(buf != NULL);

    int reminder = pkt_len - DOT11_BEACON_HDR_LEN - DOT11_BEACON_FIXED_LEN - 4 /* FCS */;

    if (reminder <= 0) {
        LOGE(TAG, "Wrong packet length.");
        return -1;
    }

    const uint8_t* rbuf = pkt + DOT11_BEACON_HDR_LEN + DOT11_BEACON_FIXED_LEN;
    size_t processed = 0;

    uint8_t tag, len;
    while (reminder > 0) {
        tag = rbuf[processed++];
        len = rbuf[processed++];
        if (tag == DOT11_TAG_SSID) {
            memcpy(buf, &(rbuf[processed]), len);
            buf[len] = '\0';
            break;
        }
        processed += len;
        reminder -= len + 2;
    }
    if (reminder <= 0) {
        return -1;
    }
    else {
        return len;
    }
}
