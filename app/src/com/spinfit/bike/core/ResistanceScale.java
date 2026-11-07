package com.spinfit.bike.core;

/** Device resistance units throughout; encoding follows the verified device profile. */
public final class ResistanceScale {
    private ResistanceScale() {}
    public static float target(float level,float min,float max,float step) {
        if(!Float.isFinite(min)||!Float.isFinite(max)||max<min){min=0;max=100;}
        if(!Float.isFinite(level))level=min;
        float value=Math.max(min,Math.min(max,level));
        if(Float.isFinite(step)&&step>0)value=min+Math.round((value-min)/step)*step;
        return Math.max(min,Math.min(max,value));
    }
    /** Use telemetry after a knob change; retain pending intent across quick repeated taps. */
    public static int step(int actual,int currentTarget,int delta,int min,int max,boolean commandPending){
        int base=!commandPending&&actual>=0?actual:currentTarget;
        return Math.max(min,Math.min(max,base+delta));
    }
    public static byte[] commandForDevice(String name,float level,float min,float max,float step){
        String n=name==null?"":name.toUpperCase(java.util.Locale.ROOT);
        // YS_M2M_08482 verified on hardware: 04 + UINT8 whole level, despite a 6-byte range.
        if(n.startsWith("YS_M2M_")||n.equals("YS_M2M")){
            int value=Math.round(target(level,min,max,step));
            return new byte[]{4,(byte)Math.max(0,Math.min(100,value))};
        }
        return command(level,min,max,step);
    }
    public static byte[] command(float level,float min,float max,float step) {
        int tenths=Math.round(target(level,min,max,step)*10);
        return new byte[]{4,(byte)tenths,(byte)(tenths>>8)};
    }
}
