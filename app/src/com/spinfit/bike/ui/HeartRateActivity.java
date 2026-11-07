package com.spinfit.bike.ui;
import android.Manifest;
import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.widget.*;
import com.spinfit.bike.app.*;
import com.spinfit.bike.ble.*;
import java.util.*;

/** Heart-rate sensor pairing remains independent of the bike, including during a ride. */
public final class HeartRateActivity extends Activity {
    private final Handler h=new Handler(Looper.getMainLooper());private BikeSession session;
    private BleHeartRate.Scanner scanner;private boolean scanning;private final Set<String> seen=new HashSet<>();
    private TextView value,source,status,empty;private Button scan,disconnect,reconnect;private LinearLayout devices;
    private final Runnable stop=()->stopScan();
    private final Runnable refresh=new Runnable(){public void run(){render();h.postDelayed(this,700);}};
    @Override protected void onCreate(Bundle state){super.onCreate(state);session=BikeSession.get();session.init(this);
        LinearLayout root=Ui.root(this);root.addView(Ui.topBar(this,"心率设备",this::finish));root.addView(Ui.eyebrow(this,"HEART RATE / YOUR OWN RHYTHM"));root.addView(Ui.title(this,"每一次心跳，都同步。"));
        root.addView(Ui.sub(this,"连接 Apple Watch「心率广播」或蓝牙心率带。\n心率与单车独立连接，骑行中也能添加。"));Ui.gap(this,root,24);
        LinearLayout card=Ui.card(this);source=Ui.tv(this,"尚未连接外部心率",16,Ui.TEXT,true);card.addView(source);Ui.gap(this,card,16);LinearLayout number=Ui.row(this);value=Ui.number(this,"—",66,Ui.PINK);number.addView(value);number.addView(Ui.sub(this,"  bpm"));card.addView(number);Ui.gap(this,card,12);status=Ui.sub(this,"请保持广播软件运行，并佩戴好手表");card.addView(status);Ui.gap(this,card,20);
        scan=Ui.primary(this,"搜索心率设备");scan.setOnClickListener(v->{if(scanning)stopScan();else ensureScan();});card.addView(scan);
        Settings prefs=session.settings();if(!prefs.heartAddress().isEmpty()){Ui.gap(this,card,12);reconnect=Ui.button(this,"重连 "+prefs.heartName(),Ui.CARD2,Ui.TEXT);reconnect.setOnClickListener(v->{if(permission())connect(prefs.heartName(),prefs.heartAddress());else ensureScan();});card.addView(reconnect);}
        Ui.gap(this,card,10);disconnect=Ui.button(this,"断开心率设备",Ui.CARD2,Ui.TEXT);disconnect.setOnClickListener(v->{session.disconnectHeart();render();});card.addView(disconnect);root.addView(card);
        root.addView(Ui.section(this,"附近的心率设备",null,null));devices=Ui.col(this);empty=Ui.sub(this,"搜索到的心率广播会显示在这里");devices.addView(empty);root.addView(devices);Ui.gap(this,root,24);
        LinearLayout help=Ui.card(this);help.addView(Ui.tv(this,"连接提示",16,Ui.TEXT,true));Ui.gap(this,help,10);help.addView(Ui.tv(this,"先在手表中开始心率广播。若搜索不到，请同时打开 iPhone 上的「心率广播」并保持前台，确认已显示手表心率。\n\n外部设备数据优先；信号中断时不沿用过期心率，单车有有效心率时可继续使用单车数据。",13,Ui.DIM,false));root.addView(help);
        Ui.gap(this,root,20);Button done=Ui.primary(this,"完成，返回骑行");done.setOnClickListener(v->finish());root.addView(done);Ui.screen(this,root,null);render();
    }
    private boolean permission(){if(Build.VERSION.SDK_INT>=31)return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)==PackageManager.PERMISSION_GRANTED&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED;return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
    private void ensureScan(){if(!permission()){requestPermissions(Build.VERSION.SDK_INT>=31?new String[]{Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT}:new String[]{Manifest.permission.ACCESS_FINE_LOCATION},31);return;}BluetoothManager m=(BluetoothManager)getSystemService(BLUETOOTH_SERVICE);BluetoothAdapter a=m==null?null:m.getAdapter();if(a==null||!a.isEnabled()){Ui.dialog(this,"请先开启蓝牙","打开手机蓝牙后再搜索心率设备。");return;}stopScan();seen.clear();devices.removeAllViews();empty=Ui.sub(this,"正在搜索，请保持心率广播开启…");devices.addView(empty);scanning=true;scan.setText("停止搜索");scanner=new BleHeartRate.Scanner(a,new BleHeartRate.Scanner.Listener(){public void found(String n,String address,int rssi){runOnUiThread(()->add(n,address,rssi));}public void error(String message){runOnUiThread(()->{stopScan();empty.setText(message);});}});scanner.start();h.postDelayed(stop,15000);}
    private void add(String name,String address,int rssi){if(!scanning||!seen.add(address))return;devices.removeView(empty);LinearLayout card=Ui.card(this);LinearLayout row=Ui.row(this),text=Ui.col(this);text.addView(Ui.tv(this,name,17,Ui.TEXT,true));Ui.gap(this,text,8);text.addView(Ui.sub(this,rssi>-65?"信号良好":"靠近手机可改善信号"));Ui.weight(row,text);Button b=Ui.button(this,"连接",Ui.ACCENT,Ui.INK);b.setOnClickListener(v->connect(name,address));row.addView(b);card.addView(row);Ui.add(this,devices,card,12);}
    private void connect(String name,String address){stopScan();session.connectHeart(this,address,name);render();}
    private void stopScan(){h.removeCallbacks(stop);if(scanner!=null)scanner.stop();scanner=null;if(scanning){scanning=false;scan.setText("重新搜索");if(seen.isEmpty())empty.setText("暂未找到心率广播，请检查手表和 iPhone 的广播状态后重试");}}
    private void render(){if(source==null)return;BleHeartRate heart=session.heart();source.setText(heart==null?"尚未连接外部心率":heart.name());value.setText(heart!=null&&heart.bpm()>0?""+heart.bpm():"—");value.setTextColor(Ui.heartRateColor(heart==null?-1:heart.bpm()));status.setText(heart==null?"请保持广播软件运行，并佩戴好手表":heart.status());if(reconnect!=null){reconnect.setEnabled(heart==null||!heart.busy());reconnect.setText(heart!=null&&heart.busy()?(heart.connected()?"心率设备已连接":"正在连接，请稍候…"):"重连 "+session.settings().heartName());}disconnect.setVisibility(heart==null?android.view.View.GONE:android.view.View.VISIBLE);}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(permission())ensureScan();else Ui.dialog(this,"需要蓝牙权限","请在系统设置中允许蓝牙扫描与连接。");}
    @Override protected void onResume(){super.onResume();h.post(refresh);}
    @Override protected void onPause(){h.removeCallbacks(refresh);super.onPause();}
    @Override protected void onDestroy(){stopScan();h.removeCallbacksAndMessages(null);super.onDestroy();}
}
