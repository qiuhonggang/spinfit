package com.spinfit.bike.core;
/** Bluetooth SIG HRS 0x2A37. Invalid/contact-lost measurements remain unavailable. */
public final class HeartRatePacket {
    private HeartRatePacket() {}
    public static int decode(byte[] bytes) {
        if(bytes==null||bytes.length<2)return -1;
        int flags=bytes[0]&255;
        if((flags&4)!=0&&(flags&2)==0)return -1;
        boolean wide=(flags&1)!=0;
        if(wide&&bytes.length<3)return -1;
        int bpm=wide?(bytes[1]&255)|((bytes[2]&255)<<8):bytes[1]&255;
        return bpm>0&&bpm<=300?bpm:-1;
    }
}
