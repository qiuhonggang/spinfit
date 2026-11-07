package com.spinfit.bike.core;

/** Time-weighted statistics over valid device fields only. No synthetic telemetry. */
public final class TelemetryStats {
    private static boolean finite(float f){return !Float.isNaN(f)&&!Float.isInfinite(f);}

    public double speedSum,speedSec;
    public float maxSpeed;
    public double powerSum, powerSec, cadenceSum, cadenceSec, hrSum, hrSec;
    public float maxPower, maxCadence, maxHr, distance, calories;
    public boolean distanceSeen, caloriesSeen;
    private float lastDistance=-1, lastCalories=-1;
    public void counters(float d, float k, boolean active) {
        if (finite(d) && d>=0) {
            if(active && lastDistance>=0 && d>=lastDistance) {distance+=d-lastDistance; distanceSeen=true;}
            lastDistance=d;
        }
        if(finite(k) && k>=0) {
            if(active && lastCalories>=0 && k>=lastCalories) {calories+=k-lastCalories; caloriesSeen=true;}
            lastCalories=k;
        }
    }
    public void add(float dt,float p,float c,float hr) {
        if(!finite(dt)||dt<=0)return;
        if(finite(p)&&p>=0){powerSum+=p*dt;powerSec+=dt;maxPower=Math.max(maxPower,p);}
        if(finite(c)&&c>=0){cadenceSum+=c*dt;cadenceSec+=dt;maxCadence=Math.max(maxCadence,c);}
        if(finite(hr)&&hr>0){hrSum+=hr*dt;hrSec+=dt;maxHr=Math.max(maxHr,hr);}
    }
    public void addSpeed(float dt,float speed){if(!finite(dt)||dt<=0||!finite(speed)||speed<0)return;speedSum+=speed*dt;speedSec+=dt;maxSpeed=Math.max(maxSpeed,speed);}
    public float avgSpeed(){return speedSec>0?(float)(speedSum/speedSec):-1;}
    public float avgPower(){return powerSec>0?(float)(powerSum/powerSec):-1;}
    public float avgCadence(){return cadenceSec>0?(float)(cadenceSum/cadenceSec):-1;}
    public float avgHr(){return hrSec>0?(float)(hrSum/hrSec):-1;}
}
