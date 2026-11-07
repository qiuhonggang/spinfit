package com.spinfit.bike.ui;
import android.app.*;
import android.content.*;
import android.os.*;
import android.widget.*;
import com.spinfit.bike.app.*;
import com.spinfit.bike.core.*;
public final class ProgramDetailActivity extends Activity{
 private TrainingProgram p;private Button start;
 public static void open(Activity a,String id){Intent i=new Intent(a,ProgramDetailActivity.class);i.putExtra("id",id);a.startActivity(i);}
 @Override protected void onCreate(Bundle b){super.onCreate(b);BikeSession.get().init(this);p=ProgramLibrary.byId(getIntent().getStringExtra("id"));LinearLayout root=Ui.root(this);root.addView(Ui.topBar(this,"训练详情",this::finish));root.addView(Ui.pill(this,ProgramLibrary.level(p)+"  ·  "+ProgramLibrary.effort(p),ProgramLibrary.advanced(p)?Ui.WARN:Ui.ACCENT));Ui.gap(this,root,16);root.addView(Ui.number(this,p.name,34,Ui.TEXT));Ui.gap(this,root,10);root.addView(Ui.sub(this,p.subtitle));Ui.gap(this,root,24);
  LinearLayout profile=Ui.card(this);Visuals.Profile graph=new Visuals.Profile(this,p);profile.addView(graph,new LinearLayout.LayoutParams(-1,Ui.dp(this,100)));Ui.gap(this,profile,12);profile.addView(Ui.sub(this,p.freeRide?"自由安排 · 不限时":"0:00                         "+Ui.time(p.totalSeconds())));Ui.add(this,root,profile,24);root.addView(Ui.tv(this,"这节课，怎么骑",18,Ui.TEXT,true));Ui.gap(this,root,10);root.addView(Ui.tv(this,p.science,14,Ui.DIM,false));
  root.addView(Ui.section(this,"训练阶段",null,null));for(int i=0;i<p.segments.length;i++){Segment s=p.segments[i];LinearLayout r=Ui.row(this);TextView n=Ui.number(this,String.format(java.util.Locale.US,"%02d",i+1),16,Ui.ACCENT);r.addView(n,new LinearLayout.LayoutParams(Ui.dp(this,38),-2));LinearLayout t=Ui.col(this);t.addView(Ui.tv(this,s.name,15,Ui.TEXT,true));Ui.gap(this,t,6);t.addView(Ui.sub(this,s.hint));Ui.gap(this,t,5);t.addView(Ui.tv(this,p.freeRide?"自行调节阻力":Math.round(s.targetPowerPct)+"% FTP · 参考踏频 "+s.targetCadence+" rpm",12,Ui.DIM,false));Ui.weight(r,t);TextView d=Ui.tv(this,p.freeRide?"自由":s.durationSec/60+" 分",12,Ui.DIM,true);r.addView(d);Ui.add(this,root,r,22);}
  root.addView(Ui.sub(this,"阻力按分段设置参考档位，并非功率闭环控制。请以实际体感调节；不同单车的阻力刻度并不相同。"));Ui.gap(this,root,12);Button source=Ui.button(this,"训练原则与资料来源",Ui.CARD,Ui.DIM);source.setOnClickListener(v->startActivity(new Intent(this,GuideActivity.class)));root.addView(source);
  LinearLayout foot=Ui.root(this);foot.setPadding(Ui.dp(this,24),Ui.dp(this,12),Ui.dp(this,24),Ui.dp(this,12));start=Ui.primary(this,"开始训练");start.setOnClickListener(v->begin());foot.addView(start);Ui.screen(this,root,foot);
 }
 @Override protected void onResume(){super.onResume();BikeSession s=BikeSession.get();start.setText(s.active()?"返回正在进行的训练":!s.isConnected()?"连接单车后开始":s.isDemo()?"开始演示训练":"开始训练  →");}
 private void begin(){BikeSession s=BikeSession.get();if(s.active()){startActivity(new Intent(this,RideActivity.class));return;}if(!s.isConnected()){startActivity(new Intent(this,ScanActivity.class));return;}if(ProgramLibrary.advanced(p)){new AlertDialog.Builder(this).setTitle("准备好进阶训练了吗？").setMessage("适合已有骑行基础的人。目标功率依赖准确的个人 FTP；如感觉吃力，可随时降低阻力。建议两次较高强度训练至少隔一天。").setNegativeButton("再看看",null).setPositiveButton("开始训练",(d,w)->launch()).show();}else launch();}
 private void launch(){try{BikeSession.get().startProgram(p);startActivity(new Intent(this,RideActivity.class));}catch(RuntimeException e){Ui.dialog(this,"暂时无法开始","请检查手机剩余空间和蓝牙连接后重试。");}}
}
