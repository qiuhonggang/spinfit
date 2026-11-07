package com.spinfit.bike.ble;

/**
 * 单车数据源抽象：BLE 真实设备或内置演示单车都实现同一接口。
 *
 * 重要约定：Sample 里每个字段都带 xxxValid 标志。
 * - true  = 该数值来自设备实测（或演示单车模型）
 * - false = 设备未提供该字段，UI 必须显示 "--"，不得用模型推算值冒充实测。
 */
public interface BikeSource {
    int TYPE_DEMO = 0;
    int TYPE_FTMS = 1;
    int TYPE_PRIVATE = 2;
    int TYPE_POWER = 3;
    int TYPE_CSC = 4;

    interface Listener {
        void onState(int state, String message);
        void onData(Sample s);
        void onLog(String line);
    }

    int STATE_IDLE = 0;
    int STATE_SCANNING = 1;
    int STATE_CONNECTING = 2;
    int STATE_CONNECTED = 3;
    int STATE_DISCONNECTED = 4;
    int STATE_ERROR = 5;

    /** 一帧实时数据。字段是否可用由 xxxValid 决定，未使用的值一律填 -1。 */
    final class Sample {
        public float powerW = -1, cadenceRpm = -1, speedKph = -1;
        public int heartRate = -1, resistance = -1;
        public float distanceKm = -1, calories = -1;
        public boolean powerValid, cadenceValid, speedValid, hrValid,
                resistanceValid, distanceValid, caloriesValid;
        /** 数据是否为演示/模拟源（UI 需明确标注，避免误认为真实单车数据）。 */
        public boolean simulated;
        public boolean hrExternal;
        public String hrSource = "";
        /** 数据来源说明，如「FTMS 0x2AD2」「演示单车模型」。 */
        public String source = "";
        public long timestampMs = System.currentTimeMillis();

        public Sample copy() {
            Sample c=new Sample();c.powerW=powerW;c.cadenceRpm=cadenceRpm;c.speedKph=speedKph;c.heartRate=heartRate;c.resistance=resistance;c.distanceKm=distanceKm;c.calories=calories;
            c.powerValid=powerValid;c.cadenceValid=cadenceValid;c.speedValid=speedValid;c.hrValid=hrValid;c.resistanceValid=resistanceValid;c.distanceValid=distanceValid;c.caloriesValid=caloriesValid;
            c.simulated=simulated;c.hrExternal=hrExternal;c.hrSource=hrSource;c.source=source;c.timestampMs=timestampMs;return c;
        }
        public static Sample measured() {
            Sample s = new Sample();
            s.simulated = false;
            return s;
        }
        public static Sample simulated(String src) {
            Sample s = new Sample();
            s.simulated = true;
            s.source = src;
            return s;
        }
    }

    /** 阻力写入结果：界面据此明确提示「自动/手动/只读」状态。 */
    final class WriteResult {
        public final boolean sent;          // 是否真的下发到 BLE
        public final boolean supported;     // 单车是否声明支持程序控阻
        public final int requestedLevel;    // APP 想设置的档位
        public final String message;        // 面向用户的一句话说明
        public WriteResult(boolean sent, boolean supported, int requestedLevel, String message) {
            this.sent = sent; this.supported = supported;
            this.requestedLevel = requestedLevel; this.message = message;
        }
    }

    void setListener(Listener l);
    void connect();
    void disconnect();
    boolean isConnected();
    int type();
    String name();
    String address();

    /** 写入目标阻力，返回结构化结果（不再只返回 boolean）。 */
    WriteResult writeResistance(int level, int resistanceMax);
    /** 写入目标功率（FTMS 0x2AD9 / 私有协议尽力而为）。 */
    boolean writeTargetPower(int watts);

    /** 训练会话控制：开始/继续、暂停、停止（下发到单车屏幕）。 */
    boolean startWorkout();
    boolean pauseWorkout();
    boolean stopWorkout();

    /** 单车是否声明支持「目标阻力」控制（读 0x2ACC 得到）。 */
    boolean supportsResistanceTarget();
    /** 是否已获得 FTMS 控制权（RequestControl 成功）。 */
    boolean hasControl();
    /** 单车屏幕上一次实际生效的阻力（0x2ADA 状态上报 / 遥测），-1 表示未知。 */
    int actualResistance();
    /** 连接/协议诊断摘要（供 UI 与日志显示）。 */
    String diagnostics();
}
