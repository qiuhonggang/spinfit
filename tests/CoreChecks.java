import com.spinfit.bike.core.*;
public class CoreChecks {
 static int checks=0;
 static void check(boolean b,String name){if(!b)throw new AssertionError(name);checks++;}
 static void near(float a,float b,String name){check(Math.abs(a-b)<.015,name+" "+a+" vs "+b);}
 public static void main(String[] args){
  check(ProgramLibrary.all().size()==8,"curated count");
  for(TrainingProgram p:ProgramLibrary.all()){
   int sum=0;for(Segment s:p.segments){check(s.durationSec>0,"positive stage");check(s.targetCadence>=60&&s.targetCadence<=95,"sensible cadence");sum+=s.durationSec;}
   check(sum==p.totalSeconds(),"duration sum");
   if(!p.freeRide){check(p.segments[0].durationSec>=300,"warmup >=5min");check(p.segments[p.segments.length-1].durationSec>=300,"cooldown >=5min");}
   SessionEngine e=new SessionEngine(p,180,190,68,32);e.start();e.advance(1.8f,100,80,120,5);check(e.snapshot().elapsedSec==1,"clock not tied to packet count");e.pause();e.advance(3,100,80,120,5);check(e.snapshot().elapsedSec==1,"pause freezes clock");e.resume();e.advance(.3f,100,80,120,5);check(e.snapshot().elapsedSec==2,"resume clock");
   if(!p.freeRide){e.setTimeScale(60);for(int j=0;j<3000&&!e.isFinished();j++)e.advance(1,100,80,120,5);check(e.isFinished(),"natural finish");check(e.snapshot().elapsedSec==sum,"finish clamps exact duration");}
  }
  TelemetryStats s=new TelemetryStats();s.add(1,100,80,120);s.add(3,200,-1,0);s.add(1,-1,100,160);near(s.avgPower(),175,"weighted power only valid");near(s.avgCadence(),90,"cadence ignores missing");near(s.avgHr(),140,"heart ignores missing");s.add(Float.NaN,900,900,900);near(s.avgPower(),175,"nan dt ignored");
  s.counters(8,100,false);s.counters(8.2f,103,true);near(s.distance,.2f,"counter delta baseline");near(s.calories,3,"calorie delta");s.counters(10,120,false);s.counters(10.1f,121,true);near(s.distance,.3f,"pause delta excluded");near(s.calories,4,"pause calories excluded");s.counters(.02f,0,true);s.counters(.04f,1,true);near(s.distance,.32f,"counter reset does not go negative");near(s.calories,5,"calorie reset");
  TelemetryStats empty=new TelemetryStats();check(empty.avgPower()==-1&&empty.avgCadence()==-1&&empty.avgHr()==-1,"missing averages stay missing");check(!empty.distanceSeen&&!empty.caloriesSeen,"missing counters stay missing");
  TelemetryStats speed=new TelemetryStats();speed.addSpeed(1,10);speed.addSpeed(3,30);speed.addSpeed(5,-1);near(speed.avgSpeed(),25,"weighted speed ignores missing");near(speed.maxSpeed,30,"max speed");check(empty.avgSpeed()==-1,"missing speed stays missing");speed.addSpeed(Float.NaN,90);near(speed.avgSpeed(),25,"invalid speed dt ignored");
  check(HeartRatePacket.decode(new byte[]{0,72})==72,"uint8 HR");check(HeartRatePacket.decode(new byte[]{1,44,1})==300,"uint16 HR little endian");check(HeartRatePacket.decode(new byte[]{6,90})==90,"sensor contact detected");check(HeartRatePacket.decode(new byte[]{4,90})==-1,"contact lost rejected");check(HeartRatePacket.decode(new byte[]{0,0})==-1,"zero HR unavailable");check(HeartRatePacket.decode(new byte[]{1,0})==-1,"truncated uint16 rejected");check(HeartRatePacket.decode(null)==-1,"null HR rejected");check(HeartRatePacket.decode(new byte[]{(byte)0xe0,80})==80,"reserved flag bits ignored");check(HeartRatePacket.decode(new byte[]{0x18,80,0,0,12,3})==80,"energy and RR fields do not alter HR");check(HeartRatePacket.decode(new byte[]{1,0,4})==-1,"invalid HR value rejected");
  check(java.util.Arrays.equals(ResistanceScale.command(60,0,100,1),new byte[]{4,0x58,2}),"physical 60 encoded as 600 tenths, no app-scale mapping");
  check(java.util.Arrays.equals(ResistanceScale.command(19,0,100,1),new byte[]{4,(byte)190,0}),"physical 19 remains 19");
  near(ResistanceScale.target(120,0,100,1),100,"device maximum enforced");near(ResistanceScale.target(-4,1,32,1),1,"device minimum enforced");near(ResistanceScale.target(5.2f,0,10,.5f),5,"device step enforced");
  SessionEngine physical=new SessionEngine(ProgramLibrary.byId("free"),180,190,68,100);physical.setAutoResistance(false);physical.setManualResistance(60);physical.start();near(physical.currentTargetResistance(),60,"manual target retains bike units at workout start");
  SessionEngine course=new SessionEngine(ProgramLibrary.byId("beginner"),180,190,68,100);near(course.currentTargetResistance(),course.currentSegment().targetResistancePct,"course percentage converted once to 100-level device");
  check(HeartReconnectPolicy.matches("OLD","AW-HR-WATCH","NEW","AW-HR-WATCH",true),"rediscover rotated watch address");
  check(!HeartReconnectPolicy.matches("OLD","AW-HR-WATCH","NEW","AW-HR-WATCH",false),"name alone cannot replace sensor identity");
  check(!HeartReconnectPolicy.matches("OLD","AW-HR-WATCH","NEW","Other Watch",true),"unselected sensor rejected");
  check(HeartReconnectPolicy.matches("SAME","AW-HR-WATCH","same",null,false),"selected address works without scan-response name");
  check(!HeartReconnectPolicy.matches("OLD","蓝牙心率设备","NEW","蓝牙心率设备",true),"generic name cannot select unrelated sensor");
  HeartReconnectPolicy retry=new HeartReconnectPolicy();check(retry.retryDelay()==1200&&retry.retryDelay()==3000&&retry.retryDelay()==6000&&retry.retryDelay()==-1,"retries back off and terminate");retry.reset();check(retry.retryDelay()==1200,"stable connection restores outage budget");
  check(ResistanceScale.step(80,11,1,0,100,false)==81,"manual plus starts from actual knob value, not stale target");
  check(ResistanceScale.step(80,81,1,0,100,true)==82,"rapid plus retains pending intent");
  check(ResistanceScale.step(80,11,-1,0,100,false)==79,"manual minus starts from actual knob value");
  check(ResistanceScale.step(-1,11,1,0,100,false)==12,"unavailable telemetry uses current intent");
  check(ResistanceScale.step(100,11,1,0,100,false)==100,"manual step honors device upper bound");
  System.out.println("PASS: "+checks+" core assertions");
 }
}
