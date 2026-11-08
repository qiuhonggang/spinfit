package com.spinfit.bike.core;
import java.util.*;
/** Practical sessions authored for SpinFit; not claimed to be official protocols. */
public final class ProgramLibrary {
 private static final List<TrainingProgram> ALL=build();public static List<TrainingProgram> all(){return ALL;}
 public static TrainingProgram byId(String id){for(TrainingProgram p:ALL)if(p.id.equals(id))return p;return ALL.get(0);}
 private static Segment s(String name,int minutes,float pct,float r,int cad,String hint){return new Segment(name,hint,minutes*60,pct,r,cad,pct<55?1:pct<75?2:pct<90?3:pct<105?4:5);}
 private static TrainingProgram p(String id,String name,String sub,String why,Segment...segs){return new TrainingProgram(id,name,sub,why,false,segs);}
 private static List<TrainingProgram> build(){List<TrainingProgram>a=new ArrayList<>();
 a.add(new TrainingProgram("free","自由骑行","按自己的节奏，随时开始与结束","不设固定目标。先轻松踩踏 5–10 分钟，结束前再慢踩 5 分钟。阻力由你掌控。",true,new Segment[]{s("自由骑行",30,0,15,80,"按自己的节奏，保持顺畅踩踏")}));
 a.add(p("beginner","轻松入门","20 分钟 · 建立第一段骑行习惯","适合刚开始骑行或久未运动。全程坐姿，以能轻松说话为准；踏频是参考，不必追数值。",s("轻松热身",5,40,12,70,"肩膀放松，找到舒适坐姿"),s("找到节奏",10,55,22,75,"均匀踩踏，保持可以对话"),s("慢踩放松",5,35,10,65,"逐渐放轻，让呼吸平稳下来")));
 a.add(p("recovery","舒缓恢复","20 分钟 · 给身体一次主动放松","用于轻松活动或训练间隔日，不用追赶功率。感觉疲劳时也可选择完全休息。",s("慢慢启动",5,35,10,65,"低阻力，慢慢进入状态"),s("轻松转动",10,45,17,75,"体感 2–3 / 10，始终轻松"),s("放松收尾",5,30,8,60,"降低踏频与阻力")));
 a.add(p("endurance","基础有氧","30 分钟 · 舒适而稳定的耐力积累","用稳定的中等体感建立有氧习惯。中等强度可以说话但难以唱歌；%FTP 仅作有功率基础者的参考。",s("渐进热身",5,45,15,75,"轻松开始，逐渐加深呼吸"),s("有氧稳态",20,65,30,80,"呼吸均匀，仍然能够说话"),s("慢踩放松",5,35,10,65,"逐渐降低输出")));
 a.add(p("zone2","有氧耐力","45 分钟 · 延长稳定输出的时间","适合已经能舒适完成 30 分钟的人。不要为追目标硬加阻力；疲劳时缩短训练。",s("渐进热身",5,45,15,75,"轻松进入状态"),s("耐力巡航",35,65,30,85,"坐姿稳定，能够对话"),s("慢踩放松",5,35,10,65,"把呼吸慢慢带回平稳")));
 a.add(p("tempo","节奏进阶","35 分钟 · 两段可控的节奏练习","已有稳定骑行基础后尝试。主段体感约 6 / 10；功率目标根据个人 FTP 换算，阻力档位只作起点。",s("渐进热身",7,45,15,75,"先找到舒适呼吸"),s("节奏一",9,80,38,85,"专注稳定输出，不要冲刺"),s("组间恢复",5,45,15,75,"放轻阻力，恢复呼吸"),s("节奏二",9,80,38,85,"保持坐姿，均匀踩踏"),s("慢踩放松",5,35,10,65,"逐步放轻")));
 a.add(p("interval","有氧间歇","30 分钟 · 4 组一分钟提速","进阶训练，主段体感约 7 / 10，不要求全力。每周最多安排 1–2 次较高强度课，中间至少隔一天轻松活动或休息。",s("渐进热身",8,45,15,75,"充分热身，逐步进入状态"),s("提速一",1,95,42,90,"稳定提速，保持动作可控"),s("恢复一",3,45,15,75,"轻松踩踏"),s("提速二",1,95,42,90,"不必全力，呼吸加深"),s("恢复二",3,45,15,75,"放松肩膀"),s("提速三",1,95,42,90,"坐姿稳定，保持顺畅"),s("恢复三",3,45,15,75,"恢复舒适呼吸"),s("提速四",1,95,42,90,"最后一组，动作不变形"),s("恢复四",3,45,15,75,"逐渐放轻"),s("慢踩放松",6,35,10,65,"耐心收尾，不要突然停下")));
 a.add(p("sweetspot","甜点区间","40 分钟 · 2 × 8 分钟专注输出","适合已知可靠 FTP 且有训练基础者。主段约 88% FTP，体感约 7 / 10。若目标不适合，直接切换手动降低阻力。",s("渐进热身",10,50,18,80,"充分热身，准备主训练"),s("专注输出一",8,88,40,85,"均匀输出，不追求峰值"),s("组间恢复",4,45,15,75,"缓慢恢复"),s("专注输出二",8,88,40,85,"保持同样节奏"),s("慢踩放松",10,35,10,65,"逐渐降低呼吸与心率")));
 List<Segment> hiit=new ArrayList<>();
 hiit.add(s("渐进热身",7,45,15,75,"轻松进入状态，最后两分钟逐渐加快踏频"));
 for(int i=1;i<=6;i++){
  hiit.add(s("高强度 "+i+"/6",1,115,45,90,"体感 8–9 / 10，保持坐姿与动作稳定，不做全力冲刺"));
  hiit.add(s("主动恢复 "+i+"/6",2,45,15,70,"降低阻力，轻松踩踏，让呼吸逐渐恢复"));
 }
 hiit.add(s("慢踩放松",5,35,10,65,"逐步降低输出，不要突然停下"));
 a.add(p("hiit30","HIIT 30 分钟","6 组 · 1 分钟高强度 / 2 分钟恢复","适合已有稳定骑行基础的人。7 分钟热身，6 组高强度与主动恢复，最后放松 5 分钟。高强度段参考 115% FTP，体感 8–9 / 10；恢复段应明显轻松。自动阻力按阶段调整参考档位，不是功率闭环；若 FTP 未实测，请以体感为准，无法保持动作时切换手动降低阻力。两次高强度课之间安排恢复日。",hiit.toArray(new Segment[0])));
 return Collections.unmodifiableList(a);}
 public static boolean advanced(TrainingProgram p){return p.id.equals("tempo")||p.id.equals("interval")||p.id.equals("sweetspot")||p.id.equals("hiit30");}
 public static String level(TrainingProgram p){return p.freeRide?"自主":advanced(p)?"进阶":"基础";}
 public static String effort(TrainingProgram p){return p.id.equals("hiit30")?"高强度":p.freeRide?"自主强度":p.id.equals("beginner")||p.id.equals("recovery")?"轻松":advanced(p)?"中高强度":"中等强度";}
}
