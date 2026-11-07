package com.spinfit.bike.ui;
import android.app.*;import android.content.*;import android.os.Bundle;import android.widget.*;
import com.spinfit.bike.app.*;import java.util.*;import java.text.SimpleDateFormat;

public final class SummaryActivity extends Activity {
    private WorkoutStore.Ride ride;private Visuals.Chart chart;private long id;private TextView chartNote;
    private final List<Button> tabs=new ArrayList<>();private final String[] fields={"speed","distance","hr","power","cadence"},units={"km/h","km","bpm","W","rpm"};
    private final int[] colors={Ui.ACCENT,Ui.BLUE,Ui.PINK,Ui.WARN,Ui.VIOLET};
    public static void open(Activity a,long id,boolean fresh){Intent i=new Intent(a,SummaryActivity.class);i.putExtra("record",id);i.putExtra("fresh",fresh);a.startActivity(i);}
    @Override protected void onCreate(Bundle b){super.onCreate(b);BikeSession.get().init(this);id=getIntent().getLongExtra("record",-1);ride=BikeSession.get().store().get(id);if(ride==null){finish();return;}
        LinearLayout root=Ui.root(this);root.addView(Ui.topBar(this,"训练记录",this::finish));boolean fresh=getIntent().getBooleanExtra("fresh",false);
        root.addView(Ui.eyebrow(this,fresh?"RIDE SAVED. WELL DONE.":"YOUR RIDE / "+new SimpleDateFormat("yyyy.MM.dd",Locale.US).format(new Date(ride.started))));Ui.gap(this,root,12);
        root.addView(Ui.number(this,fresh?"为今天的自己，鼓掌。":ride.name,29,Ui.TEXT));Ui.gap(this,root,10);root.addView(Ui.sub(this,ride.name+"  ·  "+HistoryActivity.status(ride.status)));Ui.gap(this,root,22);
        if(ride.demo)Ui.add(this,root,Ui.pill(this,ride.hrDevice.isEmpty()?"演示训练 · 全部为模拟数据":"演示单车 · 心率来自外部设备",Ui.WARN),18);
        if(ride.status.equals("interrupted"))Ui.add(this,root,Ui.sub(this,"上次训练意外中断，已保留最近一次保存的数据。"),16);
        LinearLayout hero=Ui.card(this);hero.addView(Ui.tv(this,"骑行时长",14,Ui.DIM,false));Ui.gap(this,hero,10);hero.addView(Ui.number(this,Ui.time(ride.seconds),58,Ui.ACCENT));Ui.gap(this,hero,24);
        LinearLayout row=Ui.row(this);Ui.weight(row,Ui.metric(this,"累计里程",Ui.val(ride.distance,2),"km"));Ui.weight(row,Ui.metric(this,"平均速度",Ui.val(ride.avgSpeed,1),"km/h"));hero.addView(row);Ui.gap(this,hero,24);
        row=Ui.row(this);Ui.weight(row,Ui.metric(this,"最高速度",Ui.val(ride.maxSpeed,1),"km/h"));Ui.weight(row,Ui.metric(this,"单车累计消耗",Ui.val(ride.calories,0),"kcal"));hero.addView(row);Ui.add(this,root,hero,0);
        if(ride.speedBasis.equals("legacy_samples")){Ui.gap(this,root,12);root.addView(Ui.tv(this,"历史记录的平均速度根据已保存采样回补。",12,Ui.DIM,false));}
        root.addView(Ui.section(this,"训练曲线",null,null));String[] labels={"速度","里程","心率","功率","踏频"};
        for(int r=0;r<2;r++){LinearLayout filters=Ui.row(this);int first=r==0?0:3,last=r==0?3:5;for(int j=first;j<last;j++){final int n=j;Button button=Ui.button(this,labels[j],j==0?Ui.ACCENT:Ui.CARD,j==0?Ui.INK:Ui.DIM);button.setTextSize(13);button.setSelected(j==0);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);if(j>first)lp.leftMargin=Ui.dp(this,8);filters.addView(button,lp);tabs.add(button);button.setOnClickListener(v->select(n));}Ui.add(this,root,filters,8);}
        Ui.gap(this,root,12);chart=new Visuals.Chart(this);root.addView(chart,new LinearLayout.LayoutParams(-1,Ui.dp(this,210)));Ui.gap(this,root,12);chartNote=Ui.tv(this,"",12,Ui.DIM,false);root.addView(chartNote);select(0);
        root.addView(Ui.section(this,"更多数据",null,null));LinearLayout data=Ui.card(this);row=Ui.row(this);Ui.weight(row,Ui.metric(this,"平均功率",Ui.val(ride.avgPower,0),"W"));Ui.weight(row,Ui.metric(this,"最高功率",Ui.val(ride.maxPower,0),"W"));data.addView(row);Ui.gap(this,data,24);row=Ui.row(this);Ui.weight(row,Ui.metric(this,"平均踏频",Ui.val(ride.avgCad,0),"rpm"));Ui.weight(row,Ui.metric(this,"平均心率",Ui.val(ride.avgHr,0),"bpm"));data.addView(row);Ui.gap(this,data,20);data.addView(Ui.sub(this,ride.hrDevice.isEmpty()?"心率按设备有效采样统计":"外部心率设备："+ride.hrDevice));root.addView(data);
        Ui.gap(this,root,20);root.addView(Ui.sub(this,"单车数据来自 "+ride.device+"。缺失字段显示 —；均值只计算有效采样。里程与消耗使用训练期间设备累计值的增量。"));Ui.gap(this,root,26);
        Button again=Ui.primary(this,"再选一节好课  →");again.setOnClickListener(v->Ui.goTab(this,1));root.addView(again);Ui.gap(this,root,12);Button history=Ui.button(this,"查看全部记录",Ui.CARD,Ui.TEXT);history.setOnClickListener(v->Ui.goTab(this,2));root.addView(history);Ui.screen(this,root,null);
    }
    private void select(int index){for(int i=0;i<tabs.size();i++){Ui.touch(tabs.get(i),i==index?Ui.ACCENT:Ui.CARD,16);tabs.get(i).setTextColor(i==index?Ui.INK:Ui.DIM);tabs.get(i).setSelected(i==index);}
        List<float[]> points=BikeSession.get().store().points(id,fields[index]);chart.data(points,colors[index],units[index]);boolean valid=false;for(float[] point:points)if(point[1]>=0){valid=true;break;}
        chartNote.setText(!valid&&index==1&&ride.speedBasis.equals("legacy_samples")?"此历史记录仅保存总里程，未保存逐秒里程曲线。新训练会完整记录。":!valid?"这次训练未收到该项数据，曲线留空。":"横向滑动曲线可查看具体时刻的数据 · 单位 "+units[index]);
    }
}
