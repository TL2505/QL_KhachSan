package quanlykhachsan.backend.utils;

import java.nio.charset.StandardCharsets;

/** EMVCo parameters approved for Phase 2: poly 0x1021, init 0xFFFF, no reflection. */
public final class Crc16Ccitt {
    private Crc16Ccitt() { }
    public static String checksum(String payload) {
        int crc=0xffff;
        for(byte value:payload.getBytes(StandardCharsets.UTF_8)) {
            crc^=(value&0xff)<<8;
            for(int bit=0;bit<8;bit++)crc=((crc&0x8000)!=0?(crc<<1)^0x1021:crc<<1)&0xffff;
        }
        return String.format(java.util.Locale.ROOT,"%04X",crc);
    }
}
