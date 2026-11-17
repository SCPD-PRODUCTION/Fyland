package id.fyland;

/** Damped spring that travels from 0 to 1. Fixed sub-steps keep it stable. Pure Java, so it is checked on the host. */
final class SpringSim {
 float value,velocity,target=1f;
 final float stiffness,damping;
 SpringSim(float stiffness,float dampingRatio){this.stiffness=stiffness;this.damping=2f*dampingRatio*(float)Math.sqrt(stiffness);}
 void reset(){value=0f;velocity=0f;}
 /** Advances by dt seconds. Returns true while the spring is still moving. */
 boolean step(float dt){
  if(dt<=0f)return true;
  int n=Math.max(1,(int)Math.ceil(dt*240f));float h=dt/n;
  for(int i=0;i<n;i++){float a=-stiffness*(value-target)-damping*velocity;velocity+=a*h;value+=velocity*h;}
  if(Math.abs(value-target)<0.002f&&Math.abs(velocity)<0.02f){value=target;velocity=0f;return false;}
  return true;
 }
}
