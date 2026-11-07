package com.spinfit.bike.tests;
import android.app.*;import android.os.*;import android.bluetooth.*;import android.bluetooth.le.*;import android.content.*;import android.database.*;import android.database.sqlite.*;import java.io.*;import java.nio.channels.*;import java.util.*;
public class DeviceAudit extends Instrumentation {
 private final Set<String> found=new LinkedHashSet<>();private int seen;private BluetoothLeScanner scanner;private Bundle result=new Bundle();private boolean readOnly;
 @Override public void onCreate(Bundle b){super.onCreate(b);readOnly=b!=null&&Boolean.parseBoolean(b.getString("readOnly","false"));start();}
 @Override public void onStart(){
  try{File db=getTargetContext().getDatabasePath("spinfit_workouts.db");SQLiteDatabase d=SQLiteDatabase.openDatabase(db.getPath(),null,SQLiteDatabase.OPEN_READWRITE);try(Cursor c=d.rawQuery("PRAGMA wal_checkpoint(FULL)",null)){c.moveToFirst();}try(Cursor c=d.rawQuery("SELECT COUNT(*), SUM(CASE WHEN demo=0 THEN 1 ELSE 0 END) FROM rides",null)){c.moveToFirst();result.putString("records",c.getInt(0)+" total / "+c.getInt(1)+" real");}d.close();File target=new File(getTargetContext().getExternalFilesDir("validation"),"before-heart-update.db");try(FileChannel in=new FileInputStream(db).getChannel();FileChannel out=new FileOutputStream(target).getChannel()){out.transferFrom(in,0,in.size());}result.putString("snapshot",target.getPath());
   if(readOnly){
    SQLiteDatabase checked=SQLiteDatabase.openDatabase(db.getPath(),null,SQLiteDatabase.OPEN_READONLY);
    try(Cursor c=checked.rawQuery("SELECT COUNT(*),COUNT(speed),COUNT(distance),SUM(CASE WHEN hr_source='AW-HR-WATCH' THEN 1 ELSE 0 END) FROM points",null)){c.moveToFirst();result.putString("saved_samples","all="+c.getInt(0)+", speed="+c.getInt(1)+", distance="+c.getInt(2)+", watchHR="+c.getInt(3));}
    try(Cursor c=checked.rawQuery("SELECT avg_speed,max_speed,distance,seconds FROM rides WHERE id=4",null)){if(c.moveToFirst())result.putString("original_real_ride","avg_speed="+c.getFloat(0)+", max_speed="+c.getFloat(1)+", distance="+c.getFloat(2)+", seconds="+c.getInt(3));}
    checked.close();finish(-1,result);return;
   }
   runOnMainSync(()->{BluetoothManager m=(BluetoothManager)getTargetContext().getSystemService(Context.BLUETOOTH_SERVICE);scanner=m.getAdapter().getBluetoothLeScanner();scanner.startScan(null,new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),callback);});
   Thread.sleep(15000);runOnMainSync(()->scanner.stopScan(callback));result.putString("nearby",seen+" advertisement callbacks");result.putString("heart_candidates",found.toString());finish(-1,result);
  }catch(Throwable e){result.putString("error",e.toString());finish(0,result);}
 }
 private final ScanCallback callback=new ScanCallback(){public void onScanResult(int t,ScanResult r){seen++;ScanRecord s=r.getScanRecord();String name=s==null?null:s.getDeviceName();if(name==null)name=r.getDevice().getName();String n=name==null?"":name,up=n.toUpperCase(Locale.ROOT);List<ParcelUuid> uuids=s==null?null:s.getServiceUuids();boolean hr=uuids!=null&&uuids.toString().contains("0000180d");if(hr||up.contains("HEART")||up.contains("心率")||up.contains("WATCH")||up.contains("IPHONE")||up.contains("ECHO"))found.add(n+" | "+r.getDevice().getAddress()+" | "+uuids);}};
}
