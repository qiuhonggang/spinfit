package com.spinfit.bike.core;

/**
 * 训练方案中的单个阶段（不可变）。
 * zone 为功率/心率强度区间，取值 1..5。
 * targetPowerPct 为 %FTP，0 表示该阶段无功率目标（如自由骑行）。
 * targetResistancePct 为占最大阻力的百分比 0..100。
 */
public class Segment {
    public final String name, hint;
    public final int durationSec, targetCadence, zone;   // zone: 1..5
    public final float targetPowerPct;      // %FTP, 0 表示无目标
    public final float targetResistancePct; // %最大阻力 0..100

    public Segment(String name, String hint, int durationSec, float targetPowerPct,
                   float targetResistancePct, int targetCadence, int zone) {
        this.name = name;
        this.hint = hint;
        this.durationSec = durationSec;
        this.targetPowerPct = targetPowerPct;
        this.targetResistancePct = targetResistancePct;
        this.targetCadence = targetCadence;
        this.zone = zone;
    }
}
