package com.spinfit.bike.core;

/** A control-point ACK is distinct from measured resistance reaching the target. */
public final class ResistanceCommandState {
    private int target=-1,actual=-1,attempts;
    private long requestedAt,writtenAt=-1;
    private boolean acknowledged,verified,retryable,overridden;
    private String failure="";
    public int target(){return target;}
    public int actual(){return actual;}
    public boolean verified(){return verified;}
    public boolean overridden(){return overridden;}
    public void clear(){target=-1;attempts=0;writtenAt=-1;acknowledged=verified=retryable=overridden=false;failure="";}
    public void request(int level,long now){if(level!=target)attempts=0;target=level;attempts++;requestedAt=now;writtenAt=-1;acknowledged=verified=retryable=overridden=false;failure="";}
    public void written(int level,long now){if(level==target)writtenAt=now;}
    public void response(int level,boolean ok,String why){if(level!=target)return;if(ok)acknowledged=true;else fail(level,why,false);}
    public void fail(int level,String why,boolean retry){if(level!=target)return;failure=why;retryable=retry;verified=false;}
    public void measured(int level,long now){
        actual=level;
        if(target<0)return;
        if(verified&&level!=target){overridden=true;verified=false;return;}
        if(writtenAt>=0&&now>=writtenAt&&level==target&&!overridden&&(failure.isEmpty()||retryable)){verified=true;failure="";retryable=false;}
    }
    private void expire(long now){if(target>=0&&!verified&&!overridden&&failure.isEmpty()&&now-(writtenAt>=0?writtenAt:requestedAt)>=(writtenAt>=0?8000:15000))fail(target,"未确认实际阻力到达目标",true);}
    public boolean shouldRetry(int level,long now){expire(now);return level==target&&!overridden&&retryable&&attempts<2&&actual!=target;}
    public String message(long now){
        expire(now);
        if(target<0)return "尚未设置阻力";
        if(overridden)return "检测到单车旋钮调节，已切换为手动";
        if(!failure.isEmpty())return failure+"（目标 "+target+"，实际 "+(actual<0?"—":actual)+"）";
        if(verified)return "实测阻力已到 "+target;
        if(acknowledged)return "指令已接收，等待实测阻力到 "+target;
        if(writtenAt>=0)return "目标 "+target+" 已发送，等待单车响应";
        return "目标 "+target+" 正在等待控制通道";
    }
}
