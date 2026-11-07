package com.spinfit.bike.core;

/**
 * 一次训练会话的实时指标快照。
 * 所有字段均为 public，便于上层 UI 直接读取；由 SessionEngine 负责完整填充。
 * 该类不依赖任何 Android API 或第三方库。
 */
public class Metrics {
    public float powerW, cadenceRpm, speedKph, distanceKm, caloriesKcal;
    public float targetPowerW, targetResistance, targetCadence;
    public float avgPowerW, normalizedPowerW, intensityFactor, tss, avgCadenceRpm;
    public int heartRateBpm, resistance, resistanceMax, elapsedSec, remainingSec, stageIndex, stageCount, hrZone;
    public String stageName, stageHint, programName, source;
    public boolean finished, paused, autoResistance, isFreeRide;
}
