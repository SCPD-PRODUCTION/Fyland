package id.fyland;

import android.view.Choreographer;

/** Drives a SpringSim on the display frame clock. Main thread only. */
final class Spring {
 interface Tick{void on(float value,boolean done);}
 final SpringSim sim;
 Tick tick;boolean running;long last;
 Spring(float stiffness,float dampingRatio){sim=new SpringSim(stiffness,dampingRatio);}
 final Choreographer.FrameCallback frame=new Choreographer.FrameCallback(){public void doFrame(long nanos){
  if(!running)return;
  float dt=last==0?1f/60f:Math.min(0.034f,(nanos-last)/1000000000f);last=nanos;
  boolean more=sim.step(dt);
  Tick t=tick;if(t!=null)t.on(sim.value,!more);
  if(more&&running)Choreographer.getInstance().postFrameCallback(this);else running=false;
 }};
 /** Restarts from 0 toward 1. Safe to call while running. */
 void start(Tick t){tick=t;sim.reset();last=0;if(!running){running=true;Choreographer.getInstance().postFrameCallback(frame);}}
 void cancel(){running=false;tick=null;Choreographer.getInstance().removeFrameCallback(frame);}
 boolean running(){return running;}
}
