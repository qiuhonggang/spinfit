package com.spinfit.bike.ui;

import android.app.*;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import com.spinfit.bike.core.*;
import java.util.*;
import java.util.function.IntConsumer;

/** A native course sheet: one choice, visible training profiles and a fixed next action. */
public final class CoursePicker extends Dialog {
    private final Activity activity;
    private final List<TrainingProgram> courses;
    private final IntConsumer onSelect,onConfirm;
    private final List<LinearLayout> cards=new ArrayList<>();
    private final List<View> checks=new ArrayList<>();
    private TextView summary,mode;
    private int selected;private boolean wide;

    public CoursePicker(Activity activity,List<TrainingProgram> courses,int selected,IntConsumer onSelect,IntConsumer onConfirm){
        super(activity);this.activity=activity;this.courses=courses;this.selected=selected;this.onSelect=onSelect;this.onConfirm=onConfirm;
        requestWindowFeature(Window.FEATURE_NO_TITLE);setCanceledOnTouchOutside(true);
    }
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        android.content.res.Configuration config=activity.getResources().getConfiguration();wide=config.screenWidthDp>=600&&config.screenWidthDp>config.screenHeightDp;
        LinearLayout sheet=Ui.col(activity);
        GradientDrawable background=new GradientDrawable();background.setColor(Ui.BG);
        float radius=Ui.dp(activity,28);background.setCornerRadii(new float[]{radius,radius,radius,radius,0,0,0,0});sheet.setBackground(background);sheet.setClipToOutline(true);
        if(Build.VERSION.SDK_INT>=28)sheet.setAccessibilityPaneTitle("选择骑行课程");
        LinearLayout header=Ui.row(activity);header.setPadding(Ui.dp(activity,20),Ui.dp(activity,wide?8:16),Ui.dp(activity,16),Ui.dp(activity,wide?8:16));
        TextView title=Ui.tv(activity,"选择课程",24,Ui.TEXT,true);if(Build.VERSION.SDK_INT>=28)title.setAccessibilityHeading(true);Ui.weight(header,title);
        header.addView(Ui.iconButton(activity,"close","关闭课程选择",this::dismiss),new LinearLayout.LayoutParams(Ui.dp(activity,48),Ui.dp(activity,48)));sheet.addView(header);

        LinearLayout content=Ui.col(activity);content.setPadding(Ui.dp(activity,20),0,Ui.dp(activity,20),Ui.dp(activity,20));
        content.addView(card(0,true),new LinearLayout.LayoutParams(-1,-2));
        TextView section=Ui.tv(activity,"跟课训练",14,Ui.DIM,true);section.setPadding(0,Ui.dp(activity,24),0,Ui.dp(activity,12));content.addView(section);
        boolean columns=activity.getResources().getConfiguration().screenWidthDp>=360&&activity.getResources().getConfiguration().fontScale<=1.35f;
        for(int i=1;i<courses.size();){
            LinearLayout row=Ui.row(activity);row.setGravity(Gravity.TOP);
            row.addView(card(i++,false),new LinearLayout.LayoutParams(0,-1,1));
            if(columns){
                if(i<courses.size()){LinearLayout.LayoutParams right=new LinearLayout.LayoutParams(0,-1,1);right.leftMargin=Ui.dp(activity,12);row.addView(card(i++,false),right);}
                else{LinearLayout.LayoutParams blank=new LinearLayout.LayoutParams(0,1,1);blank.leftMargin=Ui.dp(activity,12);row.addView(new View(activity),blank);}
            }
            Ui.add(activity,content,row,12);
        }
        ScrollView scroll=new ScrollView(activity);scroll.setClipToPadding(false);scroll.setVerticalScrollBarEnabled(false);scroll.setFillViewport(false);scroll.addView(content,new ScrollView.LayoutParams(-1,-2));sheet.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        sheet.addView(Ui.divider(activity),new LinearLayout.LayoutParams(-1,Ui.dp(activity,1)));
        LinearLayout footer=wide?Ui.row(activity):Ui.col(activity);footer.setPadding(Ui.dp(activity,20),Ui.dp(activity,12),Ui.dp(activity,20),Ui.dp(activity,16));
        LinearLayout information=Ui.col(activity);summary=Ui.tv(activity,"",14,Ui.TEXT,true);information.addView(summary);Ui.gap(activity,information,4);
        mode=Ui.tv(activity,"",12,Ui.DIM,false);information.addView(mode);
        if(wide)Ui.weight(footer,information);else{footer.addView(information);Ui.gap(activity,footer,12);}
        Button next=Ui.primary(activity,"查看课程  →");next.setOnClickListener(v->{int choice=selected;dismiss();onConfirm.accept(choice);});
        LinearLayout.LayoutParams action=new LinearLayout.LayoutParams(wide?Ui.dp(activity,184):-1,-2);if(wide)action.leftMargin=Ui.dp(activity,16);footer.addView(next,action);sheet.addView(footer);
        setContentView(sheet);select(selected,false);
        if(selected>0)sheet.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener(){
            @Override public void onGlobalLayout(){View card=cards.get(selected);if(card.getWidth()==0)return;sheet.getViewTreeObserver().removeOnGlobalLayoutListener(this);card.requestRectangleOnScreen(new android.graphics.Rect(0,0,card.getWidth(),card.getHeight()),true);}
        });
        Window w=getWindow();if(w!=null){w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams p=w.getAttributes();p.gravity=Gravity.BOTTOM;p.dimAmount=.6f;w.setAttributes(p);w.setWindowAnimations(com.spinfit.bike.R.style.CourseSheetMotion);w.setNavigationBarColor(Ui.BG);w.getDecorView().setSystemUiVisibility(0);}
    }
    @Override protected void onStart(){
        super.onStart();Window w=getWindow();if(w==null)return;
        android.util.DisplayMetrics display=activity.getResources().getDisplayMetrics();
        int available=Ui.dp(activity,activity.getResources().getConfiguration().screenHeightDp);
        w.setLayout(Math.min(display.widthPixels,Ui.dp(activity,680)),Math.round(available*.94f));
    }
    private LinearLayout card(int index,boolean free){
        TrainingProgram program=courses.get(index);LinearLayout card=Ui.col(activity);int pad=Ui.dp(activity,16);card.setPadding(pad,pad,pad,pad);card.setTag(program.id);
        LinearLayout heading=Ui.row(activity);heading.setGravity(Gravity.TOP);
        if(free){Visuals.Icon bike=new Visuals.Icon(activity,"bike",Ui.ACCENT);Ui.surface(bike,Ui.CARD2,16);LinearLayout.LayoutParams icon=new LinearLayout.LayoutParams(Ui.dp(activity,48),Ui.dp(activity,48));icon.rightMargin=Ui.dp(activity,12);heading.addView(bike,icon);}
        LinearLayout names=Ui.col(activity);TextView name=Ui.tv(activity,program.name,free?19:16,Ui.TEXT,true);
        if(!free&&!wide)name.setMinHeight(Ui.dp(activity,40));names.addView(name);
        if(free){Ui.gap(activity,names,8);names.addView(Ui.tv(activity,"不限时 · 手动调阻",12,Ui.DIM,false));}
        Ui.weight(heading,names);
        Visuals.Icon check=new Visuals.Icon(activity,"check",Ui.INK);Ui.surface(check,Ui.ACCENT,12);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(Ui.dp(activity,24),Ui.dp(activity,24));cp.leftMargin=Ui.dp(activity,4);heading.addView(check,cp);checks.add(check);card.addView(heading);
        if(!free){
            Ui.gap(activity,card,8);LinearLayout info=Ui.row(activity);Ui.weight(info,Ui.tv(activity,program.totalSeconds()/60+" 分钟",13,Ui.TEXT,true));info.addView(Ui.tv(activity,ProgramLibrary.effort(program),12,program.segments.length>0?Ui.zoneColor(program.segments[Math.min(1,program.segments.length-1)].zone):Ui.DIM,false));card.addView(info);
            Ui.gap(activity,card,12);Visuals.Profile profile=new Visuals.Profile(activity,program);profile.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);card.addView(profile,new LinearLayout.LayoutParams(-1,Ui.dp(activity,28)));
        }
        for(int i=0;i<card.getChildCount();i++)card.getChildAt(i).setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        card.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        card.setAccessibilityDelegate(new View.AccessibilityDelegate(){@Override public void onInitializeAccessibilityNodeInfo(View host,AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(host,info);info.setClassName(RadioButton.class.getName());info.setCheckable(true);info.setChecked(host.isSelected());}});
        card.setOnClickListener(v->select(index,true));cards.add(card);return card;
    }
    private void select(int index,boolean notify){
        selected=index;
        for(int i=0;i<cards.size();i++){
            boolean active=i==index;LinearLayout card=cards.get(i);TrainingProgram course=courses.get(i);
            GradientDrawable fill=new GradientDrawable();fill.setColor(active?Color.argb(28,Color.red(Ui.ACCENT),Color.green(Ui.ACCENT),Color.blue(Ui.ACCENT)):Ui.CARD);fill.setCornerRadius(Ui.dp(activity,20));fill.setStroke(Ui.dp(activity,active?1.5f:1),active?Ui.ACCENT:Ui.DIV);
            card.setBackground(new RippleDrawable(ColorStateList.valueOf(0x25FFFFFF),fill,null));card.setClickable(true);card.setFocusable(true);card.setSelected(active);checks.get(i).setVisibility(active?View.VISIBLE:View.INVISIBLE);
            card.setContentDescription(course.name+"，"+(course.freeRide?"不限时":course.totalSeconds()/60+"分钟，"+ProgramLibrary.effort(course))+"，"+course.resistanceModeLabel()+(active?"，已选择":""));
        }
        TrainingProgram course=courses.get(index);summary.setText("已选 · "+course.name);
        mode.setText(course.freeRide?"不限时 · 保留当前阻力":course.totalSeconds()/60+" 分钟 · "+(course.resistanceMode()==TrainingProgram.ResistanceMode.FIXED?"开始时设定固定阻力":"自动分段调阻"));
        if(notify)onSelect.accept(index);
    }
}
