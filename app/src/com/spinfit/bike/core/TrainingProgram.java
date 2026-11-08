package com.spinfit.bike.core;

/**
 * 一份完整的训练方案（不可变元数据 + 阶段数组）。
 */
public class TrainingProgram {
    public final String id, name, subtitle, science;
    public final Segment[] segments;
    public final boolean freeRide;

    public TrainingProgram(String id, String name, String subtitle, String science,
                           boolean freeRide, Segment[] segments) {
        this.id = id;
        this.name = name;
        this.subtitle = subtitle;
        this.science = science;
        this.freeRide = freeRide;
        this.segments = segments;
    }

    public enum ResistanceMode { MANUAL, FIXED, STAGED }

    /** Free rides never impose a preset; other courses are classified by resistance only. */
    public ResistanceMode resistanceMode() {
        if(freeRide||segments==null||segments.length==0)return ResistanceMode.MANUAL;
        Float first=null;boolean changes=false;
        for(Segment segment:segments){
            if(segment==null||segment.durationSec<=0)continue;
            float level=segment.targetResistancePct;
            if(!Float.isFinite(level)||level<0||level>100)return ResistanceMode.MANUAL;
            if(first==null)first=level;else if(Math.abs(first-level)>.001f)changes=true;
        }
        return first==null?ResistanceMode.MANUAL:changes?ResistanceMode.STAGED:ResistanceMode.FIXED;
    }
    public String resistanceModeLabel(){
        switch(resistanceMode()){
            case STAGED:return "自动分段调阻";
            case FIXED:return "开始时设置固定阻力";
            default:return "手动调节阻力";
        }
    }

    /** 返回各 segment 时长之和（秒）。 */
    public int totalSeconds() {
        if (segments == null) return 0;
        int total = 0;
        for (Segment s : segments) {
            if (s != null && s.durationSec > 0) total += s.durationSec;
        }
        return total;
    }
}
