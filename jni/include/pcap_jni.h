#ifndef _PCAP_JNI_H_
#define _PCAP_JNI_H_

#include <jni.h>
#include <sys/types.h>

#include "jni_utils.h"



/* custom radiotap header, see drivers/net/wireless/bcmdhd/wl_linux_mon.c */
typedef struct custom_radiotap_header {
    uint8_t it_version;
    uint8_t it_pad;
    uint16_t it_len;
    uint32_t it_present;

    uint8_t radiotap_flags;
    uint8_t padding_for_radiotap_flags;

    uint16_t channel_mhz;
    uint16_t channel_flags;

    uint8_t rssi;
    uint8_t padding_for_rssi;

} _P_ custom_radiotap_header_t;

#define FC_MASK_ORDER           (((uint16_t)1)<<15)
#define FC_MASK_PROTECTED       (((uint16_t)1)<<14)
#define FC_MASK_MORE_DATA       (((uint16_t)1)<<13)
#define FC_MASK_PWR_MNG         (((uint16_t)1)<<12)
#define FC_MASK_RETRY           (((uint16_t)1)<<11)
#define FC_MASK_MORE_FRAG       (((uint16_t)1)<<10)
#define FC_MASK_FROM_DS         (((uint16_t)1)<<9)
#define FC_MASK_TO_DS           (((uint16_t)1)<<8)

#define FC_ORDER(fc)            (((fc) & FC_MASK_ORDER)? true: false)
#define FC_PROTECTED(fc)        (((fc) & FC_MASK_PROTECTED)? true: false)
#define FC_MORE_DATA(fc)        (((fc) & FC_MASK_MORE_DATA)? true: false)
#define FC_PWR_MNG(fc)          (((fc) & FC_MASK_PWR_MNG)? true: false)
#define FC_RETRY(fc)            (((fc) & FC_MASK_RETRY)? true: false)
#define FC_MORE_FRAG(fc)        (((fc) & FC_MASK_MORE_FRAG)? true: false)
#define FC_FROM_DS(fc)          (((fc) & FC_MASK_FROM_DS)? true: false)
#define FC_TO_DS(fc)            (((fc) & FC_MASK_TO_DS)? true: false)

#define FC_TYPE(fc)             (((fc) & 0x00c0) >> 2)
#define FC_SUBTYPE(fc)          (((fc) & 0x00f0) >> 4)
#define FC_PROTO_VER(fc)        (((fc) & 0x0003))

#define MAC_LEN 6

#define DOT11_TYPE_MGMT         0
#define DOT11_TYPE_CTRL         1
#define DOT11_TYPE_DATA         2
#define DOT11_TYPE_RSVD         3

#define DOT11_SUBTYPE_BEACON    8
#define DOT11_SUBTYPE_DATA      0
#define DOT11_SUBTYPE_QOS_DATA  8

typedef struct dot11_header {
    uint16_t frame_ctrl;
    uint16_t duration;
    uint8_t addr1[MAC_LEN];
    uint8_t addr2[];
} _P_ dot11_header_t;

#define DOT11_PKT_LEN(len)  ((len) - sizeof(custom_radiotap_header_t))



JNIEXPORT jboolean JNICALL Java_edu_buffalo_cse_pocketsniffer_SnifTask_parsePcap(JNIEnv* env, jobject this, jstring file);
#endif /* _PCAP_JNI_H_ */
