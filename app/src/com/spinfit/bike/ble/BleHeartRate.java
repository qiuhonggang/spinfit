package com.spinfit.bike.ble;

import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.Context;
import android.os.*;
import com.spinfit.bike.core.HeartRatePacket;
import java.util.*;

/** Independent BLE central: never replaces or writes to the bike connection. */
public final class BleHeartRate {
    public static final UUID SERVICE=UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb");
    public static final UUID MEASUREMENT=UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb");
    private static final UUID CCCD=UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    public interface Listener {void changed();}
    private final Context context;private final String address,name;private final Listener listener;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private BluetoothGatt gatt;private boolean connected,closed;private int bpm=-1;private long updated;
    private String status="未连接";
    public BleHeartRate(Context c,String a,String n,Listener l){context=c.getApplicationContext();address=a;name=n;listener=l;}
    public String name(){return name;}public String address(){return address;}public boolean connected(){return connected;}
    public int bpm(){return connected&&SystemClock.elapsedRealtime()-updated<10000?bpm:-1;}
    public String status(){return connected?(bpm()>0?"心率正在同步":"已连接，等待心率更新"):status;}
    private void changed(){if(listener!=null)listener.changed();}
    private final Runnable timeout=()->fail("连接超时，请保持心率广播开启后重试");
    public void connect(){
        status="正在连接…";changed();handler.postDelayed(timeout,15000);
        try{
            BluetoothManager manager=(BluetoothManager)context.getSystemService(Context.BLUETOOTH_SERVICE);
            BluetoothAdapter adapter=manager==null?null:manager.getAdapter();
            if(adapter==null||!adapter.isEnabled()){fail("请先开启手机蓝牙");return;}
            gatt=adapter.getRemoteDevice(address).connectGatt(context,false,callback,BluetoothDevice.TRANSPORT_LE);
            if(gatt==null)fail("无法创建蓝牙连接，请重试");
        }catch(SecurityException e){fail("请允许蓝牙连接权限");}catch(IllegalArgumentException e){fail("设备地址已变化，请重新搜索");}
    }
    public void disconnect(){closed=true;connected=false;bpm=-1;handler.removeCallbacksAndMessages(null);BluetoothGatt old=gatt;gatt=null;if(old!=null){try{old.disconnect();old.close();}catch(SecurityException ignored){}}status="已断开";changed();}
    private void fail(String message){disconnect();status=message;changed();}
    private void accept(BluetoothGatt g,byte[] value){final byte[] data=value==null?null:value.clone();handler.post(()->{if(closed||g!=gatt)return;bpm=HeartRatePacket.decode(data);updated=SystemClock.elapsedRealtime();changed();});}
    private final BluetoothGattCallback callback=new BluetoothGattCallback(){
        @Override public void onConnectionStateChange(BluetoothGatt g,int result,int state){handler.post(()->{
            if(closed||g!=gatt)return;
            if(result!=BluetoothGatt.GATT_SUCCESS){fail("连接失败（"+result+"），请重新开启广播后重试");return;}
            if(state==BluetoothProfile.STATE_CONNECTED){status="正在识别心率服务…";changed();try{if(!g.discoverServices())fail("无法读取心率服务，请重试");}catch(SecurityException e){fail("缺少蓝牙权限");}}
            else if(state==BluetoothProfile.STATE_DISCONNECTED)fail("心率设备已断开，可重新连接");
        });}
        @Override public void onServicesDiscovered(BluetoothGatt g,int result){handler.post(()->{
            if(closed||g!=gatt)return;
            if(result!=0){fail("无法读取心率服务，请重试");return;}
            BluetoothGattService service=g.getService(SERVICE);BluetoothGattCharacteristic measurement=service==null?null:service.getCharacteristic(MEASUREMENT);
            if(measurement==null){fail("此设备未提供标准心率广播，请在广播软件中启用蓝牙广播");return;}
            BluetoothGattDescriptor descriptor=measurement.getDescriptor(CCCD);
            if(descriptor==null){fail("设备未提供心率通知通道");return;}
            try{
                if(!g.setCharacteristicNotification(measurement,true)){fail("无法开启心率通知");return;}
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                if(!g.writeDescriptor(descriptor))fail("心率订阅失败，请重试");
            }catch(SecurityException e){fail("请允许蓝牙连接权限");}
        });}
        @Override public void onDescriptorWrite(BluetoothGatt g,BluetoothGattDescriptor d,int result){handler.post(()->{
            if(closed||g!=gatt)return;
            if(result!=0){fail("心率订阅失败（"+result+"）");return;}
            handler.removeCallbacks(timeout);connected=true;status="已连接，等待心率更新";changed();
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
