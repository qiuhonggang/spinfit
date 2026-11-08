package com.spinfit.bike.tests;
import android.app.Instrumentation;import android.os.Bundle;import com.spinfit.bike.app.*;import com.spinfit.bike.core.*;import com.spinfit.bike.ble.*;import java.lang.reflect.*;import java.util.*;
public class DeviceChecks extends Instrumentation{
 static void field(Object object,String name,Object value)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);f.set(object,value);}
 int count;void check(boolean b,String message){if(!b)throw new AssertionError(message);count++;}
 void near(float a,float b,String msg){check(Math.abs(a-b)<.02,msg+" "+a);}
 @Override public void onCreate(Bundle b){super.onCreate(b);start();}
 @Override public void onStart(){Bundle result=new Bundle();WorkoutStore store=null;try{
  String db="spinfit_isolated_tests.db";getTargetContext().deleteDatabase(db);store=new WorkoutStore(getTargetContext(),db);
  TrainingProgram p=ProgramLibrary.byId("beginner");long id=store.begin(p,"TEST",false),demo=store.begin(p,"TEST DEMO",true);
  check(store.list(false).size()==1&&store.list(true).size()==1,"real and demo isolation");
  SessionEngine engine=new SessionEngine(p,180,190,68,32);engine.start();Metrics m=engine.advance(3,100,80,120,6);TelemetryStats stats=new TelemetryStats();stats.add(3,100,80,120);stats.addSpeed(1,12);stats.addSpeed(2,24);stats.counters(5,10,false);stats.counters(5.03f,11,true);BikeSource.Sample x=BikeSource.Sample.measured();x.powerValid=true;x.powerW=100;x.cadenceValid=true;x.cadenceRpm=80;x.speedValid=true;x.speedKph=24;x.distanceValid=true;x.hrValid=true;x.heartRate=120;x.hrExternal=true;x.hrSource="Test Watch";
  store.checkpoint(id,m,stats,"active",x);WorkoutStore.Ride ride=store.get(id);check(ride.seconds==3,"duration checkpoint");near(ride.avgPower,100,"power persisted");near(ride.distance,.03f,"delta persisted");check(ride.maxHr==120,"measured max hr");near(ride.avgSpeed,20,"weighted speed persisted");near(ride.maxSpeed,24,"max speed persisted");near(store.points(id,"speed").get(0)[1],24,"speed curve persisted");near(store.points(id,"distance").get(0)[1],.03f,"distance curve persisted");check(ride.hrDevice.equals("Test Watch"),"heart device persisted");
  BikeSource.Sample noData=BikeSource.Sample.measured();store.checkpoint(demo,m,new TelemetryStats(),"active",noData);check(store.points(demo,"hr").get(0)[1]==-1,"missing heart series NULL");check(store.points(demo,"speed").get(0)[1]==-1,"missing speed series NULL");check(store.points(demo,"distance").get(0)[1]==-1,"missing distance series NULL");store.close();
  store=new WorkoutStore(getTargetContext(),db);check(store.get(id).seconds==3,"data survives reopen");store.recover();check(store.get(id).status.equals("interrupted"),"interrupted checkpoint recovered");check(store.get(demo).status.equals("interrupted"),"demo interruption recovered");
  store.checkpoint(id,m,stats,"saved",null);store.recover();check(store.get(id).status.equals("saved"),"saved state preserved");check(store.points(id,"power").size()==1,"final save does not duplicate samples");
  final BikeSource.Sample[] out=new BikeSource.Sample[1];BleBike bike=new BleBike(getTargetContext(),"00:00:00:00:00:00","fixture",BikeSource.TYPE_FTMS);bike.setListener(new BikeSource.Listener(){public void onState(int a,String b){}public void onLog(String l){}public void onData(BikeSource.Sample s){out[0]=s;}});
  Field features=BleBike.class.getDeclaredField("targetSettingFeatures");features.setAccessible(true);features.setLong(bike,4);check(bike.supportsResistanceTarget()&&!bike.supportsPowerTarget(),"resistance target bit 2");features.setLong(bike,8);check(!bike.supportsResistanceTarget()&&bike.supportsPowerTarget(),"power target bit 3");
  Method parse=BleBike.class.getDeclaredMethod("parse",java.util.UUID.class,byte[].class);parse.setAccessible(true);
  byte[] frame={0x74,3,0x60,9,(byte)0xa0,0,0x39,0x30,0,12,0,(byte)180,0,(byte)250,0,0,0,0,(byte)140};
  parse.invoke(bike,BleBike.INDOOR_BIKE,frame);waitForIdleSync();check(out[0]!=null&&!out[0].simulated,"measured frame emitted");near(out[0].powerW,180,"power decode");near(out[0].cadenceRpm,80,"cadence decode");near(out[0].speedKph,24,"speed decode");near(out[0].distanceKm,12.345f,"distance decode");check(out[0].heartRate==140&&out[0].resistance==12,"heart and resistance decode");near(out[0].calories,250,"energy decode");
  frame[9]=60;parse.invoke(bike,BleBike.INDOOR_BIKE,frame);waitForIdleSync();check(out[0].resistance==60&&bike.displayResistance()==60,"actual resistance 60 never displayed as 19");check(bike.resistanceMaximum()==100,"real bike range independent of legacy 32 setting");
  BleHeartRate hr=new BleHeartRate(getTargetContext(),"00:00:00:00:00:01","Fixture Watch",null);check(hr.bpm()==-1&&!hr.connected()&&!hr.busy(),"unconnected heart has no stale reading");check(hr.samePeer("rotated","Fixture Watch"),"same selected sensor recognized after address rotation");
  Method machineStatus=BleBike.class.getDeclaredMethod("handleMachineStatus",byte[].class);machineStatus.setAccessible(true);machineStatus.invoke(bike,(Object)new byte[]{7,(byte)150,0});check(bike.actualResistance()==60,"target-status 15 cannot overwrite actual telemetry 60");
  Field commandField=BleBike.class.getDeclaredField("resistanceCommand");commandField.setAccessible(true);ResistanceCommandState command=(ResistanceCommandState)commandField.get(bike);command.request(15,1);command.written(15,2);command.measured(15,3);command.measured(16,4);
  BikeSession owner=BikeSession.get();TrainingProgram free=ProgramLibrary.byId("free");SessionEngine handoff=new SessionEngine(free,180,190,68,100);handoff.start();handoff.setAutoResistance(true);BikeSource.Sample knob=BikeSource.Sample.measured();knob.resistance=16;knob.resistanceValid=true;
  field(bike,"state",BikeSource.STATE_CONNECTED);field(owner,"source",bike);field(owner,"ble",bike);field(owner,"engine",handoff);field(owner,"program",free);field(owner,"context",getTargetContext());field(owner,"store",store);field(owner,"stats",new TelemetryStats());field(owner,"sample",knob);field(owner,"sampleAt",android.os.SystemClock.elapsedRealtime());field(owner,"lastTick",android.os.SystemClock.elapsedRealtime());field(owner,"recordId",id);owner.tick();check(!handoff.isAutoResistance()&&handoff.getManualResistance()==16,"physical knob hands auto ride to manual at actual resistance");
  final String prefsName="spinfit_stage_test_settings";
  android.content.Context isolatedSettings=new android.content.ContextWrapper(getTargetContext()){
   @Override public android.content.SharedPreferences getSharedPreferences(String n,int mode){return getBaseContext().getSharedPreferences(prefsName,mode);}
  };
  Settings testSettings=new Settings(isolatedSettings);testSettings.setResistanceMax(100);owner.init(testSettings);field(owner,"ble",null);
  TrainingProgram hiit=ProgramLibrary.byId("hiit30");SessionEngine allStages=new SessionEngine(hiit,180,190,68,100);allStages.start();
  RecordingBike recorder=new RecordingBike(allStages);field(owner,"source",recorder);field(owner,"engine",allStages);field(owner,"program",hiit);field(owner,"sentTarget",-1);
  owner.applyAuto();for(int second=1;second<=1800;second++){allStages.advance(1,200,90,150,0);owner.applyAuto();}
  int[] expectedTargets={15,45,15,45,15,45,15,45,15,45,15,45,15,10};
  int[] expectedTimes={0,420,480,600,660,780,840,960,1020,1140,1200,1320,1380,1500};
  check(recorder.targets.size()==14,"HIIT sends one target per changed phase, not one per telemetry tick");
  for(int j=0;j<14;j++){check(recorder.targets.get(j)==expectedTargets[j],"HIIT physical target at phase "+j);check(recorder.seconds.get(j)==expectedTimes[j],"HIIT switches resistance exactly at boundary "+j);}
  check(allStages.isFinished(),"HIIT natural completion at 1800 seconds");
  SessionEngine transitions=new SessionEngine(hiit,180,190,68,100);transitions.start();recorder=new RecordingBike(transitions);field(owner,"source",recorder);field(owner,"engine",transitions);field(owner,"sentTarget",-1);owner.applyAuto();
  transitions.advance(419,100,80,130,15);transitions.pause();transitions.advance(2,100,80,130,15);owner.applyAuto();check(recorder.targets.size()==1&&transitions.snapshot().elapsedSec==419,"pause before a phase freezes time and blocks resistance writes");
  transitions.resume();field(owner,"sampleAt",android.os.SystemClock.elapsedRealtime());field(owner,"lastTick",android.os.SystemClock.elapsedRealtime()-1000);field(owner,"stats",new TelemetryStats());owner.tick();check(recorder.targets.size()==2&&recorder.targets.get(1)==45&&transitions.snapshot().stageIndex==1,"session timer tick crosses into hard interval and automatically sends its target");
  owner.setAuto(false);transitions.advance(180,100,80,130,45);owner.applyAuto();check(recorder.targets.size()==2&&!transitions.isAutoResistance(),"manual mode survives later course phases without automatic writes");
  owner.setAuto(true);check(recorder.targets.size()==3&&recorder.targets.get(2)==45,"explicitly resuming auto immediately applies current phase");
  // Exercise production startProgram with an isolated store and a service stub: no real BLE or timer.
  android.content.Context serviceContext=new android.content.ContextWrapper(getTargetContext()){
   @Override public android.content.ComponentName startForegroundService(android.content.Intent i){return i.getComponent();}
   @Override public android.content.ComponentName startService(android.content.Intent i){return i.getComponent();}
   @Override public boolean stopService(android.content.Intent i){return true;}
  };
  transitions.stop();field(owner,"context",serviceContext);field(owner,"demo",null);field(owner,"demoMode",false);
  testSettings.setAutoResistance(false);
  for(TrainingProgram candidate:ProgramLibrary.all()){
   RecordingBike startup=new RecordingBike(null);field(owner,"source",startup);field(owner,"sample",knob);field(owner,"sampleAt",android.os.SystemClock.elapsedRealtime());
   owner.startProgram(candidate);
   check(owner.active()&&startup.starts==1,"course starts its device once: "+candidate.id);
   check(owner.engine().isAutoResistance()==!candidate.freeRide,"startup mode follows course despite old global preference: "+candidate.id);
   if(candidate.freeRide){check(startup.targets.isEmpty()&&owner.engine().getManualResistance()==16,"free ride preserves current bike resistance without any motor write");}
   else{check(startup.targets.size()==1&&startup.targets.get(0)==Math.round(candidate.segments[0].targetResistancePct),"course immediately sends first phase target: "+candidate.id);}
   owner.setAuto(false);owner.finishWorkout();
  }
  TrainingProgram fixed=new TrainingProgram("fixed_fixture","Fixed fixture","","",false,new Segment[]{new Segment("A","",60,50,25,70,1),new Segment("B","",60,80,25,90,3)});
  RecordingBike startup=new RecordingBike(null);field(owner,"source",startup);field(owner,"sampleAt",android.os.SystemClock.elapsedRealtime());owner.startProgram(fixed);
  check(!owner.engine().isAutoResistance()&&startup.targets.equals(Arrays.asList(25)),"fixed course sets target once on startup in manual mode");
  owner.engine().advance(61,100,80,130,25);owner.applyAuto();owner.applyAuto();check(startup.targets.size()==1,"fixed course never repeats a successful startup write at later phases");
  owner.adjustBy(1);check(startup.targets.equals(Arrays.asList(25,17)),"manual fixed-course adjustment steps from current actual resistance");owner.applyAuto();check(startup.targets.size()==2,"manual takeover cancels pending fixed startup target");owner.finishWorkout();
  field(owner,"context",new android.content.ContextWrapper(getTargetContext()){
   @Override public android.content.ComponentName startForegroundService(android.content.Intent i){throw new IllegalStateException("fixture service denied");}
  });
  startup=new RecordingBike(null);field(owner,"source",startup);boolean denied=false;
  try{owner.startProgram(hiit);}catch(IllegalStateException expected){denied=true;}
  check(denied&&!owner.active()&&store.get(owner.recordId()).status.equals("interrupted"),"service failure records an interrupted start");
  check(startup.starts==0&&startup.stops==0&&startup.targets.isEmpty(),"failed service startup sends no bike commands");
  field(owner,"context",getTargetContext());
  getTargetContext().deleteSharedPreferences(prefsName);
  final BikeSource.Sample[] missing=new BikeSource.Sample[1];BleBike minimal=new BleBike(getTargetContext(),"00:00:00:00:00:00","fixture",1);minimal.setListener(new BikeSource.Listener(){public void onState(int a,String b){}public void onLog(String l){}public void onData(BikeSource.Sample v){missing[0]=v;}});parse.invoke(minimal,BleBike.INDOOR_BIKE,new byte[]{0,0,0,0});waitForIdleSync();check(missing[0].speedValid&&!missing[0].powerValid&&!missing[0].hrValid&&!missing[0].caloriesValid&&!missing[0].distanceValid,"unreported fields not fabricated");
  String migration="spinfit_migration_tests.db";getTargetContext().deleteDatabase(migration);android.database.sqlite.SQLiteDatabase legacy=getTargetContext().openOrCreateDatabase(migration,0,null);
  legacy.execSQL("CREATE TABLE rides(id INTEGER PRIMARY KEY AUTOINCREMENT,started INTEGER,name TEXT,program TEXT,device TEXT,demo INTEGER,status TEXT,seconds INTEGER DEFAULT 0,distance REAL,calories REAL,avg_power REAL,avg_cad REAL,avg_hr REAL,max_power REAL,max_hr REAL)");
  legacy.execSQL("CREATE TABLE points(id INTEGER PRIMARY KEY,ride INTEGER,second INTEGER,power REAL,cadence REAL,hr REAL,speed REAL,resistance REAL,target REAL)");
  legacy.execSQL("INSERT INTO rides(id,started,name,program,device,demo,status,seconds,distance,avg_power) VALUES(1,123,'Legacy Ride','free','Real Bike',0,'saved',54,0.21,37)");legacy.execSQL("INSERT INTO points(ride,second,speed) VALUES(1,1,10),(1,2,20),(1,3,NULL)");legacy.setVersion(1);legacy.close();
  WorkoutStore migrated=new WorkoutStore(getTargetContext(),migration);WorkoutStore.Ride older=migrated.get(1);check(older.seconds==54&&older.status.equals("saved"),"legacy record survives migration");near(older.distance,.21f,"legacy total distance retained");near(older.avgPower,37,"legacy power retained");near(older.avgSpeed,15,"legacy sample average speed backfilled");near(older.maxSpeed,20,"legacy maximum speed backfilled");check(older.speedBasis.equals("legacy_samples"),"legacy averaging method explicit");check(migrated.points(1,"distance").get(0)[1]==-1,"missing historical distance not fabricated");check(migrated.points(1,"speed").size()==3,"legacy speed curve preserved");migrated.close();getTargetContext().deleteDatabase(migration);
  result.putString("result","PASS: "+count+" Android assertions (SQLite persistence, recovery, protocol fixtures)");finish(-1,result);
 }catch(Throwable e){result.putString("failure",e.toString());finish(0,result);}finally{if(store!=null)store.close();getTargetContext().deleteDatabase("spinfit_isolated_tests.db");}}
 static final class RecordingBike implements BikeSource{
  int starts,stops;final SessionEngine engine;final List<Integer> targets=new ArrayList<>(),seconds=new ArrayList<>();
  RecordingBike(SessionEngine e){engine=e;}
  public void setListener(Listener l){} public void connect(){} public void disconnect(){}
  public boolean isConnected(){return true;} public int type(){return TYPE_FTMS;}
  public String name(){return "Isolated stage fixture";} public String address(){return "00:00:00:00:00:00";}
  public WriteResult writeResistance(int level,int max){if(max!=100)throw new AssertionError("physical scale");targets.add(level);seconds.add((engine!=null?engine:BikeSession.get().engine()).snapshot().elapsedSec);return new WriteResult(true,true,level,"");}
  public boolean writeTargetPower(int watts){return false;}public boolean startWorkout(){starts++;return true;}public boolean pauseWorkout(){return true;}public boolean stopWorkout(){stops++;return true;}
  public boolean supportsResistanceTarget(){return true;}public boolean hasControl(){return true;}public int actualResistance(){return -1;}public String diagnostics(){return "Fixture";}
 }

}
