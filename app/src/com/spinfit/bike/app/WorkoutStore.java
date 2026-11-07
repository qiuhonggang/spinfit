package com.spinfit.bike.app;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import com.spinfit.bike.ble.BikeSource;
import com.spinfit.bike.core.*;
import java.util.*;

/** Schema 2 adds speed summaries, per-point distance, and explicit heart-rate source. */
public final class WorkoutStore extends SQLiteOpenHelper {
    public WorkoutStore(Context c){this(c,"spinfit_workouts.db");}
    public WorkoutStore(Context c,String name){super(c.getApplicationContext(),name,null,2);}
    @Override public void onConfigure(SQLiteDatabase d){d.setForeignKeyConstraintsEnabled(true);}
    @Override public void onCreate(SQLiteDatabase d){
        d.execSQL("CREATE TABLE rides(id INTEGER PRIMARY KEY AUTOINCREMENT,started INTEGER,name TEXT,program TEXT,device TEXT,demo INTEGER,status TEXT,seconds INTEGER DEFAULT 0,distance REAL,calories REAL,avg_power REAL,avg_cad REAL,avg_hr REAL,max_power REAL,max_hr REAL,avg_speed REAL,max_speed REAL,speed_basis TEXT,hr_device TEXT)");
        d.execSQL("CREATE TABLE points(id INTEGER PRIMARY KEY,ride INTEGER REFERENCES rides(id) ON DELETE CASCADE,second INTEGER,power REAL,cadence REAL,hr REAL,speed REAL,resistance REAL,target REAL,distance REAL,hr_source TEXT)");
        d.execSQL("CREATE INDEX points_ride ON points(ride,second)");
    }
    @Override public void onUpgrade(SQLiteDatabase d,int old,int version){
        if(old<2){
            d.execSQL("ALTER TABLE rides ADD COLUMN avg_speed REAL");d.execSQL("ALTER TABLE rides ADD COLUMN max_speed REAL");d.execSQL("ALTER TABLE rides ADD COLUMN speed_basis TEXT");d.execSQL("ALTER TABLE rides ADD COLUMN hr_device TEXT");
            d.execSQL("ALTER TABLE points ADD COLUMN distance REAL");d.execSQL("ALTER TABLE points ADD COLUMN hr_source TEXT");
            d.execSQL("UPDATE rides SET avg_speed=(SELECT AVG(speed) FROM points WHERE points.ride=rides.id AND speed>=0),max_speed=(SELECT MAX(speed) FROM points WHERE points.ride=rides.id AND speed>=0),speed_basis='legacy_samples'");
            // Historical totals remain intact. Never synthesize missing historical distance samples.
        }
    }
    public void recover(){ContentValues v=new ContentValues();v.put("status","interrupted");getWritableDatabase().update("rides",v,"status='active'",null);}
    public long begin(TrainingProgram p,String device,boolean demo){ContentValues v=new ContentValues();v.put("started",System.currentTimeMillis());v.put("name",p.name);v.put("program",p.id);v.put("device",device);v.put("demo",demo?1:0);v.put("status","active");v.put("speed_basis","time_weighted");return getWritableDatabase().insertOrThrow("rides",null,v);}
    public void checkpoint(long id,Metrics m,TelemetryStats stats,String status,BikeSource.Sample s){
        SQLiteDatabase d=getWritableDatabase();d.beginTransaction();
        try{
            ContentValues v=new ContentValues();v.put("seconds",m.elapsedSec);v.put("status",status);
            nullable(v,"distance",stats.distanceSeen?stats.distance:-1);nullable(v,"calories",stats.caloriesSeen?stats.calories:-1);
            nullable(v,"avg_power",stats.avgPower());nullable(v,"avg_cad",stats.avgCadence());nullable(v,"avg_hr",stats.avgHr());
            nullable(v,"max_power",stats.powerSec>0?stats.maxPower:-1);nullable(v,"max_hr",stats.hrSec>0?stats.maxHr:-1);
            nullable(v,"avg_speed",stats.avgSpeed());nullable(v,"max_speed",stats.speedSec>0?stats.maxSpeed:-1);
            if(s!=null&&s.hrExternal&&s.hrValid)v.put("hr_device",s.hrSource);
            d.update("rides",v,"id=?",new String[]{""+id});
            if(s!=null){
                ContentValues q=new ContentValues();q.put("ride",id);q.put("second",m.elapsedSec);
                nullable(q,"power",s.powerValid?s.powerW:-1);nullable(q,"cadence",s.cadenceValid?s.cadenceRpm:-1);
                nullable(q,"hr",s.hrValid?s.heartRate:-1);nullable(q,"speed",s.speedValid?s.speedKph:-1);
                nullable(q,"distance",s.distanceValid&&stats.distanceSeen?stats.distance:-1);
                nullable(q,"resistance",s.resistanceValid?s.resistance:-1);q.put("target",m.targetPowerW);q.put("hr_source",s.hrValid?s.hrSource:"");
                d.insertOrThrow("points",null,q);
            }
            d.setTransactionSuccessful();
        }finally{d.endTransaction();}
    }
    private static void nullable(ContentValues v,String k,float f){if(f>=0&&!Float.isNaN(f)&&!Float.isInfinite(f))v.put(k,f);else v.putNull(k);}
    public static final class Ride {
        public long id,started;public String name,program,device,status,speedBasis,hrDevice;public boolean demo;public int seconds;
        public float distance,calories,avgPower,avgCad,avgHr,maxPower,maxHr,avgSpeed,maxSpeed;
    }
    private Ride read(Cursor c){Ride r=new Ride();r.id=c.getLong(c.getColumnIndexOrThrow("id"));r.started=c.getLong(c.getColumnIndexOrThrow("started"));r.seconds=c.getInt(c.getColumnIndexOrThrow("seconds"));r.demo=c.getInt(c.getColumnIndexOrThrow("demo"))==1;
        r.name=str(c,"name");r.program=str(c,"program");r.device=str(c,"device");r.status=str(c,"status");r.speedBasis=str(c,"speed_basis");r.hrDevice=str(c,"hr_device");
        r.distance=val(c,"distance");r.calories=val(c,"calories");r.avgPower=val(c,"avg_power");r.avgCad=val(c,"avg_cad");r.avgHr=val(c,"avg_hr");r.maxPower=val(c,"max_power");r.maxHr=val(c,"max_hr");r.avgSpeed=val(c,"avg_speed");r.maxSpeed=val(c,"max_speed");return r;
    }
    private String str(Cursor c,String key){String s=c.getString(c.getColumnIndexOrThrow(key));return s==null?"":s;}
    private float val(Cursor c,String key){int i=c.getColumnIndexOrThrow(key);return c.isNull(i)?-1:c.getFloat(i);}
    public List<Ride> list(boolean demos){List<Ride> a=new ArrayList<>();try(Cursor c=getReadableDatabase().query("rides",null,"demo=?",new String[]{demos?"1":"0"},null,null,"started DESC")){while(c.moveToNext())a.add(read(c));}return a;}
    public Ride get(long id){try(Cursor c=getReadableDatabase().query("rides",null,"id=?",new String[]{""+id},null,null,null)){return c.moveToFirst()?read(c):null;}}
    public List<float[]> points(long id,String field){
        if(!Arrays.asList("power","cadence","hr","speed","distance").contains(field))return Collections.emptyList();
        List<float[]> a=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT second,"+field+" FROM points WHERE ride=? ORDER BY second,id",new String[]{""+id})){while(c.moveToNext())a.add(new float[]{c.getFloat(0),c.isNull(1)?-1:c.getFloat(1)});}return a;
    }
}
