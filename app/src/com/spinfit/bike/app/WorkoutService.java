package com.spinfit.bike.app;
import android.app.*;
import android.content.*;
import android.os.*;
import com.spinfit.bike.ui.RideActivity;
public final class WorkoutService extends Service {
    private final Handler h=new Handler(Looper.getMainLooper());private PowerManager.WakeLock wake;
    private final Runnable tick=new Runnable(){public void run(){BikeSession s=BikeSession.get();s.tick();if(s.active()){notifyState();h.postDelayed(this,1000);}else stopSelf();}};
    @Override public void onCreate(){super.onCreate();if(Build.VERSION.SDK_INT>=26)((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(new NotificationChannel("ride","骑行训练",NotificationManager.IMPORTANCE_LOW));}
    private Notification notification(){
        BikeSession s=BikeSession.get();Intent i=new Intent(this,RideActivity.class);i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent p=PendingIntent.getActivity(this,1,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,"ride"):new Notification.Builder(this);
        return b.setSmallIcon(com.spinfit.bike.R.drawable.ic_stat).setContentTitle(s.program()==null?"SpinFit 骑行":s.program().name).setContentText(s.engine()!=null&&s.engine().isPaused()?"训练已暂停 · 点击返回":"正在记录训练 · 点击返回骑行").setContentIntent(p).setOngoing(true).build();
    }
    private void notifyState(){((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(27,notification());}
    @Override public int onStartCommand(Intent i,int f,int id){startForeground(27,notification());if(!BikeSession.get().active()){stopSelf();return START_NOT_STICKY;}if(wake==null){wake=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"spinfit:ride");wake.acquire();}h.removeCallbacks(tick);h.post(tick);return START_NOT_STICKY;}
    @Override public IBinder onBind(Intent i){return null;}
    @Override public void onDestroy(){h.removeCallbacks(tick);if(wake!=null&&wake.isHeld())wake.release();super.onDestroy();}
}
