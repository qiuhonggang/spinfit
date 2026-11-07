package com.spinfit.bike.core;

/** Bounded retry budget and identity matching, independent of Android/GATT callbacks. */
public final class HeartReconnectPolicy {
    private int failures;
    public void reset(){failures=0;}
    public long retryDelay(){return ++failures<=3?new long[]{1200,3000,6000}[failures-1]:-1;}
    public static boolean matches(String savedAddress,String savedName,String address,String name,boolean heartService){
        if(savedAddress!=null&&savedAddress.equalsIgnoreCase(address))return true;
        // A rotating address is accepted only for the selected name and advertised HR service.
        return heartService&&savedName!=null&&!savedName.isEmpty()&&!"蓝牙心率设备".equals(savedName)&&savedName.equals(name);
    }
}
