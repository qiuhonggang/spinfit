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
    private void ui(Runnable action){
        final Throwable[] failure=new Throwable[1];
        runOnMainSync(()->{try{action.run();}catch(Throwable error){failure[0]=error;}});
        if(failure[0]!=null)throw new AssertionError("UI action failed",failure[0]);
    }
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
        ui(()->getTargetContext().startActivity(new Intent(getTargetContext(),RideActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK)));
        Activity a=waitForMonitorWithTimeout(monitor,5000);removeMonitor(monitor);
        if(a==null)throw new AssertionError("ride activity launch");Thread.sleep(850);activity=a;return a;
    }
    private Activity openMain()throws Exception{
        if(activity==null){shell("am start -W -n com.spinfit.bike/.ui.MainActivity");Thread.sleep(350);}
        ActivityMonitor monitor=addMonitor(MainActivity.class.getName(),null,false);
        ui(()->getTargetContext().startActivity(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK)));
        Activity a=waitForMonitorWithTimeout(monitor,5000);removeMonitor(monitor);
        check(a!=null,"home activity launch");Thread.sleep(650);activity=a;return a;
    }
    private Dialog picker()throws Exception{Field f=MainActivity.class.getDeclaredField("coursePicker");f.setAccessible(true);return (Dialog)f.get(activity);}
    private void openPicker(){TextView start=text(activity.getWindow().getDecorView(),"开始骑行  →");check(start!=null,"home start button");start.performClick();}
    private void checkSheet()throws Exception{
        View root=picker().getWindow().getDecorView();noHorizontalOverflow(root);
        TextView action=text(root,"查看课程  →");Rect bounds=new Rect();
        check(action!=null&&action.getGlobalVisibleRect(bounds)&&bounds.height()>=Ui.dp(activity,48),"fixed sheet action visible at current display settings");
        check(action.getWidth()>=Ui.dp(activity,48),"sheet action touch width");
    }
    private void checkSheetOnMain(){try{checkSheet();}catch(Exception error){throw new RuntimeException(error);}}
    private void nextCourse(String expected)throws Exception{
        ActivityMonitor monitor=addMonitor(ProgramDetailActivity.class.getName(),null,false);
        ui(()->{try{text(picker().getWindow().getDecorView(),"查看课程  →").performClick();}catch(Exception e){throw new RuntimeException(e);}});
        Activity detail=waitForMonitorWithTimeout(monitor,5000);removeMonitor(monitor);
        check(detail!=null&&expected.equals(detail.getIntent().getStringExtra("id")),"chooser routes selected course to details: "+expected);activity=detail;
    }
    private void capture(String name)throws Exception{
        File file=new File(getTargetContext().getExternalFilesDir("validation"),"ui27-"+name+".png");
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
        openMain();ui(()->{openPicker();try{View root=picker().getWindow().getDecorView();for(TrainingProgram p:ProgramLibrary.all())check(root.findViewWithTag(p.id)!=null,"picker lists course "+p.id);check(root.findViewWithTag("free").isSelected(),"new picker defaults to free ride");}catch(Exception error){throw new RuntimeException(error);}});
        Thread.sleep(350);ui(this::checkSheetOnMain);capture("course-picker");
        ui(()->{try{View card=picker().getWindow().getDecorView().findViewWithTag("hiit30");card.requestRectangleOnScreen(new Rect(0,0,card.getWidth(),card.getHeight()),true);}catch(Exception error){throw new RuntimeException(error);}});Thread.sleep(250);
        ui(()->{try{View root=picker().getWindow().getDecorView(),card=root.findViewWithTag("hiit30");Rect bounds=new Rect();check(card.getGlobalVisibleRect(bounds)&&bounds.height()==card.getHeight(),"last course is reachable in scroll area above fixed footer");card.performClick();check(card.isSelected()&&!root.findViewWithTag("free").isSelected(),"card selection is exclusive");check(text(root,"已选 · HIIT 30 分钟")!=null,"footer reflects selected course");}catch(Exception error){throw new RuntimeException(error);}});Thread.sleep(150);capture("picker-selected");
        ActivityMonitor recreateMonitor=addMonitor(MainActivity.class.getName(),null,false);ui(()->activity.recreate());Activity recreated=waitForMonitorWithTimeout(recreateMonitor,5000);removeMonitor(recreateMonitor);check(recreated!=null,"home recreated while sheet open");activity=recreated;Thread.sleep(650);
        ui(()->{try{check(picker().isShowing()&&picker().getWindow().getDecorView().findViewWithTag("hiit30").isSelected(),"open sheet preserves selection across activity recreation");}catch(Exception error){throw new RuntimeException(error);}});nextCourse("hiit30");
        openMain();ui(()->{openPicker();try{check(picker().getWindow().getDecorView().findViewWithTag("free").isSelected(),"fresh picker does not retain the previous HIIT selection");picker().dismiss();openPicker();check(picker().getWindow().getDecorView().findViewWithTag("free").isSelected(),"cancel and reopen defaults to free ride");}catch(Exception error){throw new RuntimeException(error);}});nextCourse("free");
        shell("settings put global window_animation_scale 0");shell("settings put global transition_animation_scale 0");shell("settings put global animator_duration_scale 0");
        shell("settings put system font_scale 2.0");Thread.sleep(500);openMain();ui(this::openPicker);Thread.sleep(350);ui(this::checkSheetOnMain);capture("picker-large-text");ui(()->{try{picker().dismiss();}catch(Exception e){throw new RuntimeException(e);}});
        shell("settings put system font_scale 1.0");shell("settings put system user_rotation 1");Thread.sleep(500);openMain();ui(this::openPicker);Thread.sleep(350);ui(this::checkSheetOnMain);capture("picker-landscape");ui(()->{try{picker().dismiss();}catch(Exception e){throw new RuntimeException(e);}});
        shell("settings put system user_rotation 0");shell("wm size 1080x1920");Thread.sleep(500);openMain();ui(this::openPicker);Thread.sleep(350);ui(this::checkSheetOnMain);capture("picker-small-phone");ui(()->{try{picker().dismiss();}catch(Exception e){throw new RuntimeException(e);}});
        shell("wm size reset");Thread.sleep(500);
        ui(()->owner.connectDemo());Thread.sleep(1000);
        ui(()->{owner.startProgram(ProgramLibrary.byId("hiit30"));owner.engine().advance(420,200,90,150,15);owner.applyAuto();});Thread.sleep(1500);
        openMain();ActivityMonitor resumeMonitor=addMonitor(RideActivity.class.getName(),null,false);
        ui(()->{TextView resume=text(activity.getWindow().getDecorView(),"继续我的训练  →");check(resume!=null,"active ride shows resume instead of course picker");resume.performClick();});
        Activity resumed=waitForMonitorWithTimeout(resumeMonitor,5000);removeMonitor(resumeMonitor);check(resumed!=null,"home resume opens active ride directly");activity=resumed;
        Thread.sleep(1000);Activity a=resumed;
        ui(()->{
            View root=activity.getWindow().getDecorView();TextView resistance=text(root,"阻力控制"),phase=text(root,owner.engine().currentSegment().name);
            check(resistance!=null&&phase!=null,"HIIT and resistance are shown");int[] r=new int[2],p=new int[2];resistance.getLocationOnScreen(r);phase.getLocationOnScreen(p);check(r[1]<p[1],"resistance precedes phase context: "+r[1]+" vs "+p[1]);
            TextView plus=text(root,"+");Rect visible=new Rect();check(plus!=null&&plus.getGlobalVisibleRect(visible)&&visible.height()>=Ui.dp(activity,48),"resistance control is visible and touchable without scrolling");
            noHorizontalOverflow(root);
        });capture("portrait");
        shell("settings put global window_animation_scale 0");shell("settings put global transition_animation_scale 0");shell("settings put global animator_duration_scale 0");
        shell("settings put system font_scale 2.0");Thread.sleep(650);openRide();ui(()->{noHorizontalOverflow(activity.getWindow().getDecorView());check(text(activity.getWindow().getDecorView(),"结束").getLineCount()==1,"end button stays on one line at maximum text size");});capture("large-text");
        shell("settings put system font_scale 1.0");shell("settings put system user_rotation 1");Thread.sleep(650);openRide();ui(()->{View root=activity.getWindow().getDecorView();noHorizontalOverflow(root);Rect bounds=new Rect();check(text(root,"+").getGlobalVisibleRect(bounds)&&bounds.height()>=Ui.dp(activity,48),"landscape resistance remains visible without scrolling");});capture("landscape");
        shell("settings put system user_rotation 0");shell("wm size 1080x1920");Thread.sleep(650);openRide();ui(()->noHorizontalOverflow(activity.getWindow().getDecorView()));capture("small-phone");
        shell("wm size reset");Thread.sleep(600);
        ui(()->{owner.finishWorkout();owner.startProgram(ProgramLibrary.byId("free"));});openRide();ui(()->check(progressCount(activity.getWindow().getDecorView())==0,"free ride has no empty phase card"));capture("free-ride");
        result.putString("result","PASS: "+checks+" UI assertions (portrait, large text, landscape, small phone, free ride)");
    }catch(Throwable error){code=0;result.putString("failure",android.util.Log.getStackTraceString(error));}
    finally{
        try{if(owner!=null)ui(()->{if(owner.active())owner.finishWorkout();owner.disconnect();if(activity!=null)activity.finish();});}catch(Throwable ignored){}
        try{restore("system","font_scale",font);restore("system","user_rotation",rotation);restore("system","accelerometer_rotation",accelerometer);
            restore("global","window_animation_scale",windowAnim);restore("global","transition_animation_scale",transitionAnim);restore("global","animator_duration_scale",durationAnim);
            if(size!=null){String marker="Override size: ";shell(size.contains(marker)?"wm size "+size.substring(size.indexOf(marker)+marker.length()).trim():"wm size reset");}
        }catch(Throwable error){code=0;result.putString("restore_failure",error.toString());}
        if(store!=null)store.close();getTargetContext().deleteDatabase(DB);getTargetContext().deleteSharedPreferences(PREF);
    }
    finish(code,result);}
}
