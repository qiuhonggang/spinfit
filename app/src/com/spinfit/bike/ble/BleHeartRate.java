package com.spinfit.bike.ble;

import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.Context;
import android.os.*;
import android.util.Log;
import com.spinfit.bike.core.HeartRatePacket;
import com.spinfit.bike.core.HeartReconnectPolicy;
import java.util.*;

/** Independent BLE central. All state transitions run on the main handler. */
public final class BleHeartRate {
    public static final UUID SERVICE=UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb");
    public static final UUID MEASUREMENT=UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb");
    private static final UUID CCCD=UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    public interface Listener {void changed();}
    private final Context context;private String address;private final String name;private final Listener listener;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final HeartReconnectPolicy retries=new HeartReconnectPolicy();
    private final Set<BluetoothGatt> retiring=new HashSet<>();
    private BluetoothGatt gatt;private BluetoothLeScanner scanner;private ScanCallback scanCallback;
    private boolean connected,closed=true,working;private int bpm=-1;private long updated,lastPacket,subscribedAt;
    private static long connectAfter; // allow a previous sensor owner to finish disconnecting
    private String status="未连接",loggedStatus="";
    private Runnable scanTimeout,stageTimeout,retryTask,watchdog,pendingConnect;
    public BleHeartRate(Context c,String a,String n,Listener l){context=c.getApplicationContext();address=a;name=n;listener=l;}
    public String name(){return name;}public String address(){return address;}public boolean connected(){return connected;}
    public boolean busy(){return working||connected;}
    public boolean samePeer(String a,String n){return address.equalsIgnoreCase(a)||(name.equals(n)&&!"蓝牙心率设备".equals(n));}
    public int bpm(){return connected&&SystemClock.elapsedRealtime()-updated<10000?bpm:-1;}
    public String status(){return connected?(bpm()>0?"心率正在同步":"已连接，等待心率更新"):status;}
    private void changed(){if(!status.equals(loggedStatus)){Log.d("SpinFitHR",status+" connected="+connected);loggedStatus=status;}if(listener!=null)listener.changed();}
    private void remove(Runnable r){if(r!=null)handler.removeCallbacks(r);}
    private void stopScan(){remove(scanTimeout);if(scanner!=null&&scanCallback!=null)try{scanner.stopScan(scanCallback);}catch(SecurityException ignored){}scanner=null;scanCallback=null;}
    private void cancelWork(){stopScan();remove(stageTimeout);remove(retryTask);remove(watchdog);remove(pendingConnect);}
    private void closeRetired(BluetoothGatt old){if(retiring.remove(old))try{old.close();}catch(SecurityException ignored){}}
    private void retireGatt(){
        BluetoothGatt old=gatt;gatt=null;connected=false;bpm=-1;
        if(old==null)return;
        retiring.add(old);connectAfter=SystemClock.elapsedRealtime()+1200;
        try{old.disconnect();}catch(SecurityException ignored){}
        // Closing immediately after disconnect can cancel teardown before the peer sees it.
        handler.postDelayed(()->closeRetired(old),1100);
    }
    public void connect(){if(busy())return;closed=false;working=true;retries.reset();discover();}
    public void disconnect(){closed=true;working=false;cancelWork();retireGatt();status="已断开";changed();}
    private void fail(String message,boolean retryable){fail(message,retryable,0);}
    private void fail(String message,boolean retryable,long minimumDelay){
        if(closed)return;cancelWork();retireGatt();
        long delay=retryable?retries.retryDelay():-1;
        if(delay<0){working=false;status=message+"；请确认广播开启后点重连";changed();return;}
        working=true;status=message+"，正在自动重连…";changed();
        retryTask=()->{if(!closed)discover();};handler.postDelayed(retryTask,Math.max(delay,minimumDelay));
    }
    private void deadline(long delay,String message){remove(stageTimeout);stageTimeout=()->fail(message,true);handler.postDelayed(stageTimeout,delay);}
    private void discover(){
        if(closed)return;working=true;status="正在重新发现心率广播…";changed();
        try{
            BluetoothManager manager=(BluetoothManager)context.getSystemService(Context.BLUETOOTH_SERVICE);
            BluetoothAdapter adapter=manager==null?null:manager.getAdapter();
            if(adapter==null||!adapter.isEnabled()){fail("请先开启手机蓝牙",false);return;}
            scanner=adapter.getBluetoothLeScanner();if(scanner==null){fail("蓝牙扫描不可用",false);return;}
            scanCallback=new ScanCallback(){
                @Override public void onScanResult(int type,ScanResult result){handler.post(()->{
                    if(closed||scanCallback!=this)return;
                    try{
                        ScanRecord record=result.getScanRecord();String found=record==null?null:record.getDeviceName();if(found==null)found=result.getDevice().getName();
                        List<ParcelUuid> uuids=record==null?null:record.getServiceUuids();boolean hr=uuids!=null&&uuids.contains(new ParcelUuid(SERVICE));
                        if(!HeartReconnectPolicy.matches(address,name,result.getDevice().getAddress(),found,hr))return;
                        BluetoothDevice device=result.getDevice();address=device.getAddress();stopScan();
                        status="已发现广播，正在连接…";changed();
                        pendingConnect=()->open(device);handler.postDelayed(pendingConnect,Math.max(200,connectAfter-SystemClock.elapsedRealtime()));
                    }catch(SecurityException e){fail("请允许蓝牙扫描与连接权限",false);}
                });}
                @Override public void onScanFailed(int code){handler.post(()->{if(scanCallback==this)fail(code==6?"扫描频繁，等待蓝牙恢复":"扫描失败（"+code+"）",true,code==6?31000:0);});}
            };
            scanner.startScan(null,new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),scanCallback);
            scanTimeout=()->fail("未发现所选设备的心率广播",true);handler.postDelayed(scanTimeout,10000);
        }catch(SecurityException e){fail("请允许蓝牙扫描与连接权限",false);}
    }
    private void open(BluetoothDevice device){
        if(closed)return;
        try{gatt=device.connectGatt(context,false,callback,BluetoothDevice.TRANSPORT_LE);if(gatt==null)fail("无法创建心率连接",true);else deadline(12000,"连接心率设备超时");}
        catch(SecurityException e){fail("请允许蓝牙连接权限",false);}
    }
    private void accept(BluetoothGatt g,byte[] value){final byte[] data=value==null?null:value.clone();handler.post(()->{
        if(closed||g!=gatt||!connected)return;
        lastPacket=SystemClock.elapsedRealtime();bpm=HeartRatePacket.decode(data);updated=lastPacket;
        // Reset the outage budget after a sustained connection, not one stray notification.
        if(lastPacket-subscribedAt>10000)retries.reset();
        status=bpm>0?"心率正在同步":"已连接，请佩戴好手表";changed();
    });}
    private void watch(){
        watchdog=()->{if(closed||!connected)return;if(SystemClock.elapsedRealtime()-lastPacket>20000){fail("心率广播中断",true);return;}watch();};handler.postDelayed(watchdog,5000);
    }
    private final BluetoothGattCallback callback=new BluetoothGattCallback(){
        @Override public void onConnectionStateChange(BluetoothGatt g,int result,int state){handler.post(()->{
            if(retiring.contains(g)){if(state==BluetoothProfile.STATE_DISCONNECTED)closeRetired(g);return;}
            if(closed||g!=gatt)return;
            if(result!=BluetoothGatt.GATT_SUCCESS){fail("心率连接失败（"+result+"）",true);return;}
            if(state==BluetoothProfile.STATE_CONNECTED){status="正在识别心率服务…";changed();deadline(10000,"读取心率服务超时");try{if(!g.discoverServices())fail("无法读取心率服务",true);}catch(SecurityException e){fail("缺少蓝牙权限",false);}}
            else if(state==BluetoothProfile.STATE_DISCONNECTED)fail("心率设备已断开",true);
        });}
        @Override public void onServicesDiscovered(BluetoothGatt g,int result){handler.post(()->{
            if(closed||g!=gatt)return;
            if(result!=0){fail("无法读取心率服务",true);return;}
            BluetoothGattService service=g.getService(SERVICE);BluetoothGattCharacteristic measurement=service==null?null:service.getCharacteristic(MEASUREMENT);
            if(measurement==null){fail("此设备未提供标准心率服务，请选择手表的蓝牙广播",false);return;}
            BluetoothGattDescriptor descriptor=measurement.getDescriptor(CCCD);
            if(descriptor==null){fail("设备未提供心率通知通道",false);return;}
            try{
                if(!g.setCharacteristicNotification(measurement,true)){fail("无法开启心率通知",true);return;}
                byte[] mode=(measurement.getProperties()&BluetoothGattCharacteristic.PROPERTY_NOTIFY)!=0?BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE:BluetoothGattDescriptor.ENABLE_INDICATION_VALUE;
                status="正在订阅心率…";changed();deadline(10000,"心率订阅超时");
                if(Build.VERSION.SDK_INT>=33){if(g.writeDescriptor(descriptor,mode)!=BluetoothStatusCodes.SUCCESS)fail("心率订阅失败",true);}
                else{descriptor.setValue(mode);if(!g.writeDescriptor(descriptor))fail("心率订阅失败",true);}
            }catch(SecurityException e){fail("请允许蓝牙连接权限",false);}
        });}
        @Override public void onDescriptorWrite(BluetoothGatt g,BluetoothGattDescriptor d,int result){handler.post(()->{
            if(closed||g!=gatt||!CCCD.equals(d.getUuid()))return;
            if(result!=0){fail("心率订阅失败（"+result+"）",true);return;}
            remove(stageTimeout);connected=true;working=false;subscribedAt=lastPacket=SystemClock.elapsedRealtime();status="已连接，等待心率更新";changed();watch();
        });}
        @Override public void onCharacteristicChanged(BluetoothGatt g,BluetoothGattCharacteristic c){if(c.getUuid().equals(MEASUREMENT))accept(g,c.getValue());}
        @Override public void onCharacteristicChanged(BluetoothGatt g,BluetoothGattCharacteristic c,byte[] value){if(c.getUuid().equals(MEASUREMENT))accept(g,value);}
    };
    public static final class Scanner {
        public interface Listener{void found(String name,String address,int rssi);void error(String message);}
        private final BluetoothLeScanner scanner;private final Listener listener;
        private final ScanCallback callback=new ScanCallback(){
            @Override public void onScanResult(int type,ScanResult r){
                try{
                    ScanRecord record=r.getScanRecord();String name=record==null?null:record.getDeviceName();if(name==null)name=r.getDevice().getName();
                    String upper=name==null?"":name.toUpperCase(Locale.ROOT);
                    List<ParcelUuid> uuids=record==null?null:record.getServiceUuids();boolean heart=uuids!=null&&uuids.contains(new ParcelUuid(SERVICE));
                    if(!heart&&!upper.contains("HEART")&&!upper.contains("心率")&&!upper.contains("ECHO")&&!upper.contains("HRM")&&!upper.contains("POLAR")&&!upper.contains("IPHONE")&&!upper.contains("WATCH"))return;
                    listener.found(name==null?"蓝牙心率设备":name,r.getDevice().getAddress(),r.getRssi());
                }catch(SecurityException e){listener.error("请允许蓝牙扫描权限");}
            }
            @Override public void onScanFailed(int code){listener.error("扫描失败（"+code+"），请稍后重试");}
        };
        public Scanner(BluetoothAdapter a,Listener l){scanner=a==null?null:a.getBluetoothLeScanner();listener=l;}
        public void start(){if(scanner==null){listener.error("蓝牙暂时不可用，请先开启蓝牙");return;}try{scanner.startScan(null,new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),callback);}catch(SecurityException e){listener.error("请允许蓝牙扫描权限");}}
        public void stop(){if(scanner!=null)try{scanner.stopScan(callback);}catch(SecurityException ignored){}}
    }
}
