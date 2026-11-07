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
  System.out.println("PASS: "+checks+" core assertions");
 }
}
