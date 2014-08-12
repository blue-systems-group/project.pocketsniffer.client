#ifndef _CRC_H_
#define _CRC_H_

#include <sys/types.h>

uint32_t crc32(const uint8_t* pkt, size_t pkt_len);
bool check_crc(const uint8_t* pkt, size_t pkt_len);
#endif /* _CRC_H_ */
