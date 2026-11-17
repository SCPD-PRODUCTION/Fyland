package id.fyland;

public final class MotionChecks {
 static int count;
 static void check(boolean value,String name){count++;if(!value)throw new AssertionError(name);}
 public static void main(String[] args){
  // Spring: converges, overshoots a little (bouncy), never explodes, independent of frame rate.
  for(float dt:new float[]{1f/30f,1f/60f,1f/90f,1f/120f}){
   SpringSim s=new SpringSim(420f,.62f);s.reset();float peak=0;int frames=0;boolean moving=true;
   while(moving&&frames<600){moving=s.step(dt);peak=Math.max(peak,s.value);frames++;}
   check(!moving,"Spring settles at dt="+dt);
   check(s.value==1f,"Spring ends exactly on target at dt="+dt);
   check(peak>1.0f&&peak<1.25f,"Spring overshoots slightly but not wildly at dt="+dt+" peak="+peak);
   check(frames*dt<1.6f,"Spring settles in under 1.6s at dt="+dt);
  }
  SpringSim critical=new SpringSim(300f,1.0f);critical.reset();float cpeak=0;boolean cm=true;int cf=0;
  while(cm&&cf<600){cm=critical.step(1f/60f);cpeak=Math.max(cpeak,critical.value);cf++;}
  check(cpeak<=1.01f,"Critically damped spring does not overshoot");
  check(new SpringSim(300f,1f).step(0f),"Zero dt does nothing");
  // Wave taper: full away from the thumb, flat at the thumb, monotonic.
  check(WaveMath.taper(1000f,40f)==1f,"Full amplitude far from thumb");
  check(WaveMath.taper(0f,40f)==0f,"Flat at the thumb");
  check(WaveMath.taper(-5f,40f)==0f,"Flat past the thumb");
  float prev=-1f;boolean mono=true;for(int i=0;i<=40;i++){float t=WaveMath.taper(i,40f);if(t<prev)mono=false;prev=t;}
  check(mono,"Taper rises monotonically away from the thumb");
  check(WaveMath.taper(10f,0f)==1f,"Zero taper length means no taper");
  // Wave shape and flow direction.
  check(Math.abs(WaveMath.y(0f,0f,30f,0f,3f))<1e-4f,"Wave starts on the centre line");
  check(Math.abs(WaveMath.y(7.5f,0f,30f,0f,3f)-3f)<1e-3f,"Quarter wavelength reaches the crest");
  float crest0=7.5f;float yMoved=WaveMath.y(crest0+4f,0f,30f,WaveMath.advance(0f,.1f,1.2f)* 1f,3f);
  check(WaveMath.y(crest0,0f,30f,0f,3f)>=yMoved-1e-3f,"Crest leaves its old spot");
  float ph=WaveMath.advance(0f,.3f,1.2f);float shifted=WaveMath.y(7.5f+.3f/1.2f*30f,0f,30f,ph,3f);
  check(Math.abs(shifted-3f)<1e-2f,"Positive phase moves the wave toward larger x at wavelength/period speed");
  float p=0f;for(int i=0;i<2000;i++)p=WaveMath.advance(p,1f/60f,1.2f);
  check(p>=0f&&p<2*Math.PI+1e-3,"Phase stays wrapped");
  check(WaveMath.approach(0f,1f,10f,8f)>.99f,"Approach converges");
  check(WaveMath.approach(0f,1f,1f/60f,8f)>0f&&WaveMath.approach(0f,1f,1f/60f,8f)<.2f,"Approach moves gently per frame");
  check(WaveMath.fraction(30,100)==.3f,"Fraction");
  check(WaveMath.fraction(500,100)==1f&&WaveMath.fraction(-5,100)==0f&&WaveMath.fraction(5,0)==0f,"Fraction clamps and handles unknown duration");
  // Call buttons.
  check(CallActions.role("Answer")==CallActions.ANSWER,"Answer");
  check(CallActions.role("Jawab")==CallActions.ANSWER,"Jawab");
  check(CallActions.role("Terima")==CallActions.ANSWER,"Terima");
  check(CallActions.role("Decline")==CallActions.DECLINE,"Decline");
  check(CallActions.role("Tolak")==CallActions.DECLINE,"Tolak");
  check(CallActions.role("Hang up")==CallActions.DECLINE,"Hang up");
  check(CallActions.role("Akhiri")==CallActions.DECLINE,"Akhiri");
  check(CallActions.role("Send")==CallActions.UNKNOWN&&CallActions.role("Mute")==CallActions.UNKNOWN&&CallActions.role(null)==CallActions.UNKNOWN,"Unrelated buttons stay unknown");
  System.out.println(count+" motion checks passed");
 }
}
