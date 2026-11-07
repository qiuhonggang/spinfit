package com.spinfit.bike.ui;
import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import com.spinfit.bike.app.*;
import com.spinfit.bike.core.*;
public final class ProgramsActivity extends Activity{
 @Override protected void onCreate(Bundle b){super.onCreate(b);BikeSession.get().init(this);LinearLayout r=Ui.root(this);populate(this,r);Ui.screen(this,r,Ui.nav(this,1,n->Ui.goTab(this,n)));}
 public static void populate(Activity a,LinearLayout root){
  root.addView(Ui.eyebrow(a,"TRAINING COLLECTION"));root.addView(Ui.title(a,"找到你的骑行节奏"));root.addView(Ui.sub(a,"8 套训练 · 从第一公里到更从容的自己"));Ui.gap(a,root,24);
  LinearLayout guide=Ui.card(a);Ui.surface(guide,0xFF293824,22);guide.addView(Ui.pill(a,"4 周入门路线",Ui.ACCENT));Ui.gap(a,guide,14);guide.addView(Ui.number(a,"把骑行，变成日常。",23,Ui.TEXT));Ui.gap(a,guide,8);guide.addView(Ui.sub(a,"每周 3 次，由 20 分钟逐步增加时长"));Ui.gap(a,guide,14);Button more=Ui.button(a,"查看四周安排  →",Ui.CARD2,Ui.TEXT);more.setOnClickListener(v->a.startActivity(new Intent(a,GuideActivity.class)));guide.addView(more);root.addView(guide);Ui.gap(a,root,24);
  LinearLayout filters=Ui.row(a);LinearLayout list=Ui.col(a);String[] names={"全部","基础","进阶"};Button[] btn=new Button[3];for(int i=0;i<3;i++){final int n=i;btn[i]=Ui.button(a,names[i],i==0?Ui.ACCENT:Ui.CARD,i==0?Ui.INK:Ui.DIM);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);if(i>0)p.leftMargin=Ui.dp(a,8);filters.addView(btn[i],p);btn[i].setOnClickListener(v->{for(int j=0;j<3;j++){Ui.touch(btn[j],j==n?Ui.ACCENT:Ui.CARD,16);btn[j].setTextColor(j==n?Ui.INK:Ui.DIM);btn[j].setSelected(j==n);}fill(a,list,n);});}root.addView(filters);Ui.gap(a,root,18);root.addView(list);fill(a,list,0);
 }
 private static void fill(Activity a,LinearLayout l,int filter){l.removeAllViews();for(TrainingProgram p:ProgramLibrary.all())if(filter==0||filter==1&&!ProgramLibrary.advanced(p)||filter==2&&ProgramLibrary.advanced(p))programCard(a,l,p);}
 public static void programCard(Activity a,LinearLayout parent,TrainingProgram p){LinearLayout c=Ui.card(a);LinearLayout top=Ui.row(a);top.addView(Ui.pill(a,ProgramLibrary.level(p),ProgramLibrary.advanced(p)?Ui.WARN:Ui.ACCENT));TextView time=Ui.tv(a,p.freeRide?"不限时":p.totalSeconds()/60+" MIN",12,Ui.DIM,true);time.setGravity(Gravity.RIGHT);Ui.weight(top,time);c.addView(top);Ui.gap(a,c,16);c.addView(Ui.number(a,p.name,23,Ui.TEXT));Ui.gap(a,c,7);c.addView(Ui.sub(a,p.subtitle));Ui.gap(a,c,18);Visuals.Profile profile=new Visuals.Profile(a,p);c.addView(profile,new LinearLayout.LayoutParams(-1,Ui.dp(a,44)));Ui.gap(a,c,14);LinearLayout foot=Ui.row(a);Ui.weight(foot,Ui.tv(a,ProgramLibrary.effort(p)+"  /  "+(p.freeRide?"手动掌控":"跟随分段目标"),12,Ui.DIM,false));foot.addView(new Visuals.Icon(a,"arrow",Ui.ACCENT),new LinearLayout.LayoutParams(Ui.dp(a,24),Ui.dp(a,24)));c.addView(foot);Ui.touch(c,Ui.CARD,22);c.setContentDescription(p.name+"，"+p.subtitle+"，查看计划");c.setOnClickListener(v->ProgramDetailActivity.open(a,p.id));Ui.add(a,parent,c,14);}
}
