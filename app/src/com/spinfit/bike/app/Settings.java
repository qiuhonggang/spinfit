package com.spinfit.bike.app;

import android.content.Context;
import android.content.SharedPreferences;

/** 用户可调参数：FTP、最大心率、体重、阻力档位上限、演示时间倍率、目标模式。 */
public final class Settings {
    private static final String PREF = "spinfit_settings";
    private final SharedPreferences p;

    public Settings(Context c) {
        p = c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public int ftpW()          { return clamp(p.getInt("ftp", 180), 50, 500); }
    public int maxHr()         { return clamp(p.getInt("maxhr", 190), 120, 230); }
    public float weightKg()    { return clampF(p.getFloat("weight", 68f), 30f, 200f); }
    public int resistanceMax() { return clamp(p.getInt("rmax", 32), 8, 100); }
    public float timeScale()   { return clampF(p.getFloat("scale", 1f), 1f, 60f); }
    public boolean autoResistance() { return p.getBoolean("autores", true); }
    /** 0=功率(%FTP) 目标, 1=心率(%HRmax) 目标 */
    public int targetMode()    { return p.getInt("targetmode", 0); }
    public boolean demoMode()  { return p.getBoolean("demo", false); }
    public String bikeAddress(){return p.getString("bike_address", "");}
    public String bikeName(){return p.getString("bike_name", "我的单车");}
    public int bikeType(){return p.getInt("bike_type",1);}
    public void rememberBike(String address,String name,int type){p.edit().putString("bike_address",address).putString("bike_name",name).putInt("bike_type",type).apply();}

    public String heartAddress(){return p.getString("heart_address", "");}
    public String heartName(){return p.getString("heart_name", "心率设备");}
    public void rememberHeart(String address,String name){p.edit().putString("heart_address",address).putString("heart_name",name).apply();}
    public void setFtpW(int v)          { p.edit().putInt("ftp", clamp(v, 50, 500)).apply(); }
    public void setMaxHr(int v)         { p.edit().putInt("maxhr", clamp(v, 120, 230)).apply(); }
    public void setWeightKg(float v)    { p.edit().putFloat("weight", clampF(v, 30f, 200f)).apply(); }
    public void setResistanceMax(int v) { p.edit().putInt("rmax", clamp(v, 8, 100)).apply(); }
    public void setTimeScale(float v)   { p.edit().putFloat("scale", clampF(v, 1f, 60f)).apply(); }
    public void setAutoResistance(boolean v) { p.edit().putBoolean("autores", v).apply(); }
    public void setTargetMode(int v)    { p.edit().putInt("targetmode", v == 1 ? 1 : 0).apply(); }
    public void setDemoMode(boolean v)  { p.edit().putBoolean("demo", v).apply(); }

    private static int clamp(int v, int lo, int hi) { return v < lo ? lo : (v > hi ? hi : v); }
    private static float clampF(float v, float lo, float hi) {
        if (Float.isNaN(v) || Float.isInfinite(v)) return lo;
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
