package com.spinfit.bike.ble;

import com.spinfit.bike.core.ResistanceScale;
import com.spinfit.bike.core.ResistanceCommandState;
import android.os.SystemClock;
import android.util.Log;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.UUID;

/**
 * 真实 BLE 单车连接。
 *
 * 设计要点（针对实机测试发现的问题修正）：
 *  - 0x2AD9 控制点是 Write + Indicate，必须订阅 CCCD 才能收到 [0x80,op,result] 响应，
 *    否则永远拿不到控制权、所有控制命令都会被拦截。
 *  - FTMS 规定任何控制命令前必须 0x00 RequestControl 成功；训练开始必须下发 0x07 Start/Resume，
 *    单车屏幕才会进入训练会话并显示数据。
 *  - Sample 只上报「设备实测」字段；设备没给的字段一律 xxxValid=false，UI 显示 "--"，
 *    绝不用模型推算值冒充实测（速度/心率/功率都不编造）。
 *  - 阻力写入返回结构化结果：是否真的下发、单车是否支持程序控阻、目标档位、用户可读说明。
 */
public final class BleBike implements BikeSource {

    public static final UUID FTMS_SERVICE = uuid(0x1826);
    public static final UUID INDOOR_BIKE  = uuid(0x2AD2);
    public static final UUID FM_CONTROL  = uuid(0x2AD9);
    public static final UUID FM_FEATURE  = uuid(0x2ACC);
    public static final UUID FM_STATUS   = uuid(0x2ADA);
    public static final UUID RES_RANGE   = uuid(0x2AD6);
    public static final UUID POWER_RANGE = uuid(0x2AD8);
    public static final UUID POWER_SERVICE = uuid(0x1818);
    public static final UUID POWER_MEAS  = uuid(0x2A63);
    public static final UUID CSC_SERVICE = uuid(0x1816);
    public static final UUID CSC_MEAS    = uuid(0x2A5B);
    public static final UUID HR_SERVICE  = uuid(0x180D);
    public static final UUID HR_MEAS     = uuid(0x2A37);
    public static final UUID NUS_SERVICE = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb");
    public static final UUID NUS_TX      = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb");
    public static final UUID NUS_RX      = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb");
    public static final UUID ALT_SERVICE = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb");
    public static final UUID ALT_CHAR    = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb");
    public static final UUID LED_WRITE   = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb");
    public static final UUID VENDOR_WRITE = UUID.fromString("d18d2c10-c44c-11e8-a355-529269fb1459");
    public static final UUID CCCD        = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private final Context ctx;
    private final Handler h = new Handler(Looper.getMainLooper());
    private Listener listener;
    private final String address, name;
    private final int kind;
    private BluetoothAdapter adapter;
    private BluetoothGatt gatt;
    private volatile int state = STATE_IDLE;
    private final List<String> logs = new ArrayList<>();

    /** 待订阅的通知/指示特征（含 0x2AD9 控制点响应）。 */
    private final Queue<BluetoothGattCharacteristic> subscribeQueue = new ArrayDeque<>();
    private BluetoothGattCharacteristic writeChar, ctrlChar, statusChar;
    private volatile boolean controlGranted, controlPointSubscribed;
    private final Queue<byte[]> pendingControl = new ArrayDeque<>();
    private volatile boolean writingControl;
    private final Queue<UUID> probes = new ArrayDeque<>();
    private volatile boolean initialized;
    private boolean discovering,servicesHandled;
    private int inFlightResistance=-1;
    private long resistanceObservedAt;
    private final ResistanceCommandState resistanceCommand=new ResistanceCommandState();
    private int waitingOpcode = -1;
    private final Runnable controlTimeout = () -> {
        if (waitingOpcode >= 0) {
            log("控制命令超时 op=" + waitingOpcode);
            lastWriteMessage = "单车控制响应超时，请重试";
            if(waitingOpcode==4)resistanceCommand.fail(inFlightResistance,"单车控制响应超时",true);
            writingControl = false; waitingOpcode = -1; pendingControl.clear();
        }
    };
    public String controlStatus(){return resistanceCommand.target()>=0?resistanceCommand.message(SystemClock.elapsedRealtime()):lastWriteMessage;}
    public boolean resistanceNeedsRetry(int target){return resistanceCommand.shouldRetry(target,SystemClock.elapsedRealtime());}
    public boolean resistanceOverridden(){return resistanceCommand.overridden();}
    public void resetResistanceCommand(){resistanceCommand.clear();pendingControl.removeIf(p->p.length>0&&u8(p[0])==4);}
    public void cancelPendingResistance(){pendingControl.removeIf(p->p.length>0&&u8(p[0])==4);}
    /** Actual telemetry is already in the bike's units; never normalize to app settings. */
    public int displayResistance(){return actualRes;}
    public int resistanceMaximum(){return Math.max(1,(int)Math.floor(resistanceMaxSpec));}
    public int resistanceMinimum(){return Math.max(0,(int)Math.ceil(resistanceMin));}
    private void probeNext(){
        if(gatt==null)return;
        UUID u=probes.poll();
        if(u==null){subscribeNext();return;}
        BluetoothGattCharacteristic c=findChar(u);
        if(c==null||(c.getProperties()&BluetoothGattCharacteristic.PROPERTY_READ)==0){probeNext();return;}
        try{if(!gatt.readCharacteristic(c))h.post(this::probeNext);}catch(SecurityException e){h.post(this::probeNext);}
    }

    // 累计量（由设备遥测累加，不从推算速度累加）
    private long lastWheelRevs, lastCrankRevs;
    private int lastWheelTime, lastCrankTime;
    private float distanceKm, calories;
    private long lastSampleMs, lastWheelMs, lastCrankMs;
    // 最近一次「实测」值 + 时间戳；不同特征分帧到达，按新鲜度窗口合并到一帧 Sample
    private static final long STALE_MS = 4000L;
    private float speedKph = -1, cadenceRpm = -1, powerW = -1, hr = -1, res = -1;
    private long speedMs, cadenceMs, powerMs, hrMs, resMs, distanceMs, caloriesMs;
    private StringBuilder nusBuffer = new StringBuilder();

    /** FTMS 0x2ACC Target Setting Features（-1 = 未读到）。bit2 目标阻力, bit3 目标功率 */
    private long targetSettingFeatures = -1;
    private float resistanceMin = 0, resistanceMaxSpec = 100, resistanceInc = 1;
    /** 单车上报的实际阻力（0x2ADA 状态 0x07 或 0x2AD2 遥测），-1=未知 */
    private volatile int actualRes = -1;
    private volatile String lastWriteMessage = "尚未下发阻力指令";
    private volatile boolean lastWriteSent;

    public BleBike(Context c, String address, String name, int kind) {
        this.ctx = c; this.address = address; this.name = name; this.kind = kind;
    }

    private static UUID uuid(int a) {
        return UUID.fromString(String.format(Locale.US, "0000%04x-0000-1000-8000-00805f9b34fb", a));
    }

    @Override public void setListener(Listener l) { this.listener = l; }
    @Override public int type() { return kind; }
    @Override public String name() { return name; }
    @Override public String address() { return address; }
    @Override public boolean isConnected() { return state == STATE_CONNECTED; }
    @Override public boolean supportsResistanceTarget() {
        return targetSettingFeatures >= 0 && (targetSettingFeatures & (1L << 2)) != 0;
    }
    public boolean supportsPowerTarget() {
        return targetSettingFeatures >= 0 && (targetSettingFeatures & (1L << 3)) != 0;
    }
    @Override public boolean hasControl() { return controlGranted; }
    @Override public int actualResistance() { return actualRes; }
    @Override public String diagnostics() {
        StringBuilder sb = new StringBuilder();
        sb.append("连接: ").append(state == STATE_CONNECTED ? "已连接" : "未连接");
        sb.append(" | 数据源: ").append(kind == TYPE_FTMS ? "FTMS" : kind == TYPE_PRIVATE ? "透传" : "BLE");
        sb.append("\n控制权(0x00): ").append(controlGranted ? "✓ 已获得" : "✗ 未获得");
        sb.append(" | 控制点订阅: ").append(controlPointSubscribed ? "✓" : "✗");
        sb.append("\n程序控阻: ").append(supportsResistanceTarget() ? "✓ 单车声明支持 0x04"
                : targetSettingFeatures < 0 ? "? 未读到 0x2ACC（无法确认）" : "✗ 单车未声明（很可能只读）");
        sb.append("\n单车实际阻力: ").append(actualRes >= 0 ? actualRes : "未知（单车未上报 0x2ADA/0x2AD2 阻力）");
        sb.append("\n控阻反馈: ").append(controlStatus());
        sb.append("\n实测字段: ").append(fieldsSummary());
        return sb.toString();
    }

    private String fieldsSummary() {
        long now = System.currentTimeMillis();
        StringBuilder s = new StringBuilder();
        if (now - powerMs    < STALE_MS && powerW    >= 0) s.append("功率 ");
        if (now - cadenceMs  < STALE_MS && cadenceRpm >= 0) s.append("踏频 ");
        if (now - speedMs    < STALE_MS && speedKph  >= 0) s.append("速度 ");
        if (now - hrMs       < STALE_MS && hr        >= 0) s.append("心率 ");
        if (now - resMs      < STALE_MS && res       >= 0) s.append("阻力 ");
        if (now - distanceMs < STALE_MS) s.append("里程 ");
        if (now - caloriesMs < STALE_MS) s.append("卡路里 ");
        return s.length() == 0 ? "（暂无实测数据）" : s.toString().trim();
    }

    private void fire(final int st, final String msg) {
        state = st;
        h.post(() -> { if (listener != null) listener.onState(st, msg); });
        log(msg);
    }

    private void log(String s) {
        if (s == null) return;
        if(!s.contains(" → ")||s.startsWith("写入"))Log.d("SpinFitBike",s);
        synchronized (logs) { logs.add(s); if (logs.size() > 120) logs.remove(0); }
        final String x = s;
        h.post(() -> { if (listener != null) listener.onLog(x); });
    }

    public List<String> logs() { synchronized (logs) { return new ArrayList<>(logs); } }

    @Override public void connect() {
        discovering=false;servicesHandled=false;resistanceObservedAt=0;resistanceCommand.clear();
        BluetoothManager bm = (BluetoothManager) ctx.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bm == null) { fire(STATE_ERROR, "设备不支持蓝牙"); return; }
        adapter = bm.getAdapter();
        if (adapter == null || !adapter.isEnabled()) { fire(STATE_ERROR, "蓝牙未开启，请先打开蓝牙"); return; }
        BluetoothDevice dev;
        try { dev = adapter.getRemoteDevice(address); }
        catch (IllegalArgumentException e) { fire(STATE_ERROR, "无效的蓝牙地址"); return; }
        fire(STATE_CONNECTING, "正在连接 " + name + " …");
        try {
            gatt = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                    ? dev.connectGatt(ctx, false, cb, BluetoothDevice.TRANSPORT_LE)
                    : dev.connectGatt(ctx, false, cb);
        } catch (SecurityException e) { fire(STATE_ERROR, "缺少蓝牙权限：" + e.getMessage()); }
    }

    @Override public void disconnect() {
        if (gatt != null) {
            try { gatt.disconnect(); gatt.close(); } catch (SecurityException ignored) {}
            gatt = null;
        }
        controlGranted = false; writingControl = false; initialized=false;
        h.removeCallbacksAndMessages(null); pendingControl.clear(); probes.clear(); waitingOpcode=-1;
        fire(STATE_DISCONNECTED, "已断开连接");
    }

    private final BluetoothGattCallback cb = new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt g, int status, int newState) {
            if (status != BluetoothGatt.GATT_SUCCESS) { fire(STATE_ERROR, "连接失败 status=" + status); return; }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                if(discovering)return;discovering=true;
                fire(STATE_CONNECTING, "已连接，正在发现服务…");
                try { g.discoverServices(); }
                catch (SecurityException e) { fire(STATE_ERROR, "发现服务失败：" + e.getMessage()); }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                fire(STATE_DISCONNECTED, "连接已断开");
            }
        }

        @Override public void onServicesDiscovered(BluetoothGatt g, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) { fire(STATE_ERROR, "服务发现失败 " + status); return; }
            if(servicesHandled)return;servicesHandled=true;
            controlGranted = false; controlPointSubscribed = false;
            subscribeQueue.clear(); pendingControl.clear(); writingControl = false;
            writeChar = null; ctrlChar = null; statusChar = null;
            StringBuilder found = new StringBuilder();
            for (BluetoothGattService s : g.getServices()) {
                found.append(s.getUuid().toString().substring(0, 8)).append(" ");
                for (BluetoothGattCharacteristic c : s.getCharacteristics()) {
                    UUID u = c.getUuid();
                    int props = c.getProperties();
                    boolean notify = (props & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
                            || (props & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0;
                    boolean writable = (props & (BluetoothGattCharacteristic.PROPERTY_WRITE
                            | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0;
                    // 关键：0x2AD9 是 Indicate，必须订阅才能收到控制响应
                    if (u.equals(FM_CONTROL)) {
                        if (writable) ctrlChar = c;
                        if (notify) subscribeQueue.add(c);
                        log("特征 2AD9 控制点 props=" + props + " [write=" + writable + ", indicate=" + notify + "]");
                    } else if (u.equals(FM_STATUS)) {
                        statusChar = c;
                        if (notify) subscribeQueue.add(c);
                    } else if (isKnownData(u) && notify) {
                        subscribeQueue.add(c);
                    }
                    if (isKnownWrite(u) && writable && writeChar == null) writeChar = c;
                    log("特征 " + shortUuid(u) + " props=" + props
                            + (notify ? " [notify/indicate]" : "") + (writable ? " [write]" : ""));
                }
            }
            log("发现服务: " + found.toString().trim());
            if (writeChar == null) {
                for (BluetoothGattService s : g.getServices())
                    for (BluetoothGattCharacteristic c : s.getCharacteristics())
                        if ((c.getProperties() & (BluetoothGattCharacteristic.PROPERTY_WRITE
                                | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0) {
                            writeChar = c; break;
                        }
            }
            // FTMS 能力探测
            initialized=false;probes.clear();probes.add(FM_FEATURE);probes.add(RES_RANGE);probes.add(POWER_RANGE);
            probeNext();
            fire(STATE_CONNECTED, "单车已连接 · " + name + "（正在初始化控制通道）");

        }

        private void ensureControl() {
            if(gatt==null)return;
            if (ctrlChar == null) { log("无 0x2AD9 控制点：单车可能只读，控制命令不可用"); return; }
            if (!controlPointSubscribed) {
                log("控制点 CCCD 尚未订阅完成，稍后重试");
                h.postDelayed(this::ensureControl, 800);
                return;
            }
            if (!controlGranted) sendControl(new byte[]{0x00}, "RequestControl(0x00)", true);
        }

        @Override public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic c) {
            UUID u = c.getUuid();
            byte[] v = c.getValue();
            if(u.equals(FM_CONTROL)){final byte[] packet=v==null?null:v.clone();h.post(()->handleControlResponse(packet));return;}
            if(u.equals(FM_STATUS)){final byte[] packet=v==null?null:v.clone();h.post(()->handleMachineStatus(packet));return;}
            parse(u, v);
        }

        @Override public void onCharacteristicRead(BluetoothGatt g, BluetoothGattCharacteristic c, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                log("读取 " + shortUuid(c.getUuid()) + " 失败 " + status);
                h.post(BleBike.this::probeNext);return;
            }
            byte[] v = c.getValue();
            if (v == null) {h.post(BleBike.this::probeNext);return;}
            UUID u = c.getUuid();
            if(u.equals(RES_RANGE)&&v.length==3){
                resistanceMin=u8(v[0]);resistanceMaxSpec=u8(v[1]);resistanceInc=u8(v[2]);
            } else if (u.equals(RES_RANGE) && v.length >= 6) {
                resistanceMin = s16(v[0], v[1]) / 10f;
                resistanceMaxSpec = s16(v[2], v[3]) / 10f;
                resistanceInc = u16(v[4], v[5]) / 10f;
                log(String.format(Locale.US, "单车阻力范围(2AD6): %.1f..%.1f 步进 %.1f",
                        resistanceMin, resistanceMaxSpec, resistanceInc));
            } else if (u.equals(FM_FEATURE) && v.length >= 8) {
                long feat = ((long) u8(v[0]) | ((long) u8(v[1]) << 8) | ((long) u8(v[2]) << 16)
                        | ((long) u8(v[3]) << 24)) & 0xFFFFFFFFL;
                targetSettingFeatures = ((long) u8(v[4]) | ((long) u8(v[5]) << 8)
                        | ((long) u8(v[6]) << 16) | ((long) u8(v[7]) << 24)) & 0xFFFFFFFFL;
                log(String.format(Locale.US, "FTMS Feature=0x%08X TargetSetting=0x%08X", feat, targetSettingFeatures));
                log(supportsResistanceTarget()
                        ? "✓ 单车声明支持「目标阻力」控制（0x04 可用）"
                        : "✗ 单车未声明目标阻力控制 → 阻力由物理旋钮控制，APP 只能显示目标、提示手动调节");
            } else if (u.equals(POWER_RANGE) && v.length >= 6) {
                log(String.format(Locale.US, "单车功率范围(2AD8): %d..%d W", s16(v[0], v[1]), s16(v[2], v[3])));
            }
            h.post(BleBike.this::probeNext);
        }

        @Override public void onCharacteristicWrite(BluetoothGatt g, BluetoothGattCharacteristic c, int status) {
            log("写入 " + shortUuid(c.getUuid()) + " → " + (status == 0 ? "已发出" : "失败(" + status + ")"));
            if(c.getUuid().equals(FM_CONTROL)&&status!=0)h.post(()->{
                if(waitingOpcode==4)resistanceCommand.fail(inFlightResistance,"阻力指令写入失败",true);
                h.removeCallbacks(controlTimeout);writingControl=false;waitingOpcode=-1;
                lastWriteMessage="阻力指令写入失败，连接恢复后请重试";pendingControl.clear();
            });
        }

        @Override public void onDescriptorWrite(BluetoothGatt g, BluetoothGattDescriptor d, int status) {
            UUID cu = d.getCharacteristic().getUuid();
            log("CCCD 订阅 " + shortUuid(cu) + " → " + (status == 0 ? "成功" : "失败(" + status + ")"));
            if (cu.equals(FM_CONTROL) && status == 0) controlPointSubscribed = true;
            subscribeNext();
        }
    };

    private void handleControlResponse(byte[] v){
        if(v==null||v.length<3||u8(v[0])!=128)return;
        int op=u8(v[1]),result=u8(v[2]);
        log("控制点回执 "+Arrays.toString(v));
        if(op!=waitingOpcode){log("忽略非当前命令回执 op="+op);return;}
        int physicalTarget=inFlightResistance;
        h.removeCallbacks(controlTimeout);writingControl=false;waitingOpcode=-1;
        if(result==5){controlGranted=false;pendingControl.clear();}
        String why=result==2?"单车不支持该操作":result==3?"单车拒绝参数":result==4?"单车操作失败":result==5?"控制权已失效":"单车返回错误 "+result;
        if(op==0&&result==1){controlGranted=true;log("已获得单车控制权");}
        else if(op==4){resistanceCommand.response(physicalTarget,result==1,why);log(result==1?"阻力指令已接收，等待遥测确认目标 "+physicalTarget:why);}
        if(controlGranted)flushPendingControl();
    }
    private void handleMachineStatus(byte[] v){
        if(v==null||v.length==0)return;
        int op=u8(v[0]);
        if(op==7&&v.length>=3){int target=Math.round(s16(v[1],v[2])/10f);log("单车报告目标阻力 "+target+"；实际阻力以遥测为准");}
        else log("单车状态 "+Arrays.toString(v));
    }

    private void subscribeNext() {
        if (gatt == null) return;
        BluetoothGattCharacteristic c = subscribeQueue.poll();
        if(c==null){initialized=true;if(ctrlChar!=null)ensureControlLater();return;}
        try {
            gatt.setCharacteristicNotification(c, true);
            BluetoothGattDescriptor d = c.getDescriptor(CCCD);
            if (d != null) {
                boolean indicate = (c.getProperties() & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0;
                d.setValue(indicate ? BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                        : BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                if(!gatt.writeDescriptor(d))h.post(this::subscribeNext);
                log("订阅 " + shortUuid(c.getUuid()) + (indicate ? " [Indication]" : " [Notification]"));
            } else {
                // 无 CCCD 时直接订阅下一个
                h.post(this::subscribeNext);
            }
        } catch (SecurityException e) {
            log("订阅失败 " + e.getMessage());
            h.post(this::subscribeNext);
        }
    }

    private void probe(BluetoothGatt g, UUID u) {
        BluetoothGattCharacteristic c = findChar(u);
        if (c != null && (c.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
            try { g.readCharacteristic(c); } catch (SecurityException ignored) {}
        }
    }

    private BluetoothGattCharacteristic findChar(UUID u) {
        if (gatt == null) return null;
        BluetoothGattService s = gatt.getService(serviceOf(u));
        if (s != null) {
            BluetoothGattCharacteristic c = s.getCharacteristic(u);
            if (c != null) return c;
        }
        for (BluetoothGattService sv : gatt.getServices()) {
            BluetoothGattCharacteristic c = sv.getCharacteristic(u);
            if (c != null) return c;
        }
        return null;
    }

    private UUID serviceOf(UUID u) {
        if (u.equals(INDOOR_BIKE) || u.equals(FM_CONTROL) || u.equals(FM_FEATURE)
                || u.equals(FM_STATUS) || u.equals(RES_RANGE) || u.equals(POWER_RANGE)) return FTMS_SERVICE;
        if (u.equals(POWER_MEAS)) return POWER_SERVICE;
        if (u.equals(CSC_MEAS)) return CSC_SERVICE;
        if (u.equals(HR_MEAS)) return HR_SERVICE;
        if (u.equals(NUS_TX)) return NUS_SERVICE;
        if (u.equals(ALT_CHAR) || u.equals(LED_WRITE)) return ALT_SERVICE;
        return u;
    }

    private boolean isKnownData(UUID u) {
        return u.equals(INDOOR_BIKE) || u.equals(POWER_MEAS) || u.equals(CSC_MEAS)
                || u.equals(HR_MEAS) || u.equals(NUS_TX) || u.equals(ALT_CHAR);
    }

    private boolean isKnownWrite(UUID u) {
        return u.equals(NUS_RX) || u.equals(ALT_CHAR) || u.equals(LED_WRITE)
                || u.equals(VENDOR_WRITE);
    }

    private static String shortUuid(UUID u) { return u.toString().substring(0, 8).toUpperCase(Locale.US); }
    private static int u16(byte a, byte b) { return (a & 0xFF) | ((b & 0xFF) << 8); }
    private static int s16(byte a, byte b) { return (short) u16(a, b); }
    private static int u8(byte b) { return b & 0xFF; }

    // ------------------------------------------------------------------
    // 遥测解析：只设置本帧真正出现的字段
    // ------------------------------------------------------------------

    private void parse(UUID u, byte[] v) {
        if (v == null || v.length == 0) return;
        try {
            if (u.equals(INDOOR_BIKE)) parseIndoorBike(v);
            else if (u.equals(POWER_MEAS)) parsePower(v);
            else if (u.equals(CSC_MEAS)) parseCsc(v);
            else if (u.equals(HR_MEAS)) parseHr(v);
            else parsePrivate(v);
        } catch (Exception e) { log("解析异常 " + e.getClass().getSimpleName()); }
    }

    /** FTMS 0x2AD2：字段按 Flags 位低→高排列，little-endian。只解析实际出现的字段。 */
    private void parseIndoorBike(byte[] v) {
        long now = System.currentTimeMillis();
        int flags = u16(v[0], v.length > 1 ? v[1] : 0);
        int i = 2;
        if ((flags & 0x0001) == 0 && i + 2 <= v.length) {
            speedKph = u16(v[i], v[i + 1]) * 0.01f; speedMs = now; i += 2;
        }
        if ((flags & 0x0002) != 0 && i + 2 <= v.length) i += 2;               // 平均速度
        if ((flags & 0x0004) != 0 && i + 2 <= v.length) {
            cadenceRpm = u16(v[i], v[i + 1]) * 0.5f; cadenceMs = now; i += 2;
        }
        if ((flags & 0x0008) != 0 && i + 2 <= v.length) i += 2;               // 平均踏频
        if ((flags & 0x0010) != 0 && i + 3 <= v.length) {
            distanceKm = (u8(v[i]) | (u8(v[i + 1]) << 8) | (u8(v[i + 2]) << 16)) / 1000f;
            distanceMs = now; i += 3;
        }
        if ((flags & 0x0020) != 0 && i + 2 <= v.length) {
            int r = s16(v[i], v[i + 1]);
            // Yesoul legacy 变体：0.1 分辨率；标准：分辨率 1
            res = Math.abs(r) > 200 ? Math.round(r / 10f) : r;
            resMs = now; actualRes = (int) res; i += 2;
        }
        if ((flags & 0x0040) != 0 && i + 2 <= v.length) {
            powerW = s16(v[i], v[i + 1]); powerMs = now; i += 2;
        }
        if ((flags & 0x0080) != 0 && i + 2 <= v.length) i += 2;               // 平均功率
        if ((flags & 0x0100) != 0 && i + 5 <= v.length) {
            calories = u16(v[i], v[i + 1]); caloriesMs = now; i += 5;
        }
        if ((flags & 0x0200) != 0 && i + 1 <= v.length) { hr = u8(v[i]); hrMs = now; i += 1; }
        if ((flags & 0x0400) != 0 && i + 1 <= v.length) i += 1;               // MET
        if ((flags & 0x0800) != 0 && i + 2 <= v.length) i += 2;               // 已用时间
        if ((flags & 0x1000) != 0 && i + 2 <= v.length) i += 2;               // 剩余时间
        emit("FTMS 0x2AD2");
    }

    private void parsePower(byte[] v) {
        long now = System.currentTimeMillis();
        if (v.length >= 4) { powerW = s16(v[2], v[3]); powerMs = now; }
        if (v.length >= 16 && (u16(v[0], v[1]) & 0x20) == 0) {
            int crank = u16(v[10], v[11]); int t = u16(v[12], v[13]);
            if (lastCrankMs > 0 && t != lastCrankTime) {
                int dt = (t - lastCrankTime) & 0xFFFF;
                int dr = (crank - (int) lastCrankRevs) & 0xFFFF;
                if (dt > 0 && dr > 0 && dr < 1000) {
                    cadenceRpm = dr * 60f * 1024f / dt; cadenceMs = now;
                }
            }
            lastCrankRevs = crank; lastCrankTime = t; lastCrankMs = now;
        }
        emit("功率计 0x2A63");
    }

    private void parseCsc(byte[] v) {
        if (v.length < 1) return;
        long now = System.currentTimeMillis();
        int flags = u8(v[0]);
        int i = 1;
        if ((flags & 0x01) != 0 && i + 6 <= v.length) {
            long wr = ((long) u8(v[i]) | ((long) u8(v[i + 1]) << 8) | ((long) u8(v[i + 2]) << 16)
                    | ((long) u8(v[i + 3]) << 24));
            int wt = u16(v[i + 4], v[i + 5]); i += 6;
            if (lastWheelMs > 0 && wt != lastWheelTime) {
                int dt = (wt - lastWheelTime) & 0xFFFF;
                long dr = wr - lastWheelRevs;
                if (dt > 0 && dr > 0 && dr < 100000) {
                    float revPerSec = dr * 1024f / dt;
                    speedKph = revPerSec * 2.1f * 3.6f; speedMs = now;
                    distanceKm += dr * 2.1f / 1000f; distanceMs = now;
                }
            }
            lastWheelRevs = wr; lastWheelTime = wt; lastWheelMs = now;
        }
        if ((flags & 0x02) != 0 && i + 4 <= v.length) {
            int cr = u16(v[i], v[i + 1]); int ct = u16(v[i + 2], v[i + 3]);
            if (lastCrankMs > 0 && ct != lastCrankTime) {
                int dt = (ct - lastCrankTime) & 0xFFFF;
                int dr = (cr - (int) lastCrankRevs) & 0xFFFF;
                if (dt > 0 && dr > 0 && dr < 1000) {
                    cadenceRpm = dr * 60f * 1024f / dt; cadenceMs = now;
                }
            }
            lastCrankRevs = cr; lastCrankTime = ct; lastCrankMs = now;
        }
        emit("CSC 0x2A5B");
    }

    private void parseHr(byte[] v) {
        if (v.length >= 2) { hr = u8(v[1]); hrMs = System.currentTimeMillis(); }
        emit("心率带 0x2A37");
    }

    /** 私有透传：文本 "P120 C88 R12 H130 S25" 或二进制 AA55 帧。 */
    private void parsePrivate(byte[] v) {
        String s = new String(v, java.nio.charset.Charset.forName("US-ASCII")).trim();
        nusBuffer.append(s).append('\n');
        if (nusBuffer.length() > 4096) nusBuffer.delete(0, nusBuffer.length() - 2048);
        long now = System.currentTimeMillis();
        String txt = nusBuffer.toString().toUpperCase(Locale.US);
        float p = extract(txt, 'P', -1); if (p >= 0) { powerW = p; powerMs = now; }
        float c = extract(txt, 'C', -1); if (c >= 0) { cadenceRpm = c; cadenceMs = now; }
        float r = extract(txt, 'R', -1); if (r >= 0) { res = r; resMs = now; actualRes = (int) r; }
        float hh = extract(txt, 'H', -1); if (hh >= 0) { hr = hh; hrMs = now; }
        float sp = extract(txt, 'S', -1); if (sp >= 0) { speedKph = sp; speedMs = now; }
        if (v.length >= 8 && (v[0] & 0xFF) == 0xAA && (v[1] & 0xFF) == 0x55) {
            powerW = u16(v[3], v[4]); powerMs = now;
            cadenceRpm = u8(v[5]); cadenceMs = now;
            res = u8(v[6]); resMs = now; actualRes = (int) res;
            log("解析私有二进制帧 AA55");
        }
        emit("厂商透传");
    }

    private static float extract(String txt, char key, float def) {
        int i = txt.lastIndexOf(key);
        if (i < 0) return def;
        int j = i + 1; float sign = 1;
        if (j < txt.length() && txt.charAt(j) == '-') { sign = -1; j++; }
        int st = j;
        while (j < txt.length() && ((txt.charAt(j) >= '0' && txt.charAt(j) <= '9') || txt.charAt(j) == '.')) j++;
        if (j == st) return def;
        try { return sign * Float.parseFloat(txt.substring(st, j)); }
        catch (NumberFormatException e) { return def; }
    }

    /** 只把「新鲜实测」字段放进 Sample；陈旧或未提供的字段留 -1 + xxxValid=false。 */
    private void emit(String src) {
        Sample s = Sample.measured();
        s.source = src;
        long now = System.currentTimeMillis();
        if (now - powerMs    < STALE_MS && powerW    >= 0) { s.powerW = powerW;       s.powerValid = true; }
        if (now - cadenceMs  < STALE_MS && cadenceRpm >= 0) { s.cadenceRpm = cadenceRpm; s.cadenceValid = true; }
        if (now - speedMs    < STALE_MS && speedKph  >= 0) { s.speedKph = speedKph;  s.speedValid = true; }
        if (now - hrMs       < STALE_MS && hr        >= 0) { s.heartRate = Math.round(hr); s.hrValid = true; }
        if (now - resMs      < STALE_MS && res       >= 0) { s.resistance = Math.round(res); s.resistanceValid = true; }
        if (now - distanceMs < STALE_MS) { s.distanceKm = distanceKm; s.distanceValid = true; }
        if (now - caloriesMs < STALE_MS) { s.calories = calories;   s.caloriesValid = true; }
        final Sample f = s;final long observedResAt=resMs;
        h.post(() -> { if(f.resistanceValid&&observedResAt>resistanceObservedAt){resistanceObservedAt=observedResAt;resistanceCommand.measured(f.resistance,SystemClock.elapsedRealtime());}if (listener != null) listener.onData(f); });
        log(src + " → " + fieldsSummary());
    }

    // ------------------------------------------------------------------
    // FTMS 控制点：串行写入 + RequestControl 门控
    // ------------------------------------------------------------------

    private int decodeResistanceCommand(byte[] p){return p.length>=3?Math.round(s16(p[1],p[2])/10f):p.length>=2?u8(p[1]):-1;}

    private void sendControl(byte[] payload, String what, boolean allowWithoutControl) {
        BluetoothGattCharacteristic ctrl = ctrlChar != null ? ctrlChar : findChar(FM_CONTROL);
        if (gatt == null || ctrl == null) { log("无 0x2AD9 控制点，无法下发：" + what); return; }
        if(payload.length>0&&u8(payload[0])==4)pendingControl.removeIf(p->p.length>0&&u8(p[0])==4);
        if (!initialized || !controlPointSubscribed) {
            if(payload[0]!=0)pendingControl.add(payload);
            log("等待控制通道初始化："+what);return;
        }
        if (!controlGranted && !allowWithoutControl) {
            pendingControl.add(payload);
            log("等待控制权，命令已排队：" + what);
            ensureControlLater();
            return;
        }
        if (writingControl) {if(u8(payload[0])!=0)pendingControl.add(payload); return; }
        try {
            ctrl.setValue(payload);
            ctrl.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            writingControl = true;waitingOpcode=u8(payload[0]);
            if(waitingOpcode==4){inFlightResistance=decodeResistanceCommand(payload);resistanceCommand.written(inFlightResistance,SystemClock.elapsedRealtime());}
            h.removeCallbacks(controlTimeout);h.postDelayed(controlTimeout,5000);
            boolean ok = gatt.writeCharacteristic(ctrl);
            log(what + (ok ? " 已写入" : " 写入失败"));
            if (!ok) {writingControl = false;if(waitingOpcode==4)resistanceCommand.fail(inFlightResistance,"阻力指令写入失败",true);waitingOpcode=-1;h.removeCallbacks(controlTimeout);}
        } catch (SecurityException e) {
            writingControl = false;if(waitingOpcode==4)resistanceCommand.fail(inFlightResistance,"缺少蓝牙控制权限",false);waitingOpcode=-1;h.removeCallbacks(controlTimeout);
            log(what + " 失败：" + e.getMessage());
        }
    }

    private void ensureControlLater() {
        h.postDelayed(() -> {
            if (controlGranted) { flushPendingControl(); return; }
            if (controlPointSubscribed && ctrlChar != null)
                sendControl(new byte[]{0x00}, "RequestControl(0x00)", true);
            else log("控制点未订阅完成，无法取得控制权");
        }, 400);
    }

    private void flushPendingControl() {
        if (writingControl || !controlGranted || gatt == null || ctrlChar == null) return;
        byte[] next = pendingControl.poll();
        if (next == null) return;
        try {
            ctrlChar.setValue(next);
            ctrlChar.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            writingControl = true;waitingOpcode=u8(next[0]);
            if(waitingOpcode==4){inFlightResistance=decodeResistanceCommand(next);resistanceCommand.written(inFlightResistance,SystemClock.elapsedRealtime());}
            h.removeCallbacks(controlTimeout);h.postDelayed(controlTimeout,5000);
            boolean ok = gatt.writeCharacteristic(ctrlChar);
            log("排队控制命令 op=0x" + Integer.toHexString(u8(next[0])) + (ok ? " 已写入" : " 写入失败"));
            if (!ok) {writingControl = false;if(waitingOpcode==4)resistanceCommand.fail(inFlightResistance,"阻力指令写入失败",true);waitingOpcode=-1;h.removeCallbacks(controlTimeout);}
        } catch (SecurityException e) {writingControl=false;if(waitingOpcode==4)resistanceCommand.fail(inFlightResistance,"缺少蓝牙控制权限",false);waitingOpcode=-1;h.removeCallbacks(controlTimeout);}
    }

    @Override public boolean startWorkout() {
        if (gatt == null) return false;
        sendControl(new byte[]{0x07}, "FTMS 0x07 开始/继续训练（单车屏幕进入会话）", false);
        return true;
    }
    @Override public boolean pauseWorkout() {
        if (gatt == null) return false;
        sendControl(new byte[]{0x08, 0x02}, "FTMS 0x08 暂停训练", false);
        return true;
    }
    @Override public boolean stopWorkout() {
        if (gatt == null) return false;
        sendControl(new byte[]{0x08, 0x01}, "FTMS 0x08 停止训练", false);
        return true;
    }

    @Override public WriteResult writeResistance(int level, int resistanceMax) {
        int lv = Math.round(ResistanceScale.target(level,resistanceMin,resistanceMaxSpec,resistanceInc));
        if (gatt == null || state != STATE_CONNECTED) {
            lastWriteSent = false;
            lastWriteMessage = "未连接，阻力指令未下发";
            return new WriteResult(false, false, lv, "未连接单车，无法下发阻力");
        }
        boolean supported = supportsResistanceTarget();
        boolean sent = false;
        // M2M uses verified UINT8 whole levels; standard FTMS keeps SINT16 tenths.
        if (ctrlChar != null && supported) {
            resistanceCommand.request(lv,SystemClock.elapsedRealtime());
            byte[] command=ResistanceScale.commandForDevice(name,lv,resistanceMin,resistanceMaxSpec,resistanceInc);
            sendControl(command,"FTMS 0x04 目标阻力 " + lv+" 数据="+Arrays.toString(command), false);
            sent = true;
        }
        lastWriteSent = sent;
        if (sent && supported) {
            lastWriteMessage = "已下发 FTMS 0x04 目标阻力 " + lv + "（等待单车 0x80 响应确认）";
            return new WriteResult(true, true, lv, "自动阻力：已向单车下发目标 " + lv + " 档，等待单车确认");
        }
        if (sent) {
            lastWriteMessage = "已尝试透传写入 " + lv + "，单车能力未知";
            return new WriteResult(true, false, lv,
                    "已尝试写入 " + lv + " 档，但单车未声明支持程序控阻；请以单车实际档位为准");
        }
        lastWriteMessage = "单车只读，阻力需手动旋钮";
        return new WriteResult(false, false, lv,
                "单车不支持程序控阻（只读）：请在单车旋钮上手动调到 " + lv + " 档");
    }

    @Override public boolean writeTargetPower(int watts) {
        if (gatt == null || state != STATE_CONNECTED || ctrlChar == null || !supportsPowerTarget()) return false;
        int w = Math.max(0, Math.min(watts, 1000));
        sendControl(new byte[]{0x05, (byte) (w & 0xFF), (byte) ((w >> 8) & 0xFF)},
                "FTMS 0x05 目标功率 " + w + "W", false);
        return true;
    }

    /** BLE 扫描器 */
    public static final class Scanner {
        public interface Callback {
            void onDevice(String name, String address, int rssi, byte[] scanRecord);
            void onError(String msg);
        }
        private final BluetoothLeScanner scanner;
        private final Callback c;
        private ScanCallback cb;
        public Scanner(BluetoothAdapter a, Callback c) {
            this.c = c;
            scanner = a == null ? null : a.getBluetoothLeScanner();
            cb = new ScanCallback() {
                @Override public void onScanResult(int callbackType, ScanResult r) {
                    BluetoothDevice d = r.getDevice();
                    byte[] rec = r.getScanRecord() == null ? null : r.getScanRecord().getBytes();
                    String name=d.getName()==null?"":d.getName();
                    String upper=name.toUpperCase(Locale.US);
                    List<android.os.ParcelUuid> services=r.getScanRecord()==null?null:r.getScanRecord().getServiceUuids();
                    boolean fitness=services!=null&&(services.contains(new android.os.ParcelUuid(FTMS_SERVICE))||services.contains(new android.os.ParcelUuid(POWER_SERVICE))||services.contains(new android.os.ParcelUuid(CSC_SERVICE))||services.contains(new android.os.ParcelUuid(NUS_SERVICE))||services.contains(new android.os.ParcelUuid(ALT_SERVICE)));
                    if(!fitness&&!upper.contains("YESOUL")&&!upper.contains("BIKE")&&!upper.contains("MERACH")&&!upper.contains("野小兽"))return;
                    c.onDevice(name.isEmpty()?"未命名单车":name,
                            d.getAddress(), r.getRssi(), rec);
                }
                @Override public void onScanFailed(int errorCode) { c.onError("扫描失败 " + errorCode); }
            };
        }
        public void start() {
            if (scanner == null) {c.onError("蓝牙扫描暂时不可用，请重启蓝牙后重试");return;}
            ScanSettings st = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
            List<ScanFilter> fs = Arrays.asList(
                    new ScanFilter.Builder().setServiceUuid(new android.os.ParcelUuid(FTMS_SERVICE)).build(),
                    new ScanFilter.Builder().setServiceUuid(new android.os.ParcelUuid(POWER_SERVICE)).build(),
                    new ScanFilter.Builder().setServiceUuid(new android.os.ParcelUuid(CSC_SERVICE)).build(),
                    new ScanFilter.Builder().setServiceUuid(new android.os.ParcelUuid(NUS_SERVICE)).build(),
                    new ScanFilter.Builder().setServiceUuid(new android.os.ParcelUuid(ALT_SERVICE)).build(),
                    new ScanFilter.Builder().setDeviceName("Yesoul").build(),
                    new ScanFilter.Builder().setDeviceName("YESOUL").build());
            try { scanner.startScan(null, st, cb); } catch (SecurityException e) { c.onError("缺少扫描权限"); }
        }
        public void stop() {
            if (scanner != null && cb != null) try { scanner.stopScan(cb); } catch (SecurityException ignored) {}
        }
    }
}
