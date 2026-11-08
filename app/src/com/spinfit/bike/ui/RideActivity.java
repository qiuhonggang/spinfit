package com.spinfit.bike.ui;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.os.*;
import android.view.*;
import android.widget.*;
import com.spinfit.bike.app.*;
import com.spinfit.bike.ble.*;
import com.spinfit.bike.core.*;

/** Live data and resistance first; course context stays compact and secondary. */
public final class RideActivity extends Activity {
    private BikeSession s;
    private TextView clock,remaining,power,powerUnit,note,connection,cad,hr,speed,distance,calories;
    private TextView actual,control,resNote,stage,stageRemaining,target,next,minus,plus;
    private Button pause,auto,reconnect;
    private ProgressBar stageProgress;
    private final Handler h=new Handler(Looper.getMainLooper());
    private boolean visible,opening;
    private final Runnable refresh=new Runnable(){public void run(){if(!visible)return;render();h.postDelayed(this,500);}};

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);s=BikeSession.get();s.init(this);
        if(s.engine()==null||s.program()==null){finish();return;}
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout root=Ui.root(this);
        root.setPadding(Ui.dp(this,20),Ui.dp(this,8),Ui.dp(this,20),Ui.dp(this,16));
        boolean wide=getResources().getConfiguration().screenWidthDp>=600&&
            getResources().getConfiguration().screenWidthDp>getResources().getConfiguration().screenHeightDp;
        if(wide){
            LinearLayout header=Ui.row(this);
            header.addView(Ui.iconButton(this,"back","返回",()->Ui.goTab(this,0)),new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48)));
            TextView name=Ui.tv(this,s.program().name,17,Ui.TEXT,true);name.setPadding(Ui.dp(this,12),0,Ui.dp(this,12),0);Ui.weight(header,name);
            LinearLayout time=Ui.col(this);time.setGravity(Gravity.RIGHT);clock=Ui.number(this,"00:00",24,Ui.TEXT);time.addView(clock);
            remaining=Ui.tv(this,"",11,Ui.DIM,false);time.addView(remaining);header.addView(time);
            connection=Ui.pill(this,"等待连接",Ui.ACCENT);LinearLayout.LayoutParams badge=new LinearLayout.LayoutParams(-2,-2);badge.leftMargin=Ui.dp(this,16);header.addView(connection,badge);
            root.addView(header);Ui.gap(this,root,8);root.addView(warnings());
            LinearLayout columns=Ui.row(this);columns.setGravity(Gravity.TOP);
            columns.addView(dataCard(true),new LinearLayout.LayoutParams(0,-2,1));
            LinearLayout right=Ui.col(this);right.addView(resistanceCard());if(!s.program().freeRide){Ui.gap(this,right,12);right.addView(stageCard());}
            LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,-2,1);rp.leftMargin=Ui.dp(this,12);columns.addView(right,rp);root.addView(columns);
        }else{
            LinearLayout bar=Ui.topBar(this,s.program().name,()->Ui.goTab(this,0));
            bar.setPadding(0,0,0,Ui.dp(this,8));root.addView(bar);
            LinearLayout times=Ui.row(this),left=Ui.col(this),right=Ui.col(this);
            left.addView(Ui.tv(this,"骑行用时",11,Ui.DIM,false));Ui.gap(this,left,4);
            clock=Ui.number(this,"00:00",28,Ui.TEXT);left.addView(clock);Ui.weight(times,left);
            right.setGravity(Gravity.RIGHT);connection=Ui.pill(this,"等待连接",Ui.ACCENT);right.addView(connection);
            Ui.gap(this,right,6);remaining=Ui.tv(this,"",12,Ui.DIM,false);right.addView(remaining);times.addView(right);
            root.addView(times);Ui.gap(this,root,12);
            LinearLayout watts=Ui.col(this);watts.setGravity(Gravity.CENTER);
            powerUnit=Ui.tv(this,"实时功率 / W",12,Ui.DIM,false);watts.addView(powerUnit);
            power=Ui.number(this,"—",60,Ui.ACCENT);watts.addView(power);watts.addView(warnings());
            root.addView(watts);Ui.gap(this,root,12);root.addView(dataCard(false));Ui.gap(this,root,12);
            root.addView(resistanceCard());if(!s.program().freeRide){Ui.gap(this,root,12);root.addView(stageCard());}
        }

        LinearLayout footer=Ui.row(this);
        footer.setPadding(Ui.dp(this,20),Ui.dp(this,8),Ui.dp(this,20),Ui.dp(this,8));footer.setBackgroundColor(Ui.BG);
        pause=Ui.primary(this,"暂停训练");pause.setOnClickListener(v->{
            if(s.engine().isPaused()){
                if(!s.resume())Ui.dialog(this,"等待单车数据","请确认单车电源与蓝牙连接，并踩动踏板。数据恢复后再点击继续。");
            }else s.pause();render();
        });Ui.weight(footer,pause);
        Button end=Ui.button(this,"结束",Ui.CARD2,Ui.TEXT);end.setSingleLine(true);
        end.setAutoSizeTextTypeUniformWithConfiguration(12,15,1,android.util.TypedValue.COMPLEX_UNIT_SP);
        end.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("结束这次骑行？")
            .setMessage("当前训练与有效数据将保存在本地记录中。")
            .setNegativeButton("继续骑行",null).setPositiveButton("结束并保存",(d,w)->finishRide()).show());
        LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(Ui.dp(this,96),-2);ep.leftMargin=Ui.dp(this,12);footer.addView(end,ep);
        Ui.screen(this,root,footer);
    }

    private LinearLayout warnings(){
        LinearLayout panel=Ui.col(this);note=Ui.tv(this,"",12,Ui.WARN,false);note.setGravity(Gravity.CENTER);note.setVisibility(View.GONE);panel.addView(note);
        reconnect=Ui.button(this,"重新连接单车",Ui.CARD2,Ui.TEXT);reconnect.setVisibility(View.GONE);
        reconnect.setOnClickListener(v->{if(s.reconnectCurrentBike())Toast.makeText(this,"正在重新连接原单车",Toast.LENGTH_SHORT).show();});panel.addView(reconnect);return panel;
    }

    private LinearLayout dataCard(boolean wide){
        LinearLayout card=Ui.card(this);card.setPadding(Ui.dp(this,12),Ui.dp(this,12),Ui.dp(this,12),Ui.dp(this,12));
        boolean large=getResources().getConfiguration().screenWidthDp<340||
            (getResources().getConfiguration().fontScale>1.35f&&getResources().getConfiguration().screenWidthDp<600);
        LinearLayout row=Ui.row(this);
        if(wide){
            LinearLayout tile=Ui.col(this);tile.setGravity(Gravity.CENTER);
            power=Ui.number(this,"—",28,Ui.ACCENT);power.setSingleLine(true);power.setAutoSizeTextTypeUniformWithConfiguration(18,28,1,android.util.TypedValue.COMPLEX_UNIT_SP);tile.addView(power);
            Ui.gap(this,tile,4);powerUnit=Ui.tv(this,"功率 / W",11,Ui.DIM,false);powerUnit.setGravity(Gravity.CENTER);tile.addView(powerUnit);Ui.weight(row,tile);
            cad=metric(row,"踏频 / rpm");hr=metric(row,"心率 / bpm  ›");card.addView(row);dataDivider(card);
            row=Ui.row(this);speed=metric(row,"速度 / km/h");distance=metric(row,"里程 / km");calories=metric(row,"消耗 / kcal");card.addView(row);
        }else{
        cad=metric(row,"踏频 / rpm");hr=metric(row,"心率 / bpm  ›");
        if(large){
            card.addView(row);dataDivider(card);row=Ui.row(this);speed=metric(row,"速度 / km/h");distance=metric(row,"里程 / km");
            card.addView(row);dataDivider(card);row=Ui.row(this);calories=metric(row,"消耗 / kcal");card.addView(row);
        }else{
            speed=metric(row,"速度 / km/h");card.addView(row);dataDivider(card);
            row=Ui.row(this);distance=metric(row,"里程 / km");calories=metric(row,"消耗 / kcal");card.addView(row);
        }
        }
        View heartTile=(View)hr.getParent();Ui.touch(heartTile,Ui.CARD,12);
        heartTile.setContentDescription("连接或查看心率设备");heartTile.setOnClickListener(v->startActivity(new Intent(this,HeartRateActivity.class)));
        return card;
    }

    private LinearLayout resistanceCard(){
        LinearLayout card=Ui.card(this);card.setPadding(Ui.dp(this,16),Ui.dp(this,12),Ui.dp(this,16),Ui.dp(this,12));
        LinearLayout heading=Ui.row(this);Ui.weight(heading,Ui.tv(this,"阻力控制",16,Ui.TEXT,true));
        auto=Ui.button(this,"自动调阻",Ui.CARD2,Ui.ACCENT);auto.setTextSize(12);
        auto.setPadding(Ui.dp(this,12),Ui.dp(this,8),Ui.dp(this,12),Ui.dp(this,8));
        auto.setMinHeight(Ui.dp(this,48));auto.setMinimumHeight(Ui.dp(this,48));
        auto.setOnClickListener(v->{if(s.engine().isPaused())return;s.setAuto(!s.engine().isAutoResistance());render();});
        heading.addView(auto);card.addView(heading);Ui.gap(this,card,8);
        LinearLayout controls=Ui.row(this);
        minus=Ui.circleButton(this,"−",Ui.CARD2,Ui.TEXT);minus.setContentDescription("降低一档阻力");minus.setOnClickListener(v->adjust(-1));
        controls.addView(minus,new LinearLayout.LayoutParams(Ui.dp(this,52),Ui.dp(this,52)));
        LinearLayout value=Ui.col(this);value.setGravity(Gravity.CENTER);
        actual=Ui.number(this,"—",34,Ui.TEXT);value.addView(actual);
        control=Ui.tv(this,"单车实测",12,Ui.DIM,false);control.setGravity(Gravity.CENTER);value.addView(control);Ui.weight(controls,value);
        plus=Ui.circleButton(this,"+",Ui.CARD2,Ui.TEXT);plus.setContentDescription("增加一档阻力");plus.setOnClickListener(v->adjust(1));
        controls.addView(plus,new LinearLayout.LayoutParams(Ui.dp(this,52),Ui.dp(this,52)));card.addView(controls);
        resNote=Ui.tv(this,"",12,Ui.DIM,false);resNote.setPadding(0,Ui.dp(this,8),0,0);resNote.setVisibility(View.GONE);card.addView(resNote);
        return card;
    }

    private LinearLayout stageCard(){
        LinearLayout card=Ui.card(this);card.setPadding(Ui.dp(this,16),Ui.dp(this,12),Ui.dp(this,16),Ui.dp(this,12));
        LinearLayout title=Ui.row(this);stage=Ui.tv(this,"",15,Ui.TEXT,true);Ui.weight(title,stage);
        stageRemaining=Ui.tv(this,"",12,Ui.DIM,false);title.addView(stageRemaining);card.addView(title);Ui.gap(this,card,8);
        stageProgress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        stageProgress.setProgressBackgroundTintList(ColorStateList.valueOf(Ui.CARD2));
        card.addView(stageProgress,new LinearLayout.LayoutParams(-1,Ui.dp(this,4)));Ui.gap(this,card,8);
        target=Ui.tv(this,"",12,Ui.DIM,false);card.addView(target);Ui.gap(this,card,4);
        next=Ui.tv(this,"",12,Ui.DIM,false);card.addView(next);
        return card;
    }

    private TextView metric(LinearLayout row,String label){
        LinearLayout tile=Ui.col(this);tile.setGravity(Gravity.CENTER);
        TextView value=Ui.number(this,"—",28,Ui.TEXT);value.setSingleLine(true);
        value.setAutoSizeTextTypeUniformWithConfiguration(18,28,1,android.util.TypedValue.COMPLEX_UNIT_SP);tile.addView(value);
        Ui.gap(this,tile,4);TextView name=Ui.tv(this,label,11,Ui.DIM,false);name.setGravity(Gravity.CENTER);tile.addView(name);Ui.weight(row,tile);return value;
    }
    private void dataDivider(LinearLayout card){Ui.gap(this,card,10);card.addView(Ui.divider(this),new LinearLayout.LayoutParams(-1,Ui.dp(this,1)));Ui.gap(this,card,10);}
    private void adjust(int delta){s.adjustBy(delta);render();}

    private void render(){
        if(s.engine()==null)return;Metrics m=s.engine().snapshot();if(m.finished){showSaved();return;}
        BikeSource.Sample x=s.fresh()?s.lastSample():null;boolean paused=s.engine().isPaused();
        clock.setText(Ui.time(m.elapsedSec));remaining.setText(s.program().freeRide?"自由骑行":"剩余 "+Ui.time(m.remainingSec));
        connection.setText(!s.isConnected()?"连接已断开":s.isDemo()?"演示训练":s.fresh()?"单车已连接":"等待数据");
        connection.setTextColor(s.isDemo()||!s.fresh()?Ui.WARN:Ui.ACCENT);
        power.setText(x!=null&&x.powerValid?Ui.val(x.powerW,0):"—");powerUnit.setText(s.isDemo()?"模拟功率 / W":"实时功率 / W");
        cad.setText(x!=null&&x.cadenceValid?Ui.val(x.cadenceRpm,0):"—");
        int bpm=s.currentHeartRate();hr.setText(bpm>0?""+bpm:"—");hr.setTextColor(Ui.heartRateColor(bpm));
        ((View)hr.getParent()).setContentDescription("心率 "+(bpm>0?bpm+" 次每分钟":"暂不可用")+"，来源 "+s.heartSource()+"，点击连接或查看心率设备");
        speed.setText(x!=null&&x.speedValid?Ui.val(x.speedKph,1):"—");
        TelemetryStats stats=s.stats();distance.setText(stats!=null&&stats.distanceSeen?Ui.val(stats.distance,2):"—");
        calories.setText(stats!=null&&stats.caloriesSeen?Ui.val(stats.calories,0):"—");
        String warning=!s.saveError().isEmpty()?s.saveError():paused?(s.dataPaused()?"数据中断，训练已暂停":"训练已暂停"):"";
        note.setText(warning);note.setVisibility(warning.isEmpty()?View.GONE:View.VISIBLE);
        reconnect.setVisibility(s.dataPaused()&&!s.isDemo()?View.VISIBLE:View.GONE);
        int resistance=x!=null&&x.resistanceValid?x.resistance:-1;actual.setText(resistance>=0?""+resistance:"—");
        control.setText(m.autoResistance?"实际 / 目标 "+Math.round(m.targetResistance)+" 档":"单车实测");
        auto.setText(m.autoResistance?"自动调阻 · 开":s.program().freeRide?"开启自动":"恢复自动");
        auto.setTextColor(m.autoResistance?Ui.ACCENT:Ui.DIM);auto.setEnabled(!paused);
        plus.setEnabled(!paused);minus.setEnabled(!paused);plus.setAlpha(paused?.4f:1);minus.setAlpha(paused?.4f:1);
        BikeSource src=s.source();BikeSource.WriteResult w=s.lastWrite();String feedback="";
        if(src!=null&&!src.supportsResistanceTarget()&&!s.isDemo())feedback="请使用车上旋钮调节阻力";
        else if(!s.isDemo()&&w!=null){
            if(!w.sent)feedback=w.message;
            else if(s.ble()!=null&&!s.ble().resistanceControlVerified())feedback=s.ble().controlStatus();
        }
        resNote.setText(feedback);resNote.setVisibility(feedback.isEmpty()?View.GONE:View.VISIBLE);
        if(stage!=null){
            int index=Math.min(m.stageIndex,s.program().segments.length-1),before=0;
            for(int i=0;i<index;i++)before+=s.program().segments[i].durationSec;
            Segment current=s.program().segments[index];int used=Math.max(0,m.elapsedSec-before);
            stage.setText(current.name);stage.setTextColor(Ui.zoneColor(current.zone));
            stageRemaining.setText("本段 "+Ui.time(Math.max(0,current.durationSec-used)));
            stageProgress.setMax(current.durationSec);stageProgress.setProgress(used);
            stageProgress.setProgressTintList(ColorStateList.valueOf(Ui.zoneColor(current.zone)));
            target.setText("参考 "+Math.round(m.targetPowerW)+" W  ·  "+Math.round(m.targetCadence)+" rpm");
            next.setText(index+1<s.program().segments.length?"接下来："+s.program().segments[index+1].name+" · "+s.program().segments[index+1].durationSec/60+" 分钟":"本段结束后保存训练");
        }
        pause.setText(paused?"继续训练":"暂停训练");
    }
    private void finishRide(){s.finishWorkout();if(!s.saveError().isEmpty()){Ui.dialog(this,"未能保存",s.saveError());return;}showSaved();}
    private void showSaved(){if(opening)return;opening=true;SummaryActivity.open(this,s.recordId(),true);finish();}
    @Override protected void onResume(){super.onResume();visible=true;h.post(refresh);}
    @Override protected void onPause(){visible=false;h.removeCallbacks(refresh);super.onPause();}
    @Override public void onBackPressed(){Ui.goTab(this,0);}
    @Override protected void onDestroy(){h.removeCallbacksAndMessages(null);super.onDestroy();}
}
