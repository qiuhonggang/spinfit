package com.spinfit.bike.ui;
import android.app.Activity;
import android.content.Context;
import android.graphics.*;
import android.view.View;
import com.spinfit.bike.core.*;
import java.util.*;
public final class Visuals {
 public static final class Icon extends View{
  private final String kind;private final Paint p=new Paint(3);private final int color;
  public Icon(Context c,String k,int color){super(c);kind=k;this.color=color;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
  protected void onDraw(Canvas c){super.onDraw(c);c.save();float s=Math.min(Math.min(getWidth(),getHeight()),Ui.dp(getContext(),24))/24f;c.translate((getWidth()-24*s)/2,(getHeight()-24*s)/2);c.scale(s,s);p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.7f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
   Path q=new Path();switch(kind){
   case "home":q.moveTo(3,11);q.lineTo(12,3);q.lineTo(21,11);q.moveTo(5,10);q.lineTo(5,21);q.lineTo(10,21);q.lineTo(10,15);q.lineTo(14,15);q.lineTo(14,21);q.lineTo(19,21);q.lineTo(19,10);break;
   case "plan":c.drawRoundRect(4,5,20,21,3,3,p);c.drawLine(8,3,8,7,p);c.drawLine(16,3,16,7,p);c.drawLine(4,10,20,10,p);c.drawLine(8,14,11,14,p);c.drawLine(14,14,16,14,p);c.drawLine(8,17,11,17,p);break;
   case "chart":q.moveTo(3,19);q.lineTo(3,5);q.moveTo(3,19);q.lineTo(21,19);q.moveTo(7,15);q.lineTo(7,11);q.moveTo(12,15);q.lineTo(12,6);q.moveTo(17,15);q.lineTo(17,9);break;
   case "back":q.moveTo(14,6);q.lineTo(8,12);q.lineTo(14,18);break;
   case "arrow":q.moveTo(5,12);q.lineTo(19,12);q.moveTo(13,6);q.lineTo(19,12);q.lineTo(13,18);break;
   case "settings":c.drawCircle(12,12,4,p);c.drawCircle(12,12,9,p);for(int i=0;i<8;i++){double t=i*Math.PI/4;c.drawLine(12+(float)Math.cos(t)*9,12+(float)Math.sin(t)*9,12+(float)Math.cos(t)*11,12+(float)Math.sin(t)*11,p);}break;
   case "bluetooth":q.moveTo(7,7);q.lineTo(17,17);q.lineTo(12,22);q.lineTo(12,2);q.lineTo(17,7);q.lineTo(7,17);break;
   case "close":q.moveTo(6,6);q.lineTo(18,18);q.moveTo(18,6);q.lineTo(6,18);break;
   case "check":q.moveTo(5,12);q.lineTo(10,17);q.lineTo(20,6);break;
   case "bike":c.drawCircle(6,16,4,p);c.drawCircle(19,16,4,p);q.moveTo(6,16);q.lineTo(10,8);q.lineTo(16,8);q.lineTo(19,16);q.moveTo(6,16);q.lineTo(13,16);q.lineTo(9,6);q.lineTo(6,6);q.moveTo(13,16);q.lineTo(16,8);q.moveTo(15,4);q.lineTo(18,4);q.lineTo(16,8);break;
   default:c.drawCircle(12,12,8,p);
   }c.drawPath(q,p);c.restore();}
 }
 public static final class Hero extends View{
  private final Paint p=new Paint(3);public Hero(Context c){super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
  protected void onDraw(Canvas c){float w=getWidth(),h=getHeight();p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Ui.dp(getContext(),1));p.setColor(0x25283718);float cx=w*.94f,cy=h*.42f;for(int i=0;i<7;i++)c.drawCircle(cx,cy,Ui.dp(getContext(),42+i*24),p);
   c.save();c.translate(w*.55f,h*.60f);float sc=w/365f;c.scale(sc,sc);p.setStrokeWidth(3);p.setColor(Ui.INK);c.drawCircle(35,36,30,p);c.drawCircle(125,36,30,p);Path q=new Path();q.moveTo(35,36);q.lineTo(67,-16);q.lineTo(102,-16);q.lineTo(125,36);q.moveTo(35,36);q.lineTo(82,36);q.lineTo(58,-30);q.lineTo(40,-30);q.moveTo(82,36);q.lineTo(102,-16);q.moveTo(97,-40);q.lineTo(113,-40);q.lineTo(102,-16);c.drawPath(q,p);c.drawCircle(82,36,7,p);c.drawLine(82,36,95,45,p);c.restore();}
 }
 public static final class Profile extends View{
  private final Paint p=new Paint(3);private TrainingProgram program;private int stage=-1;
  public Profile(Context c,TrainingProgram x){super(c);program=x;setContentDescription("训练强度分段图，详细阶段见下方列表");}
  public void stage(int s){stage=s;invalidate();}
  protected void onDraw(Canvas c){float w=getWidth(),h=getHeight();int total=program.totalSeconds();float x=0;for(int i=0;i<program.segments.length;i++){Segment s=program.segments[i];float bw=w*s.durationSec/Math.max(1,total);float ht=h*(.18f+Math.min(120,s.targetPowerPct)/140f);p.setColor(stage<0||i==stage?Ui.zoneColor(s.zone):Ui.CARD2);c.drawRoundRect(x+2,h-ht,x+bw-2,h,Ui.dp(getContext(),3),Ui.dp(getContext(),3),p);x+=bw;}}
 }
 public static final class Chart extends View {
    private final Paint paint=new Paint(3);private List<float[]> points=new ArrayList<>();private int color=Ui.ACCENT,selected=-1;
    private String unit="W";private float downX,downY;
    public Chart(Context c){super(c);setFocusable(true);setClickable(true);}
    public void data(List<float[]> values,int color,String unit){this.points=values;this.color=color;this.unit=unit;selected=-1;setContentDescription(unit+"训练曲线，可横向滑动查看数据，缺失数据处留空");invalidate();}
    private float scaleMax(float max){float floor=unit.equals("km")?.1f:unit.equals("km/h")?5:50;float wanted=Math.max(floor,max*1.08f);float step=(float)Math.pow(10,Math.floor(Math.log10(wanted)));for(float m:new float[]{1,2,2.5f,5,10})if(step*m>=wanted)return step*m;return wanted;}
    private String format(float value){return String.format(Locale.US,unit.equals("km")?"%.2f":unit.equals("km/h")?"%.1f":"%.0f",value);}
    private float end(){return points.isEmpty()?1:Math.max(1,points.get(points.size()-1)[0]);}
    @Override protected void onDraw(Canvas canvas){
        float d=getResources().getDisplayMetrics().density,l=40*d,r=getWidth()-8*d,t=40*d,b=getHeight()-28*d,max=0;boolean any=false;
        for(float[] value:points)if(value[1]>=0){max=Math.max(max,value[1]);any=true;}max=scaleMax(max);float end=end();
        paint.setTypeface(Typeface.create("sans-serif",0));paint.setTextSize(10*d);paint.setStrokeWidth(d);paint.setStyle(Paint.Style.FILL);
        for(int i=0;i<3;i++){float y=t+(b-t)*i/2;paint.setColor(Ui.DIV);canvas.drawLine(l,y,r,y,paint);paint.setColor(Ui.DIM);String label=unit.equals("km")?String.format(Locale.US,"%.2f",max*(2-i)/2):Ui.val(max*(2-i)/2,0);canvas.drawText(label,0,y+3*d,paint);}
        Path line=new Path();boolean started=false;float previous=-1;
        for(float[] value:points){if(value[1]<0){started=false;previous=-1;continue;}float x=l+(r-l)*value[0]/end,y=b-(b-t)*value[1]/max;if(!started)line.moveTo(x,y);else line.lineTo(x,y);started=true;previous=value[0];}
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2*d);paint.setColor(color);canvas.drawPath(line,paint);paint.setStyle(Paint.Style.FILL);
        if(points.size()==1&&points.get(0)[1]>=0)canvas.drawCircle(l,b-(b-t)*points.get(0)[1]/max,3*d,paint);
        paint.setColor(Ui.DIM);paint.setTextSize(10*d);canvas.drawText("0:00",l,getHeight()-4*d,paint);String label=Ui.time((int)end);canvas.drawText(label,r-paint.measureText(label),getHeight()-4*d,paint);
        if(!any){paint.setTextSize(13*d);String empty="暂无"+unit+"数据";canvas.drawText(empty,l+(r-l-paint.measureText(empty))/2,t+(b-t)/2,paint);}
        if(selected>=0&&selected<points.size()){
            float[] value=points.get(selected);float x=l+(r-l)*value[0]/end;paint.setColor(Ui.DIM);paint.setStrokeWidth(d);canvas.drawLine(x,t,x,b,paint);
            String text=Ui.time((int)value[0])+"  ·  "+(value[1]>=0?format(value[1])+" "+unit:"无数据");paint.setTextSize(12*d);paint.setColor(Ui.TEXT);canvas.drawText(text,l,18*d,paint);
            if(value[1]>=0){paint.setColor(color);canvas.drawCircle(x,b-(b-t)*value[1]/max,4*d,paint);}
        }else{paint.setColor(Ui.DIM);paint.setTextSize(11*d);canvas.drawText(unit,l,18*d,paint);}
    }
    private void choose(float x){float d=getResources().getDisplayMetrics().density,l=40*d,r=getWidth()-8*d;float seconds=Math.max(0,Math.min(1,(x-l)/Math.max(1,r-l)))*end();int index=-1;float best=Float.MAX_VALUE;for(int i=0;i<points.size();i++){float delta=Math.abs(points.get(i)[0]-seconds);if(delta<best){best=delta;index=i;}}selected=index;invalidate();}
    @Override public boolean onTouchEvent(android.view.MotionEvent e){
        if(points.isEmpty())return super.onTouchEvent(e);
        if(e.getAction()==android.view.MotionEvent.ACTION_DOWN){downX=e.getX();downY=e.getY();choose(e.getX());return true;}
        if(e.getAction()==android.view.MotionEvent.ACTION_MOVE){if(Math.abs(e.getX()-downX)>Math.abs(e.getY()-downY)&&Math.abs(e.getX()-downX)>Ui.dp(getContext(),8))getParent().requestDisallowInterceptTouchEvent(true);choose(e.getX());return true;}
        if(e.getAction()==android.view.MotionEvent.ACTION_UP){choose(e.getX());getParent().requestDisallowInterceptTouchEvent(false);performClick();return true;}
        if(e.getAction()==android.view.MotionEvent.ACTION_CANCEL){getParent().requestDisallowInterceptTouchEvent(false);return true;}return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
 }
}
