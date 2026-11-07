package com.spinfit.bike.app;
import android.content.*;
import android.os.*;
import com.spinfit.bike.ble.*;
import com.spinfit.bike.core.*;
import java.util.*;

/** Single owner of connection, monotonic training clock and durable records. */
public final class BikeSession {
    private static final BikeSession I=new BikeSession();public static BikeSession get(){return I;}private BikeSession(){}
    public interface StateListener{void onState(int state,String message);}
    public interface DataListener{void onData(BikeSource.Sample s);}
    private final List<StateListener> listeners=new ArrayList<>();private DataListener dataListener;
    private BikeSource source;private DemoBike demo;private BleBike ble;private Settings settings;private Context context;
    private SessionEngine engine;private TrainingProgram program;private Metrics last;private BikeSource.Sample sample;
    private BikeSource.WriteResult write;private String device="未连接单车";private boolean demoMode;
    private long sampleAt,lastTick,recordId=-1;private int sentTarget=-1;private TelemetryStats stats;private WorkoutStore store;
    private BleHeartRate heart;
    private boolean dataPaused;private String saveError="";private long manualCommandAt;
    public void init(Context c){if(context!=null)return;context=c.getApplicationContext();settings=new Settings(c);store=new WorkoutStore(c);store.recover();}
    public void init(Settings s){settings=s;}
    public Settings settings(){return settings;}public WorkoutStore store(){return store;}public String saveError(){return saveError;}
    public void addStateListener(StateListener l){if(!listeners.contains(l))listeners.add(l);}public void removeStateListener(StateListener l){listeners.remove(l);}
    public void setDataListener(DataListener l){dataListener=l;}
    private void fire(int s,String m){for(StateListener l:new ArrayList<>(listeners))l.onState(s,m);}
    public String deviceName(){return device;}public boolean isDemo(){return demoMode;}public BikeSource source(){return source;}public BleBike ble(){return ble;}public DemoBike demoBike(){return demo;}
    public int resistanceMax(){return ble!=null?ble.resistanceMaximum():settings.resistanceMax();}
    public int resistanceMin(){return ble!=null?ble.resistanceMinimum():0;}
    public SessionEngine engine(){return engine;}public TrainingProgram program(){return program;}public Metrics last(){return last;}public void setLast(Metrics m){last=m;}
    public BikeSource.Sample lastSample(){
        if(sample==null)return null;
        BikeSource.Sample merged=sample.copy();
        if(heart!=null&&heart.bpm()>0){merged.heartRate=heart.bpm();merged.hrValid=true;merged.hrExternal=true;merged.hrSource=heart.name();}
        else merged.hrSource=merged.hrValid?(merged.simulated?"演示单车":"单车"):"";
        return merged;
    }
    public BleHeartRate heart(){return heart;}
    public int currentHeartRate(){if(heart!=null&&heart.bpm()>0)return heart.bpm();return fresh()&&sample.hrValid?sample.heartRate:-1;}
    public String heartSource(){if(heart!=null&&heart.bpm()>0)return heart.name();return fresh()&&sample.hrValid?(sample.simulated?"演示单车":"单车"):"未连接心率设备";}
    public void connectHeart(Context c,String address,String name){init(c);if(heart!=null&&heart.samePeer(address,name)&&heart.busy())return;disconnectHeart();heart=new BleHeartRate(context,address,name,()->{if(heart!=null&&heart.connected()&&(!heart.address().equals(settings.heartAddress())||!heart.name().equals(settings.heartName())))settings.rememberHeart(heart.address(),heart.name());fire(20,"心率设备状态已更新");});heart.connect();}
    public void disconnectHeart(){if(heart!=null){heart.disconnect();heart=null;}fire(20,"心率设备已断开");}public BikeSource.WriteResult lastWrite(){return write;}public void recordWrite(BikeSource.WriteResult r){write=r;}
    public long recordId(){return recordId;}public TelemetryStats stats(){return stats;}public boolean active(){return engine!=null&&engine.isRunning();}
    public boolean isConnected(){return source!=null&&source.isConnected();}
    public boolean fresh(){return isConnected()&&sample!=null&&SystemClock.elapsedRealtime()-sampleAt<4500;}
    public List<String> logs(){return ble!=null?ble.logs():demo!=null?demo.logs():Collections.emptyList();}public void log(String s){fire(-1,s);}
    private void attach(){source.setListener(new BikeSource.Listener(){public void onState(int st,String msg){fire(st,msg);}public void onLog(String l){fire(-1,l);}public void onData(BikeSource.Sample s){sample=s;sampleAt=SystemClock.elapsedRealtime();if(dataListener!=null)dataListener.onData(s);}});source.connect();}
    public void connectDemo(){if(active())return;disconnect();demoMode=true;demo=new DemoBike();demo.configure(settings.ftpW(),settings.maxHr(),settings.resistanceMax());source=demo;device="演示单车";attach();}
    public void connectBle(Context c,String address,String name,int type){if(active())return;init(c);disconnect();demoMode=false;ble=new BleBike(c.getApplicationContext(),address,name,type);source=ble;device=name;settings.rememberBike(address,name,type);attach();}
    public boolean reconnectCurrentBike(){
        if(!active()||!engine.isPaused()||demoMode||source==null)return false;
        String address=source.address();int type=source.type();
        source.setListener(null);source.disconnect();
        ble=new BleBike(context,address,device,type);source=ble;sample=null;sampleAt=0;sentTarget=-1;write=null;
        attach();return true;
    }
    public void disconnect(){if(active())return;if(source!=null)source.disconnect();source=null;demo=null;ble=null;sample=null;write=null;device="未连接单车";fire(BikeSource.STATE_DISCONNECTED,"单车已断开");}
    public void startProgram(TrainingProgram p){
        if(active()||!isConnected())return;
        manualCommandAt=0;write=null;if(ble!=null)ble.resetResistanceCommand();program=p;engine=new SessionEngine(p,settings.ftpW(),settings.maxHr(),settings.weightKg(),resistanceMax());
        engine.setTimeScale(demoMode?settings.timeScale():1);engine.setAutoResistance(!p.freeRide&&settings.autoResistance());
        engine.setManualResistance(sample!=null&&sample.resistanceValid?Math.max(resistanceMin(),Math.min(resistanceMax(),sample.resistance)):0);
        stats=new TelemetryStats();if(sample!=null)stats.counters(sample.distanceValid?sample.distanceKm:-1,sample.caloriesValid?sample.calories:-1,false);
        recordId=store.begin(p,device,demoMode);saveError="";sentTarget=-1;dataPaused=false;engine.start();last=engine.snapshot();lastTick=SystemClock.elapsedRealtime();
        source.startWorkout();applyAuto();
        if(heart==null&&!settings.heartAddress().isEmpty())connectHeart(context,settings.heartAddress(),settings.heartName());
        Intent i=new Intent(context,WorkoutService.class);try{if(Build.VERSION.SDK_INT>=26)context.startForegroundService(i);else context.startService(i);}catch(RuntimeException e){engine.stop();store.checkpoint(recordId,engine.snapshot(),stats,"interrupted",null);source.stopWorkout();throw e;}
    }
    public void tick(){
        if(!active())return;long now=SystemClock.elapsedRealtime();float dt=Math.max(0,Math.min(5,(now-lastTick)/1000f));lastTick=now;
        boolean valid=fresh();if(!valid){engine.pause();dataPaused=true;}
        if(ble!=null&&engine.isAutoResistance()&&ble.resistanceOverridden()){engine.setAutoResistance(false);if(valid&&sample.resistanceValid)engine.setManualResistance(sample.resistance);ble.cancelPendingResistance();sentTarget=-1;fire(21,"旋钮已接管阻力");}
        boolean accumulating=!engine.isPaused()&&valid;
        BikeSource.Sample s=valid?lastSample():null;
        if(s!=null){stats.counters(s.distanceValid?s.distanceKm:-1,s.caloriesValid?s.calories:-1,accumulating);}
        if(accumulating){
            if(demo!=null){Segment seg=engine.currentSegment();if(seg!=null)demo.setTarget(seg.targetPowerPct,seg.targetCadence);}
            int before=engine.snapshot().elapsedSec;
            last=engine.advance(dt,s.powerValid?s.powerW:0,s.cadenceValid?s.cadenceRpm:0,s.hrValid?s.heartRate:0,s.resistanceValid?s.resistance:0);
            float used=dt*engine.getTimeScale();if(!program.freeRide)used=Math.min(used,Math.max(0,program.totalSeconds()-before));
            stats.add(used,s.powerValid?s.powerW:-1,s.cadenceValid?s.cadenceRpm:-1,s.hrValid?s.heartRate:-1);
            stats.addSpeed(used,s.speedValid?s.speedKph:-1);
            applyAuto();
            checkpoint(engine.isFinished()?"completed":"active",s);
        }
        last=engine.snapshot();
        if(engine.isFinished()){source.stopWorkout();context.stopService(new Intent(context,WorkoutService.class));fire(10,"训练已保存");}
    }
    private void checkpoint(String status,BikeSource.Sample s){try{store.checkpoint(recordId,engine.snapshot(),stats,status,s);saveError="";}catch(RuntimeException e){saveError="记录保存失败，请检查手机存储空间";engine.pause();fire(11,saveError);}}
    public void applyAuto(){if(engine==null||source==null||engine.isPaused()||!engine.isAutoResistance())return;int t=Math.round(engine.currentTargetResistance());if(t!=sentTarget||(ble!=null&&ble.resistanceNeedsRetry(t))){write=source.writeResistance(t,resistanceMax());if(write!=null&&write.sent)sentTarget=t;}}
    public void setAuto(boolean b){if(!active())return;manualCommandAt=0;if(ble!=null)ble.resetResistanceCommand();if(!b)engine.setManualResistance(Math.round(engine.currentTargetResistance()));engine.setAutoResistance(b);sentTarget=-1;if(b)applyAuto();}
    public void adjustBy(int delta){if(!active()||engine.isPaused())return;long now=SystemClock.elapsedRealtime();int actual=fresh()&&sample.resistanceValid?sample.resistance:-1;int target=ResistanceScale.step(actual,Math.round(engine.currentTargetResistance()),delta,resistanceMin(),resistanceMax(),manualCommandAt>0&&now-manualCommandAt<1500);adjust(target);manualCommandAt=now;}
    public void adjust(int level){if(!active()||engine.isPaused())return;engine.setAutoResistance(false);engine.setManualResistance(Math.max(resistanceMin(),Math.min(resistanceMax(),level)));write=source.writeResistance(engine.getManualResistance(),resistanceMax());}
    public void pause(){if(!active())return;tick();if(!active())return;engine.pause();source.pauseWorkout();checkpoint("active",null);}
    public boolean resume(){if(!active()||!fresh())return false;engine.resume();dataPaused=false;lastTick=SystemClock.elapsedRealtime();if(sample!=null)stats.counters(sample.distanceValid?sample.distanceKm:-1,sample.caloriesValid?sample.calories:-1,false);source.startWorkout();sentTarget=-1;applyAuto();return true;}
    public boolean dataPaused(){return dataPaused;}
    public long finishWorkout(){if(engine==null)return recordId;if(active()){tick();engine.stop();if(source!=null)source.stopWorkout();checkpoint("saved",null);}else if(!saveError.isEmpty())checkpoint("saved",null);if(saveError.isEmpty())context.stopService(new Intent(context,WorkoutService.class));return recordId;}
}
