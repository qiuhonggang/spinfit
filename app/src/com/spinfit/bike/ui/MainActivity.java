package com.spinfit.bike.ui;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import com.spinfit.bike.app.*;
import com.spinfit.bike.ble.*;
import com.spinfit.bike.core.*;
import java.util.*;
public final class MainActivity extends Activity implements BikeSession.StateListener{
 private int tab=0;private BikeSession session;private TextView device,deviceSub;private final Handler h=new Handler();
 private final Runnable refresh=new Runnable(){public void run(){updateDevice();h.postDelayed(this,1500);}};
 @Override protected void onCreate(Bundle b){super.onCreate(b);session=BikeSession.get();session.init(this);tab=b!=null?b.getInt("tab",0):getIntent().getIntExtra("tab",0);session.addStateListener(this);}
 @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);tab=i.getIntExtra("tab",0);render();}
 @Override protected void onResume(){super.onResume();render();h.post(refresh);}
 @Override protected void onPause(){h.removeCallbacks(refresh);super.onPause();}
 @Override protected void onSaveInstanceState(Bundle b){b.putInt("tab",tab);super.onSaveInstanceState(b);}
 private void render(){device=null;LinearLayout root=Ui.root(this);if(tab==0)home(root);else if(tab==1)ProgramsActivity.populate(this,root);else HistoryActivity.populate(this,root,false);Ui.screen(this,root,Ui.nav(this,tab,n->{tab=n;render();}));}
 private void home(LinearLayout root){
  LinearLayout header=Ui.row(this);LinearLayout text=Ui.col(this);text.addView(Ui.eyebrow(this,"SPINFIT  /  RIDE YOUR RHYTHM"));Ui.gap(this,text,8);text.addView(Ui.title(this,"今天，也向前一点。"));Ui.weight(header,text);header.addView(Ui.iconButton(this,"settings","训练设置",()->startActivity(new Intent(this,SettingsActivity.class))),new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48)));Ui.add(this,root,header,18);
  LinearLayout dc=Ui.row(this);Ui.touch(dc,Ui.CARD,18);int pad=Ui.dp(this,16);dc.setPadding(pad,pad,pad,pad);dc.addView(new Visuals.Icon(this,"bluetooth",Ui.ACCENT),new LinearLayout.LayoutParams(Ui.dp(this,26),Ui.dp(this,26)));LinearLayout dt=Ui.col(this);dt.setPadding(Ui.dp(this,14),0,Ui.dp(this,8),0);device=Ui.tv(this,"连接你的单车",15,Ui.TEXT,true);deviceSub=Ui.sub(this,"连接后，即可同步骑行数据");dt.addView(device);Ui.gap(this,dt,5);dt.addView(deviceSub);Ui.weight(dc,dt);dc.addView(new Visuals.Icon(this,"arrow",Ui.DIM),new LinearLayout.LayoutParams(Ui.dp(this,22),Ui.dp(this,22)));dc.setOnClickListener(v->startActivity(new Intent(this,ScanActivity.class)));Ui.add(this,root,dc,20);updateDevice();
  android.widget.FrameLayout hero=new android.widget.FrameLayout(this);Ui.surface(hero,Ui.ACCENT,26);hero.setClipToOutline(true);hero.addView(new Visuals.Hero(this),new android.widget.FrameLayout.LayoutParams(-1,-1));LinearLayout ht=Ui.col(this);ht.setPadding(Ui.dp(this,24),Ui.dp(this,24),Ui.dp(this,24),Ui.dp(this,24));ht.addView(Ui.tv(this,"MAKE EVERY RIDE COUNT",10,Ui.INK,true));Ui.gap(this,ht,15);ht.addView(Ui.number(this,"跟着节奏\n自在开骑",31,Ui.INK));Ui.gap(this,ht,12);ht.addView(Ui.tv(this,"无需赶路，只需向前。",13,Ui.INK,false));Ui.gap(this,ht,20);Button free=Ui.button(this,session.active()?"继续我的训练  →":"自由骑行  →",Ui.INK,Ui.TEXT);free.setOnClickListener(v->{if(session.active())startActivity(new Intent(this,RideActivity.class));else ProgramDetailActivity.open(this,"free");});ht.addView(free,new LinearLayout.LayoutParams(Ui.dp(this,158),-2));hero.addView(ht);Ui.add(this,root,hero,0);
  root.addView(Ui.section(this,"本周积累","查看记录",()->{tab=2;render();}));
  List<WorkoutStore.Ride> rides=session.store().list(false);Calendar cal=Calendar.getInstance();cal.setFirstDayOfWeek(Calendar.MONDAY);cal.set(Calendar.DAY_OF_WEEK,Calendar.MONDAY);cal.set(Calendar.HOUR_OF_DAY,0);cal.set(Calendar.MINUTE,0);cal.set(Calendar.SECOND,0);cal.set(Calendar.MILLISECOND,0);long monday=cal.getTimeInMillis();int secs=0,count=0;float distance=0;boolean has=false;for(WorkoutStore.Ride r:rides)if(r.started>=monday){secs+=r.seconds;count++;if(r.distance>=0){distance+=r.distance;has=true;}}
  LinearLayout stats=Ui.row(this);Ui.weight(stats,Ui.metric(this,"骑行时长",""+(secs/60),"分钟"));Ui.weight(stats,Ui.metric(this,"训练次数",""+count,"次"));Ui.weight(stats,Ui.metric(this,"累计里程",has?Ui.val(distance,1):"—","km"));root.addView(stats);Ui.gap(this,root,8);root.addView(Ui.sub(this,"只统计真实骑行，演示训练单独保存"));
  root.addView(Ui.section(this,"为你精选","全部计划",()->{tab=1;render();}));ProgramsActivity.programCard(this,root,ProgramLibrary.byId("endurance"));
  LinearLayout note=Ui.card(this);note.addView(Ui.tv(this,"先建立习惯，再增加强度。",16,Ui.TEXT,true));Ui.gap(this,note,8);note.addView(Ui.sub(this,"从每周 3 次轻松骑行开始，给恢复留出时间。"));root.addView(note);
 }
 private void updateDevice(){if(device==null)return;if(session.active()){device.setText(session.engine().isPaused()?"训练已暂停":"训练正在进行");deviceSub.setText("返回骑行页面可继续操作");}else{device.setText(session.isConnected()?session.deviceName():"连接你的单车");deviceSub.setText(session.isConnected()?(session.isDemo()?"演示模式 · 模拟数据":"已连接 · "+(session.fresh()?"数据正在同步":"等待踩踏唤醒")):"连接后，即可同步骑行数据");}}
 @Override public void onState(int s,String m){if(s<0)return;runOnUiThread(this::updateDevice);}
 @Override protected void onDestroy(){session.removeStateListener(this);h.removeCallbacksAndMessages(null);super.onDestroy();}
}
