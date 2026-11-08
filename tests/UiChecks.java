package com.spinfit.bike.tests;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import com.spinfit.bike.app.*;
import com.spinfit.bike.core.*;
import com.spinfit.bike.ui.*;
import java.io.*;
import java.lang.reflect.*;

/** Opt-in UI acceptance. Only isolated demo settings/records are used; system settings are restored. */
public final class UiChecks extends Instrumentation {
    private int checks;private BikeSession owner;private WorkoutStore store;private Activity activity;
    private final Bundle result=new Bundle();
    private static final String PREF="spinfit_ui_checks",DB="spinfit_ui_checks.db";
    private String font,rotation,accelerometer,size,windowAnim,transitionAnim,durationAnim;
    private void check(boolean condition,String message){if(!condition)throw new AssertionError(message);checks++;}
    private void field(Object object,String name,Object value)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);f.set(object,value);}
    private String shell(String command)throws Exception{
        ParcelFileDescriptor fd=getUiAutomation().executeShellCommand(command);
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){byte[] b=new byte[1024];for(int n;(n=in.read(b))!=-1;)out.write(b,0,n);}
        return out.toString("UTF-8").trim();
    }
    private void restore(String namespace,String key,String value)throws Exception{
        if(value==null)return;shell("null".equals(value)?"settings delete "+namespace+" "+key:"settings put "+namespace+" "+key+" "+value);
    }
    private TextView text(View view,String expected){
        if(view instanceof TextView&&expected.contentEquals(((TextView)view).getText()))return (TextView)view;
        if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++){TextView found=text(g.getChildAt(i),expected);if(found!=null)return found;}}
        return null;
    }
    private int progressCount(View view){int n=view instanceof ProgressBar?1:0;if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++)n+=progressCount(g.getChildAt(i));}return n;}
    private void noHorizontalOverflow(View view){
        if(view instanceof TextView&&view.getVisibility()==View.VISIBLE&&view.getWidth()>0){
            TextView t=(TextView)view;android.text.Layout layout=t.getLayout();
            if(layout!=null)for(int i=0;i<layout.getLineCount();i++)check(layout.getLineWidth(i)<=t.getWidth()-t.getCompoundPaddingLeft()-t.getCompoundPaddingRight()+3,"text fits width: "+t.getText());
        }
        if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++)noHorizontalOverflow(g.getChildAt(i));}
    }
    private Activity openRide()throws Exception{
        if(activity==null){shell("am start -W -n com.spinfit.bike/.ui.MainActivity");Thread.sleep(350);}
        ActivityMonitor monitor=addMonitor(RideActivity.class.getName(),null,false);
        runOnMainSync(()->getTargetContext().startActivity(new Intent(getTargetContext(),RideActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK)));
        Activity a=waitForMonitorWithTimeout(monitor,5000);removeMonitor(monitor);
        if(a==null)throw new AssertionError("ride activity launch");Thread.sleep(850);activity=a;return a;
    }
    private void capture(String name)throws Exception{
        File file=new File(getTargetContext().getExternalFilesDir("validation"),"ui25-"+name+".png");
        Bitmap bitmap=getUiAutomation().takeScreenshot();check(bitmap!=null,"screenshot "+name);
        try(FileOutputStream out=new FileOutputStream(file)){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
        result.putString(name,file.getAbsolutePath());Bundle progress=new Bundle();progress.putString("ui_case",name);sendStatus(0,progress);
    }
    @Override public void onCreate(Bundle b){super.onCreate(b);start();}
    @Override public void onStart(){int code=-1;try{
        font=shell("settings get system font_scale");rotation=shell("settings get system user_rotation");accelerometer=shell("settings get system accelerometer_rotation");size=shell("wm size");
        windowAnim=shell("settings get global window_animation_scale");transitionAnim=shell("settings get global transition_animation_scale");durationAnim=shell("settings get global animator_duration_scale");
        Context target=getTargetContext();target.deleteDatabase(DB);target.deleteSharedPreferences(PREF);store=new WorkoutStore(target,DB);
        Settings prefs=new Settings(new ContextWrapper(target){@Override public android.content.SharedPreferences getSharedPreferences(String n,int mode){return getBaseContext().getSharedPreferences(PREF,mode);}});
        prefs.setResistanceMax(100);prefs.setTimeScale(1);prefs.setAutoResistance(true);
        owner=BikeSession.get();owner.init(prefs);field(owner,"context",target);field(owner,"store",store);
        shell("settings put system accelerometer_rotation 0");shell("settings put system user_rotation 0");shell("settings put system font_scale 1.0");
        runOnMainSync(()->owner.connectDemo());Thread.sleep(1000);
        runOnMainSync(()->{owner.startProgram(ProgramLibrary.byId("hiit30"));owner.engine().advance(420,200,90,150,15);owner.applyAuto();});Thread.sleep(1500);
        Activity a=openRide();
        runOnMainSync(()->{
            View root=activity.getWindow().getDecorView();TextView resistance=text(root,"阻力控制"),phase=text(root,owner.engine().currentSegment().name);
            check(resistance!=null&&phase!=null,"HIIT and resistance are shown");int[] r=new int[2],p=new int[2];resistance.getLocationOnScreen(r);phase.getLocationOnScreen(p);check(r[1]<p[1],"resistance precedes phase context");
            TextView plus=text(root,"+");Rect visible=new Rect();check(plus!=null&&plus.getGlobalVisibleRect(visible)&&visible.height()>=Ui.dp(activity,48),"resistance control is visible and touchable without scrolling");
            noHorizontalOverflow(root);
        });capture("portrait");
        shell("settings put global window_animation_scale 0");shell("settings put global transition_animation_scale 0");shell("settings put global animator_duration_scale 0");
        shell("settings put system font_scale 2.0");Thread.sleep(650);openRide();runOnMainSync(()->{noHorizontalOverflow(activity.getWindow().getDecorView());check(text(activity.getWindow().getDecorView(),"结束").getLineCount()==1,"end button stays on one line at maximum text size");});capture("large-text");
        shell("settings put system font_scale 1.0");shell("settings put system user_rotation 1");Thread.sleep(650);openRide();runOnMainSync(()->{View root=activity.getWindow().getDecorView();noHorizontalOverflow(root);Rect bounds=new Rect();check(text(root,"+").getGlobalVisibleRect(bounds)&&bounds.height()>=Ui.dp(activity,48),"landscape resistance remains visible without scrolling");});capture("landscape");
        shell("settings put system user_rotation 0");shell("wm size 1080x1920");Thread.sleep(650);openRide();runOnMainSync(()->noHorizontalOverflow(activity.getWindow().getDecorView()));capture("small-phone");
        shell("wm size reset");Thread.sleep(600);
        runOnMainSync(()->{owner.finishWorkout();owner.startProgram(ProgramLibrary.byId("free"));});openRide();runOnMainSync(()->check(progressCount(activity.getWindow().getDecorView())==0,"free ride has no empty phase card"));capture("free-ride");
        result.putString("result","PASS: "+checks+" UI assertions (portrait, large text, landscape, small phone, free ride)");
    }catch(Throwable error){code=0;result.putString("failure",error.toString());}
    finally{
        try{if(owner!=null)runOnMainSync(()->{if(owner.active())owner.finishWorkout();owner.disconnect();if(activity!=null)activity.finish();});}catch(Throwable ignored){}
        try{restore("system","font_scale",font);restore("system","user_rotation",rotation);restore("system","accelerometer_rotation",accelerometer);
            restore("global","window_animation_scale",windowAnim);restore("global","transition_animation_scale",transitionAnim);restore("global","animator_duration_scale",durationAnim);
            if(size!=null){String marker="Override size: ";shell(size.contains(marker)?"wm size "+size.substring(size.indexOf(marker)+marker.length()).trim():"wm size reset");}
        }catch(Throwable error){code=0;result.putString("restore_failure",error.toString());}
        if(store!=null)store.close();getTargetContext().deleteDatabase(DB);getTargetContext().deleteSharedPreferences(PREF);
    }
    finish(code,result);}
}
