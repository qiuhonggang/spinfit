package com.spinfit.bike.core;

/** Device resistance units throughout; only the FTMS wire format uses tenths. */
public final class ResistanceScale {
    private ResistanceScale() {}
    public static float target(float level,float min,float max,float step) {
        if(!Float.isFinite(min)||!Float.isFinite(max)||max<min){min=0;max=100;}
        if(!Float.isFinite(level))level=min;
        float value=Math.max(min,Math.min(max,level));
        if(Float.isFinite(step)&&step>0)value=min+Math.round((value-min)/step)*step;
        return Math.max(min,Math.min(max,value));
    }
    public static byte[] command(float level,float min,float max,float step) {
        int tenths=Math.round(target(level,min,max,step)*10);
        return new byte[]{4,(byte)tenths,(byte)(tenths>>8)};
    }
}
