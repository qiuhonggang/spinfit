package com.spinfit.bike.ui;
import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.Build;
import android.view.*;
import android.widget.*;

/** Quiet surfaces, generous type, one citrus accent. Native controls and dp geometry. */
public final class Ui {
 public static final int BG=0xFF101412,BG2=BG,CARD=0xFF1B211D,CARD2=0xFF29312B,TEXT=0xFFF3F5EF,DIM=0xFFADB7AC,DIV=0xFF354037,ACCENT=0xFFD7F86B,RED=ACCENT,PINK=0xFFF19483,BLUE=0xFFA7C7BE,VIOLET=0xFFC5BBDE,WARN=0xFFF3C884,INK=0xFF182013;
 private Ui(){}
 public static int dp(Context a,float v){return Math.round(v*a.getResources().getDisplayMetrics().density);}
 public static void window(Activity a){a.getWindow().setStatusBarColor(BG);a.getWindow().setNavigationBarColor(BG);a.getWindow().getDecorView().setSystemUiVisibility(0);a.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);}
 public static GradientDrawable round(int color,float radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(radius*3);return g;}
 public static GradientDrawable gradient(int a,int b,float radius){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{a,b});g.setCornerRadius(radius*3);return g;}
 public static GradientDrawable stroke(int color,int width,int fill,float radius){GradientDrawable g=round(fill,radius);g.setStroke(width,color);return g;}
 public static void surface(View v,int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(v.getContext(),radius));v.setBackground(g);}
 public static void touch(View v,int fill,int radius){GradientDrawable bg=new GradientDrawable();bg.setColor(fill);bg.setCornerRadius(dp(v.getContext(),radius));v.setBackground(new RippleDrawable(ColorStateList.valueOf(0x25FFFFFF),bg,null));v.setClickable(true);v.setFocusable(true);}
 public static TextView tv(Activity a,String text,float size,int color,boolean bold){TextView t=new TextView(a);t.setLayoutParams(new LinearLayout.LayoutParams(-2,-2));t.setText(text);t.setTextSize(size);t.setTextColor(color);t.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",0));t.setIncludeFontPadding(false);t.setLineSpacing(dp(a,3),1.08f);return t;}
 public static TextView number(Activity a,String text,float size,int color){TextView t=tv(a,text,size,color,true);t.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));t.setLetterSpacing(-.035f);t.setFontFeatureSettings("tnum");return t;}
 public static TextView sub(Activity a,String s){return tv(a,s,13,DIM,false);}
 public static TextView title(Activity a,String s){TextView t=number(a,s,30,TEXT);t.setPadding(0,dp(a,6),0,dp(a,8));return t;}
 public static TextView eyebrow(Activity a,String s){TextView t=tv(a,s,11,ACCENT,true);t.setLetterSpacing(.14f);return t;}
 public static TextView pill(Activity a,String s,int c){TextView t=tv(a,s,11,c,true);t.setPadding(dp(a,10),dp(a,6),dp(a,10),dp(a,6));surface(t,Color.argb(24,Color.red(c),Color.green(c),Color.blue(c)),20);return t;}
 public static Button button(Activity a,String s,int bg,int fg){Button b=new Button(a);b.setText(s);b.setTextSize(15);b.setTextColor(fg);b.setTypeface(Typeface.create("sans-serif-medium",0));b.setAllCaps(false);b.setMinHeight(dp(a,52));b.setMinimumHeight(dp(a,52));b.setPadding(dp(a,16),dp(a,12),dp(a,16),dp(a,12));b.setStateListAnimator(null);touch(b,bg,16);return b;}
 public static Button primary(Activity a,String s){return button(a,s,ACCENT,INK);}
 public static TextView circleButton(Activity a,String s,int bg,int fg){TextView t=tv(a,s,24,fg,true);t.setGravity(Gravity.CENTER);touch(t,bg,24);t.setMinHeight(dp(a,48));t.setMinWidth(dp(a,48));t.setContentDescription(s.equals("+")?"增加阻力":s.equals("−")?"降低阻力":s);return t;}
 public static LinearLayout col(Activity a){LinearLayout l=new LinearLayout(a);l.setOrientation(1);return l;}
 public static LinearLayout row(Activity a){LinearLayout l=new LinearLayout(a);l.setOrientation(0);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
 public static LinearLayout card(Activity a){LinearLayout l=col(a);surface(l,CARD,22);int p=dp(a,20);l.setPadding(p,p,p,p);return l;}
 public static LinearLayout hero(Activity a,int c1,int c2){LinearLayout l=card(a);l.setBackground(gradient(c1,c2,8));return l;}
 public static LinearLayout root(Activity a){LinearLayout l=col(a);int p=dp(a,24);l.setPadding(p,dp(a,16),p,dp(a,28));return l;}
 public static ScrollView scroll(Activity a,LinearLayout l){ScrollView s=new ScrollView(a);s.setFillViewport(true);s.setClipToPadding(false);s.setVerticalScrollBarEnabled(false);s.setBackgroundColor(BG);s.addView(l,new ScrollView.LayoutParams(-1,-2));return s;}
 public static void gap(Activity a,LinearLayout l,int n){l.addView(new View(a),new LinearLayout.LayoutParams(1,dp(a,n)));}
 public static void add(Activity a,LinearLayout l,View v,int bottom){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(a,bottom);l.addView(v,p);}
 public static void weight(LinearLayout l,View v){l.addView(v,new LinearLayout.LayoutParams(0,-2,1));}
 public static LinearLayout.LayoutParams lp(int w,int h,int gravity){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,h);p.gravity=gravity;return p;}
 public static View divider(Activity a){View v=new View(a);v.setBackgroundColor(DIV);return v;}
 public static GradientDrawable pageBackground(){return round(BG,0);}
 public static LinearLayout topBar(Activity a,String s,Runnable back){LinearLayout l=row(a);l.setPadding(0,0,0,dp(a,20));View b=iconButton(a,"back","返回",back);l.addView(b,new LinearLayout.LayoutParams(dp(a,48),dp(a,48)));TextView t=tv(a,s,17,TEXT,true);t.setGravity(Gravity.CENTER);weight(l,t);l.addView(new View(a),new LinearLayout.LayoutParams(dp(a,48),1));return l;}
 public static View iconButton(Activity a,String kind,String desc,Runnable action){Visuals.Icon v=new Visuals.Icon(a,kind,TEXT);touch(v,CARD,24);v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);v.setContentDescription(desc);v.setOnClickListener(w->action.run());return v;}
 public static LinearLayout section(Activity a,String title,String action,Runnable r){LinearLayout l=row(a);l.setPadding(0,dp(a,24),0,dp(a,14));weight(l,tv(a,title,18,TEXT,true));if(r!=null){Button t=button(a,action,BG,DIM);t.setTextSize(12);t.setPadding(dp(a,12),0,0,0);l.addView(t);}return l;}
 public static LinearLayout metric(Activity a,String label,String value,String unit){LinearLayout l=col(a);l.addView(sub(a,label));gap(a,l,8);LinearLayout r=row(a);r.addView(number(a,value,27,TEXT));TextView u=tv(a,"  "+unit,11,DIM,false);r.addView(u);l.addView(r);return l;}
 public static View nav(Activity a,int selected,java.util.function.IntConsumer choose){LinearLayout shell=col(a);shell.setBackgroundColor(BG);shell.addView(divider(a),new LinearLayout.LayoutParams(-1,dp(a,1)));LinearLayout l=row(a);String[] labels={"骑行","计划","记录"},icons={"home","plan","chart"};for(int i=0;i<3;i++){final int n=i;LinearLayout item=col(a);item.setGravity(Gravity.CENTER);item.setPadding(0,dp(a,10),0,dp(a,10));Visuals.Icon icon=new Visuals.Icon(a,icons[i],i==selected?ACCENT:DIM);item.addView(icon,new LinearLayout.LayoutParams(dp(a,24),dp(a,24)));TextView t=tv(a,labels[i],11,i==selected?ACCENT:DIM,i==selected);t.setPadding(0,dp(a,5),0,0);t.setGravity(Gravity.CENTER);item.addView(t);touch(item,BG,18);item.setContentDescription(labels[i]);item.setOnClickListener(v->choose.accept(n));l.addView(item,new LinearLayout.LayoutParams(0,dp(a,70+Math.max(0,a.getResources().getConfiguration().fontScale-1)*18),1));}shell.addView(l);return shell;}
 public static void screen(Activity a,LinearLayout content,View footer){window(a);LinearLayout frame=col(a);frame.setBackgroundColor(BG);frame.addView(scroll(a,content),new LinearLayout.LayoutParams(-1,0,1));if(footer!=null)frame.addView(footer,new LinearLayout.LayoutParams(-1,-2));a.setContentView(frame);}
 /** Numeric color ramp only: cool at lower readings, redder at higher readings. */
 public static int heartRateColor(int bpm){
  if(bpm<=0)return DIM;
  int[] stops={80,110,140,160,180};
  int[] colors={0xFF8DC9FF,0xFF8FE0BC,0xFFF0D46B,0xFFFFAC6B,0xFFFF6666};
  if(bpm<=stops[0])return colors[0];
  for(int i=1;i<stops.length;i++)if(bpm<=stops[i]){
   float t=(bpm-stops[i-1])/(float)(stops[i]-stops[i-1]);int a=colors[i-1],b=colors[i];
   return Color.rgb(Math.round(Color.red(a)+(Color.red(b)-Color.red(a))*t),Math.round(Color.green(a)+(Color.green(b)-Color.green(a))*t),Math.round(Color.blue(a)+(Color.blue(b)-Color.blue(a))*t));
  }
  return colors[colors.length-1];
 }
 public static int zoneColor(int z){return z<2?BLUE:z==2?ACCENT:z==3?WARN:PINK;}
 public static GradientDrawable segmentBlock(int color,boolean active){return round(active?color:CARD2,2);}
 public static void dialog(Activity a,String title,String message){new AlertDialog.Builder(a).setTitle(title).setMessage(message).setPositiveButton("知道了",null).show();}
 public static String time(int seconds){return String.format(java.util.Locale.US,"%02d:%02d",Math.max(0,seconds)/60,Math.max(0,seconds)%60);}
 public static String val(float f,int digits){return f<0?"—":String.format(java.util.Locale.US,"%."+digits+"f",f);}
 public static void goTab(Activity a,int tab){Intent i=new Intent(a,MainActivity.class);i.putExtra("tab",tab);i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);a.startActivity(i);if(!(a instanceof MainActivity))a.finish();}
}
