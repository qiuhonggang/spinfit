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
