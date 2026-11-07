package com.spinfit.bike.core;

import java.util.Locale;

/**
 * 动感单车科学训练引擎：训练时钟、目标控制、功率/心率/速度推算与训练负荷统计。
 *
 * 纯 Java 实现，仅依赖 java.*，不引用任何 Android API 或第三方库。
 *
 * 关键约定：
 *  - 训练时钟按「模拟秒」推进：elapsed += realDtSec * timeScale；暂停/未开始/结束时不推进。
 *  - 阻力：autoResistance=true 取当前段目标阻力（四舍五入并 clamp），否则取手动阻力。
 *  - hrZone 采用 %maxHR 划分（1:&lt;60、2:&lt;70、3:&lt;80、4:&lt;90、5:&gt;=90）；
 *    当 maxHr 无效时回退到 %FTP 划分（1:&lt;55、2:&lt;75、3:&lt;90、4:&lt;105、5:&gt;=105）。
 *  - 热量：1 kJ 机械功 ≈ 1 kcal 膳食能量（人体效率约 24%，二者近似抵消），故直接用 W·s/1000。
 *  - rawResistance 参数保留在签名中但按契约不参与计算：实际阻力只由目标(auto)/手动决定。
 *  - source 字段由外部覆盖，这里填 "engine" 作为合理默认值。
 */
public class SessionEngine {

    private static final int NP_WINDOW_CAP = 4096;   // 30 秒滚动功率环形缓冲容量
    private static final double NP_WINDOW_SEC = 30.0;
    private static final double HR_TAU_SEC = 20.0;  // 心率一阶滞后时间常数
    private static final double SPEED_TAU_SEC = 1.5;
    private static final double COAST_TAU_SEC = 3.0; // 停止踩踏时速度缓慢衰减

    private TrainingProgram program;
    private final int ftpW;
    private final int maxHrBpm;
    private final float weightKg;
    private final int resistanceMax;

    private float timeScale = 1f;
    private boolean autoResistance = true;
    private int manualResistance = 0;

    private boolean running = false;
    private boolean paused = false;
    private boolean finished = false;

    private double elapsed = 0.0;          // 模拟秒
    private double speedKph = 0.0;         // 平滑后速度
    private double hrModel = 0.0;          // 心率模型当前值
    private int heartRateBpm = 0;
    private double distanceKm = 0.0;
    private double caloriesKcal = 0.0;

    private double powerIntegral = 0.0;    // 时间加权，用于平均功率
    private double cadenceIntegral = 0.0;  // 时间加权，用于平均踏频
    private double hrIntegral = 0.0;       // 时间加权，用于平均心率
    private int hrMax = 0;

    private final double[] zoneTime = new double[5];  // 按 %FTP 区间累计的模拟秒
    private double ftpTestPowerIntegral = 0.0;        // 20 分钟计时段功率积分
    private double ftpTestSec = 0.0;                  // 20 分钟计时段时长

    private float lastNp = 0f;
    private float lastIf = 0f;
    private float lastTss = 0f;

    // ---- 30 秒功率环形缓冲（时间 + 功率） ----
    private final double[] npTime = new double[NP_WINDOW_CAP];
    private final float[] npPower = new float[NP_WINDOW_CAP];
    private int npHead = 0;
    private int npCount = 0;

    private int lastRawResistance = 0;      // 仅记录，不参与计算（见类注释）

    public SessionEngine(TrainingProgram program, int ftpW, int maxHrBpm,
                         float weightKg, int resistanceMax) {
        this.ftpW = finiteInt(ftpW, 0);
        this.maxHrBpm = finiteInt(maxHrBpm, 0);
        this.weightKg = finiteFloat(weightKg, 0f);
        this.resistanceMax = Math.max(0, finiteInt(resistanceMax, 0));

        TrainingProgram p = program;
        if (p == null || p.segments == null || p.segments.length == 0) {
            Segment[] one = new Segment[]{new Segment("训练", "无有效方案数据", 0, 0f, 0f, 0, 1)};
            p = new TrainingProgram("empty", "空方案", "无结构化训练", "无可用方案数据。",
                    false, one);
        }
        this.program = p;

        reset();
    }

    // ------------------------------------------------------------------
    // 配置
    // ------------------------------------------------------------------

    /** 演示加速倍率，clamp 到 1..60；NaN/Inf 视为无操作。 */
    public void setTimeScale(float scale) {
        if (Float.isNaN(scale) || Float.isInfinite(scale)) return;
        this.timeScale = clamp(scale, 1f, 60f);
    }

    public float getTimeScale() {
        return timeScale;
    }

    public void setAutoResistance(boolean b) {
        this.autoResistance = b;
    }

    public boolean isAutoResistance() {
        return autoResistance;
    }

    /** 手动阻力，clamp 到 0..resistanceMax。 */
    public void setManualResistance(int level) {
        this.manualResistance = clamp(finiteInt(level, 0), 0, resistanceMax);
    }

    public int getManualResistance() {
        return manualResistance;
    }

    // ------------------------------------------------------------------
    // 生命周期
    // ------------------------------------------------------------------

    /** 开始（或恢复）会话。若上一次已结束，则重置全部统计后重新开始。 */
    public void start() {
        if (running) {
            paused = false;   // 兼作恢复，兼容外部用 isRunning() 控制按钮的写法
            return;
        }
        if (finished) reset();
        running = true;
        paused = false;
        finished = false;
    }

    public void pause() {
        if (running) paused = true;
    }

    public void resume() {
        if (running) paused = false;
    }

    /** 停止并标记 finished。 */
    public void stop() {
        running = false;
        paused = false;
        finished = true;
    }

    /**
     * 会话是否处于活跃状态：已 start、未 stop、未自然结束。
     * 暂停不改变该值（暂停由 isPaused() 单独表达），因此推进条件是
     * 「isRunning() 且未 pause」，与契约表述一致。
     */
    public boolean isRunning() {
        return running && !finished;
    }

    public boolean isPaused() {
        return paused;
    }

    public boolean isFinished() {
        return finished;
    }

    // ------------------------------------------------------------------
    // 当前目标
    // ------------------------------------------------------------------

    public Segment currentSegment() {
        Segment[] segs = program.segments;
        int idx = locateStage(elapsed);
        if (idx < 0 || idx >= segs.length) return null;
        return segs[idx];
    }

    /** ftpW * targetPowerPct/100；自由骑行返回 0。 */
    public float currentTargetPowerW() {
        if (program.freeRide) return 0f;
        Segment s = currentSegment();
        if (s == null || ftpW <= 0) return 0f;
        return finiteFloat((float) (ftpW * (double) s.targetPowerPct / 100.0), 0f);
    }

    /** resistanceMax * targetResistancePct/100；auto 关闭时返回 manualResistance。 */
    public float currentTargetResistance() {
        if (!autoResistance) return (float) manualResistance;
        Segment s = currentSegment();
        if (s == null || resistanceMax <= 0) return 0f;
        return finiteFloat((float) (resistanceMax * (double) s.targetResistancePct / 100.0), 0f);
    }

    public int currentTargetCadence() {
        Segment s = currentSegment();
        return s == null ? 0 : s.targetCadence;
    }

    /** 实际生效阻力：auto 时目标阻力四舍五入并 clamp；否则手动阻力。 */
    private int effectiveResistance() {
        if (!autoResistance) return clamp(manualResistance, 0, resistanceMax);
        Segment s = currentSegment();
        if (s == null || resistanceMax <= 0) return 0;
        double r = resistanceMax * (double) s.targetResistancePct / 100.0;
        if (Double.isNaN(r) || Double.isInfinite(r)) return 0;
        long rounded = Math.round(r);
        return clamp((int) rounded, 0, resistanceMax);
    }

    // ------------------------------------------------------------------
    // 核心推进
    // ------------------------------------------------------------------

    /**
     * 推进一步。只有 isRunning() 且 realDtSec 有效(&gt;0) 时才推进训练时钟与统计；
     * 其余情况仍返回一份完整快照，不产生任何副作用（除记录 rawResistance）。
     */
    public Metrics advance(float realDtSec, float rawPowerW, float rawCadenceRpm,
                           int rawHrBpm, int rawResistance) {
        lastRawResistance = finiteInt(rawResistance, 0);

        boolean active = running && !paused && !finished;
        boolean dtValid = !Float.isNaN(realDtSec) && !Float.isInfinite(realDtSec) && realDtSec > 0f;
        if (!active || !dtValid) {
            return snapshot();
        }

        double dtSim = (double) realDtSec * (double) timeScale;
        if (Double.isNaN(dtSim) || Double.isInfinite(dtSim) || dtSim <= 0.0) {
            return snapshot();
        }

        // 非自由骑行：把最后一帧裁剪到方案总时长，恰好结束，避免累计越界。
        if (!program.freeRide) {
            double total = program.totalSeconds();
            double remain = total - elapsed;
            if (remain <= 0.0) {
                finish();
                return snapshot();
            }
            if (dtSim > remain) dtSim = remain;
        }

        // --- 实测值清洗 ---
        float inPower = sanitizeInput(rawPowerW);
        float inCadence = sanitizeInput(rawCadenceRpm);
        boolean stopped = inPower <= 0f && inCadence <= 0f;
        float powerW = inPower;
        float cadenceRpm = inCadence;
        setLastSample(powerW, cadenceRpm);

        // --- 阻力（先算，速度依赖它）---
        int resistance = effectiveResistance();

        // --- 速度：cadence * (0.30 + 0.0015 * resistance)，轻微平滑；停止踩踏时缓慢衰减 ---
        double targetSpeed = (double) cadenceRpm * (0.30 + 0.0015 * (double) resistance);
        if (targetSpeed < 0.0 || !isFinite(targetSpeed)) targetSpeed = 0.0;
        double tau = stopped ? COAST_TAU_SEC : SPEED_TAU_SEC;
        double alphaSpeed = 1.0 - Math.exp(-dtSim / tau);
        speedKph += (targetSpeed - speedKph) * alphaSpeed;
        speedKph = finiteDouble(speedKph, 0.0);
        if (speedKph < 0.0) speedKph = 0.0;
        if (stopped && speedKph < 0.05) speedKph = 0.0;

        // --- 距离 / 热量 ---
        distanceKm += speedKph * (dtSim / 3600.0);
        // 1 kJ 机械功 ≈ 1 kcal 膳食能量（人体效率约 24%），故 W·s / 1000 ≈ kcal
        caloriesKcal += (double) powerW * dtSim / 1000.0;
        distanceKm = finiteDouble(distanceKm, 0.0);
        caloriesKcal = finiteDouble(caloriesKcal, 0.0);

        // --- 心率 ---
        updateHeartRate(dtSim, powerW, rawHrBpm);

        // --- 时间加权统计 ---
        elapsed += dtSim;
        powerIntegral += (double) powerW * dtSim;
        cadenceIntegral += (double) cadenceRpm * dtSim;
        hrIntegral += (double) heartRateBpm * dtSim;
        if (heartRateBpm > hrMax) hrMax = heartRateBpm;

        int pz = powerZone(powerW);
        zoneTime[pz - 1] += dtSim;

        Segment seg = currentSegment();
        if (seg != null && seg.name != null && seg.name.startsWith("20' 全力测试")) {
            ftpTestPowerIntegral += (double) powerW * dtSim;
            ftpTestSec += dtSim;
        }

        // --- 标准化功率 NP（30 秒滚动窗口 4 次方均值再开 4 次方） ---
        pushNp(elapsed, powerW);
        lastNp = finiteFloat(computeNp(elapsed), 0f);
        lastIf = computeIf(lastNp);
        lastTss = computeTss(lastNp, lastIf);

        // --- 结束判定 ---
        if (!program.freeRide && elapsed >= (double) program.totalSeconds() - 1e-6) {
            finish();
        }

        return snapshot();
    }

    /** 返回当前指标快照，不推进时间。 */
    public Metrics snapshot() {
        Metrics m = new Metrics();
        Segment seg = currentSegment();
        int total = program.totalSeconds();

        int elapsedSec = (int) Math.max(0L, Math.floor(elapsed));
        int remainingSec = Math.max(0, total - elapsedSec);

        m.powerW = lastPowerW;               // 最近一帧实测功率
        m.cadenceRpm = lastCadenceRpm;
        m.speedKph = finiteFloat((float) speedKph, 0f);
        m.distanceKm = finiteFloat((float) distanceKm, 0f);
        m.caloriesKcal = finiteFloat((float) caloriesKcal, 0f);

        m.targetPowerW = currentTargetPowerW();
        m.targetResistance = currentTargetResistance();
        m.targetCadence = (float) currentTargetCadence();

        m.avgPowerW = finiteFloat((float) (elapsed > 0.0 ? powerIntegral / elapsed : 0.0), 0f);
        m.normalizedPowerW = finiteFloat(lastNp, 0f);
        m.intensityFactor = finiteFloat(lastIf, 0f);
        m.tss = finiteFloat(lastTss, 0f);
        m.avgCadenceRpm = finiteFloat((float) (elapsed > 0.0 ? cadenceIntegral / elapsed : 0.0), 0f);

        m.heartRateBpm = clamp(heartRateBpm, 0, 220);
        m.resistance = effectiveResistance();
        m.resistanceMax = resistanceMax;
        m.elapsedSec = elapsedSec;
        m.remainingSec = remainingSec;
        m.stageIndex = Math.max(0, locateStage(elapsed));
        m.stageCount = program.segments == null ? 0 : program.segments.length;
        m.hrZone = computeHrZone(heartRateBpm, lastPowerW);

        m.stageName = seg == null ? "" : nullToEmpty(seg.name);
        m.stageHint = seg == null ? "" : nullToEmpty(seg.hint);
        m.programName = nullToEmpty(program.name);
        m.source = "engine";                 // 外部会覆盖

        m.finished = finished;
        m.paused = paused;
        m.autoResistance = autoResistance;
        m.isFreeRide = program.freeRide;
        return m;
    }

    /** 多行中文训练总结。 */
    public String summaryText() {
        StringBuilder sb = new StringBuilder();
        double elapsedSafe = Math.max(0.0, elapsed);
        int min = (int) (elapsedSafe / 60.0);
        int sec = (int) (elapsedSafe % 60.0);
        double avgPower = elapsedSafe > 0.0 ? powerIntegral / elapsedSafe : 0.0;
        double avgCadence = elapsedSafe > 0.0 ? cadenceIntegral / elapsedSafe : 0.0;
        double avgHr = elapsedSafe > 0.0 ? hrIntegral / elapsedSafe : 0.0;

        sb.append("===== 训练总结 =====\n");
        sb.append("方案：").append(nullToEmpty(program.name))
          .append("（").append(nullToEmpty(program.id)).append("）\n");
        sb.append("状态：").append(finished ? "已完成" : (paused ? "已暂停" : (running ? "进行中" : "未开始"))).append("\n");
        sb.append("总时长：").append(min).append(" 分 ").append(sec).append(" 秒（")
          .append(f1(elapsedSafe)).append(" 秒）\n");
        sb.append("体重设定：").append(f1(weightKg)).append(" kg\n");
        sb.append("平均功率：").append(f1(avgPower)).append(" W\n");
        sb.append("标准化功率 NP：").append(f1(lastNp)).append(" W\n");
        sb.append("强度因子 IF：").append(f2(lastIf)).append("\n");
        sb.append("训练负荷 TSS：").append(f1(lastTss)).append("\n");
        sb.append("平均踏频：").append(f1(avgCadence)).append(" rpm\n");
        sb.append("距离：").append(f2(distanceKm)).append(" km\n");
        sb.append("消耗：").append(f1(caloriesKcal)).append(" kcal\n");
        sb.append("最高/平均心率：").append(hrMax).append(" / ").append(f1(avgHr)).append(" bpm\n");

        sb.append("功率区间占比：");
        double zoneSum = 0.0;
        for (double z : zoneTime) zoneSum += z;
        for (int i = 0; i < 5; i++) {
            double pct = zoneSum > 0.0 ? zoneTime[i] * 100.0 / zoneSum : 0.0;
            if (i > 0) sb.append(" | ");
            sb.append("Z").append(i + 1).append(" ").append(f1(pct)).append("%");
        }
        sb.append("\n");

        if ("ftp20".equals(program.id)) {
            double avg20 = ftpTestSec > 0.0 ? ftpTestPowerIntegral / ftpTestSec : 0.0;
            double suggested = avg20 * 0.95;
            sb.append("建议 FTP = 20 分钟平均功率 × 0.95 ≈ ")
              .append(Math.round(finiteDouble(suggested, 0.0))).append(" W\n");
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // 内部实现
    // ------------------------------------------------------------------

    private float lastPowerW = 0f;
    private float lastCadenceRpm = 0f;

    /** 在 advance 中保存当帧实测值，供 snapshot/summary 使用。 */
    private void setLastSample(float powerW, float cadenceRpm) {
        this.lastPowerW = finiteFloat(powerW, 0f);
        this.lastCadenceRpm = finiteFloat(cadenceRpm, 0f);
    }

    private void updateHeartRate(double dtSim, float powerW, int rawHrBpm) {
        boolean realHr = rawHrBpm > 0;
        if (realHr) {
            int hi = maxHrBpm > 0 ? maxHrBpm : 220;
            int hr = clamp(finiteInt(rawHrBpm, 60), 60, hi);
            heartRateBpm = hr;
            hrModel = hr;   // 同步模型，切回模型时不跳变
            return;
        }
        if (maxHrBpm <= 0) {
            heartRateBpm = 0;
            hrModel = 0;
            return;
        }
        double ratio = ftpW > 0 ? (double) powerW / (double) ftpW : 0.0;
        ratio = clamp(ratio, 0.0, 1.4);
        double target = maxHrBpm * (0.50 + 0.40 * ratio);
        double alpha = 1.0 - Math.exp(-dtSim / HR_TAU_SEC);
        hrModel += (target - hrModel) * alpha;
        hrModel = finiteDouble(hrModel, maxHrBpm * 0.55);
        double hr = clamp(hrModel, 60.0, (double) maxHrBpm);
        heartRateBpm = (int) Math.round(finiteDouble(hr, 0.0));
    }

    private void pushNp(double t, float p) {
        npTime[npHead] = t;
        npPower[npHead] = finiteFloat(p, 0f);
        npHead = (npHead + 1) % NP_WINDOW_CAP;
        if (npCount < NP_WINDOW_CAP) npCount++;
    }

    /** 最近 30 秒功率的 4 次方均值再开 4 次方。 */
    private float computeNp(double now) {
        double cutoff = now - NP_WINDOW_SEC;
        double sum4 = 0.0;
        int n = 0;
        for (int k = 0; k < npCount; k++) {
            int idx = (npHead - 1 - k + NP_WINDOW_CAP * 2) % NP_WINDOW_CAP;
            if (npTime[idx] < cutoff) break;   // 由新到旧，时间递减，可提前退出
            double p = npPower[idx];
            double p2 = p * p;
            sum4 += p2 * p2;
            n++;
        }
        if (n <= 0 || sum4 <= 0.0) return 0f;
        double np = Math.pow(sum4 / (double) n, 0.25);
        return finiteFloat((float) np, 0f);
    }

    private float computeIf(float np) {
        if (ftpW <= 0) return 0f;
        double v = (double) np / (double) ftpW;
        v = clamp(finiteDouble(v, 0.0), 0.0, 3.0);
        return (float) v;
    }

    private float computeTss(float np, float ifactor) {
        if (ftpW <= 0 || elapsed <= 0.0) return 0f;
        double denom = (double) ftpW * 3600.0;
        if (denom <= 0.0) return 0f;
        double v = (elapsed * (double) np * (double) ifactor) / denom * 100.0;
        v = clamp(finiteDouble(v, 0.0), 0.0, 1000.0);
        return (float) v;
    }

    /** 功率区间（Coggan 简化）：1:&lt;55% 2:&lt;75% 3:&lt;90% 4:&lt;105% 5:&gt;=105% FTP。 */
    private int powerZone(float powerW) {
        if (ftpW <= 0 || powerW <= 0f) return 1;
        double pct = (double) powerW / (double) ftpW * 100.0;
        if (pct < 55.0) return 1;
        if (pct < 75.0) return 2;
        if (pct < 90.0) return 3;
        if (pct < 105.0) return 4;
        return 5;
    }

    /** hrZone：主用 %maxHR；maxHr 无效时回退到 %FTP。 */
    private int computeHrZone(int hr, float powerW) {
        if (maxHrBpm > 0 && hr > 0) {
            double pct = (double) hr / (double) maxHrBpm * 100.0;
            if (pct < 60.0) return 1;
            if (pct < 70.0) return 2;
            if (pct < 80.0) return 3;
            if (pct < 90.0) return 4;
            return 5;
        }
        int pz = powerZone(powerW);
        if (ftpW <= 0 || powerW <= 0f) return 1;
        return pz;
    }

    private int locateStage(double t) {
        Segment[] segs = program.segments;
        if (segs == null || segs.length == 0) return 0;
        double acc = 0.0;
        for (int i = 0; i < segs.length; i++) {
            Segment s = segs[i];
            double d = s == null ? 0.0 : Math.max(0.0, (double) s.durationSec);
            if (t < acc + d) return i;
            acc += d;
        }
        return segs.length - 1;
    }

    private void finish() {
        finished = true;
        running = false;
        paused = false;
    }

    private void reset() {
        elapsed = 0.0;
        speedKph = 0.0;
        heartRateBpm = 0;
        hrModel = 0.0;
        distanceKm = 0.0;
        caloriesKcal = 0.0;
        powerIntegral = 0.0;
        cadenceIntegral = 0.0;
        hrIntegral = 0.0;
        hrMax = 0;
        for (int i = 0; i < 5; i++) zoneTime[i] = 0.0;
        ftpTestPowerIntegral = 0.0;
        ftpTestSec = 0.0;
        lastNp = 0f;
        lastIf = 0f;
        lastTss = 0f;
        lastPowerW = 0f;
        lastCadenceRpm = 0f;
        npHead = 0;
        npCount = 0;
        finished = false;
        running = false;
        paused = false;
        // 手动阻力默认从第一段目标档位起步，避免新训练停留在 0 档
        Segment first = (program.segments != null && program.segments.length > 0)
                ? program.segments[0] : null;
        manualResistance = first == null ? 0
                : clamp(Math.round(resistanceMax * first.targetResistancePct / 100f), 0, resistanceMax);
        if (maxHrBpm > 0) {
            double init = maxHrBpm * 0.55;
            hrModel = init;
            heartRateBpm = (int) Math.round(clamp(init, 60.0, (double) maxHrBpm));
        }
    }

    // ------------------------------------------------------------------
    // 数值工具：全程防 NaN / Infinity / 除零
    // ------------------------------------------------------------------

    private static float sanitizeInput(float v) {
        if (Float.isNaN(v) || Float.isInfinite(v) || v < 0f) return 0f;  // 不可用/负值视为 0
        return v;
    }

    private static boolean isFinite(double v) {
        return !Double.isNaN(v) && !Double.isInfinite(v);
    }

    private static double finiteDouble(double v, double fallback) {
        return isFinite(v) ? v : fallback;
    }

    private static float finiteFloat(float v, float fallback) {
        return (Float.isNaN(v) || Float.isInfinite(v)) ? fallback : v;
    }

    private static int finiteInt(int v, int fallback) {
        return v; // int 无 NaN/Inf，保留以统一入口
    }

    private static double clamp(double v, double lo, double hi) {
        if (Double.isNaN(v)) return lo;
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }

    private static float clamp(float v, float lo, float hi) {
        if (Float.isNaN(v)) return lo;
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }

    private static int clamp(int v, int lo, int hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String f1(double v) {
        return String.format(Locale.ROOT, "%.1f", finiteDouble(v, 0.0));
    }

    private static String f2(double v) {
        return String.format(Locale.ROOT, "%.2f", finiteDouble(v, 0.0));
    }
}
