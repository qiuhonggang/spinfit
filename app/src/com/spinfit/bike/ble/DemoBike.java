package com.spinfit.bike.ble;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 内置「演示单车」：无需真实硬件即可完整验证训练引擎、阻力调节、界面与流程。
 *
 * 所有数据都是模拟值，Sample.simulated=true，UI 必须明确标注「演示数据」，
 * 绝不能让用户误认为是真实单车实测（实机测试反馈的问题之一）。
 */
public final class DemoBike implements BikeSource {
    private final Handler h = new Handler(Looper.getMainLooper());
    private Listener listener;
    private volatile boolean running;
    private volatile int state = STATE_IDLE;
    private int ftp = 180, hrMax = 190, rmax = 32;
    private volatile int resistance = 11;
    private volatile float targetPowerPct = 60f;
    private volatile int targetCadence = 85;
    private volatile boolean paused;
    private volatile String lastWriteMessage = "演示单车：档位已即时作用于模拟功率模型";
    private float cadence, power, speed, distance, calories, hr = 105;
    private long lastMs;
    private final List<String> logs = new ArrayList<>();
    private final java.util.Random rnd = new java.util.Random(20261107L);

    public void configure(int ftpW, int maxHr, int resistanceMax) {
        this.ftp = Math.max(50, ftpW); this.hrMax = Math.max(120, maxHr);
        this.rmax = Math.max(8, resistanceMax);
    }

    public void setTarget(float powerPctFtp, int cadenceRpm) {
        this.targetPowerPct = Math.max(0, Math.min(200, powerPctFtp));
        this.targetCadence = Math.max(40, Math.min(130, cadenceRpm));
    }

    public void setPaused(boolean p) { paused = p; }

    @Override public void setListener(Listener l) { listener = l; }
    @Override public int type() { return TYPE_DEMO; }
    @Override public String name() { return "演示单车 · SpinFit Simulator"; }
    @Override public String address() { return "DE:MO:00:00:00:01"; }
    @Override public boolean isConnected() { return state == STATE_CONNECTED; }

    private void log(String s) {
        synchronized (logs) { logs.add(s); if (logs.size() > 80) logs.remove(0); }
        final String x = s;
        h.post(() -> { if (listener != null) listener.onLog(x); });
    }
    public List<String> logs() { synchronized (logs) { return new ArrayList<>(logs); } }

    private void fire(int st, String msg) {
        state = st;
        final int s = st; final String m = msg;
        h.post(() -> { if (listener != null) listener.onState(s, m); });
        log(msg);
    }

    @Override public void connect() {
        fire(STATE_CONNECTING, "正在初始化演示单车…");
        h.postDelayed(() -> {
            running = true;
            fire(STATE_CONNECTED, "演示单车已连接（模拟数据，非真实单车）");
            log("模拟数据源启动：功率/踏频/速度/心率/阻力/里程/卡路里（全部为模拟值）");
            loop();
        }, 400);
    }

    private void loop() {
        if (!running) return;
        long now = System.currentTimeMillis();
        float dt = lastMs == 0 ? 0.25f : Math.min(1f, (now - lastMs) / 1000f);
        lastMs = now;
        if (!paused && state == STATE_CONNECTED) step(dt);
        h.postDelayed(this::loop, 250);
    }

    private void step(float dt) {
        float gain = 0.35f + 1.1f * (resistance / (float) rmax);
        float want = ftp * targetPowerPct / 100f;
        if (want <= 0f) want = ftp * 0.55f;   // 自由骑行也有基础负荷
        float targetCad = targetCadence + (want > ftp ? 10 : 0);
        cadence += (targetCad - cadence) * Math.min(1f, dt * 1.2f);
        float noise = (rnd.nextFloat() - 0.5f) * 0.10f;
        float raw = want * gain * (0.85f + 0.3f * (cadence / 90f)) * (1f + noise);
        power += (raw - power) * Math.min(1f, dt * 1.5f);
        power = Math.max(0, Math.min(600, power));
        speed = cadence * (0.30f + 0.0015f * resistance) * 0.92f;
        distance += speed * dt / 3600f;
        calories += power * dt / 1000f;
        float targetHr = hrMax * (0.50f + 0.40f * Math.min(1.3f, power / (float) Math.max(1, ftp)));
        hr += (targetHr - hr) * Math.min(1f, dt / 20f);
        hr = Math.max(60, Math.min(hrMax, hr));

        Sample s = Sample.simulated("演示单车模型");
        s.powerW = power;            s.powerValid = true;
        s.cadenceRpm = cadence;      s.cadenceValid = true;
        s.speedKph = speed;         s.speedValid = true;
        s.heartRate = Math.round(hr); s.hrValid = true;
        s.resistance = resistance;   s.resistanceValid = true;
        s.distanceKm = distance;     s.distanceValid = true;
        s.calories = calories;       s.caloriesValid = true;
        final Sample f = s;
        h.post(() -> { if (listener != null) listener.onData(f); });
    }

    @Override public WriteResult writeResistance(int level, int resistanceMax) {
        int lv = Math.max(0, Math.min(level, resistanceMax));
        resistance = lv;
        lastWriteMessage = "演示单车：档位 " + lv + " 已作用于模拟功率模型";
        log(lastWriteMessage);
        return new WriteResult(true, true, lv,
                "演示模式：阻力 " + lv + " 档已生效（仅模拟，不会控制真实单车）");
    }

    @Override public boolean writeTargetPower(int watts) {
        log("演示单车：目标功率 " + watts + " W（仅模拟记录）");
        return true;
    }

    @Override public boolean startWorkout()  { log("演示单车：开始训练（仅模拟）"); return true; }
    @Override public boolean pauseWorkout()  { log("演示单车：暂停训练（仅模拟）"); return true; }
    @Override public boolean stopWorkout()   { log("演示单车：停止训练（仅模拟）"); return true; }
    @Override public boolean supportsResistanceTarget() { return true; }
    @Override public boolean hasControl() { return true; }
    @Override public int actualResistance() { return resistance; }
    @Override public String diagnostics() {
        return "数据源：演示单车（全部为模拟值，不是真实单车）\n"
                + "阻力：" + resistance + " 档（模拟即时生效）\n"
                + "连接真实单车请在「连接单车」页扫描 BLE 设备";
    }

    @Override public void disconnect() {
        running = false;
        fire(STATE_DISCONNECTED, "演示单车已断开");
    }
}
