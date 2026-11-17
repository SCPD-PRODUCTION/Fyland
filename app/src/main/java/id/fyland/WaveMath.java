package id.fyland;

/** Geometry of the flowing progress wave. Pure Java so the shape rules are checked on the host. */
final class WaveMath {
 private WaveMath(){}
 /** 0..1 multiplier for a point `distance` before the playhead; the wave flattens smoothly into the thumb. */
 static float taper(float distance,float length){
  if(length<=0f)return 1f;
  float t=Math.max(0f,Math.min(1f,distance/length));
  return t*t*(3f-2f*t);
 }
 /** Wave height at x. A positive phase moves the wave toward larger x (toward the playhead). */
 static float y(float x,float left,float wavelength,float phase,float amplitude){
  return amplitude*(float)Math.sin(2.0*Math.PI*(x-left)/wavelength-phase);
 }
 static float advance(float phase,float dt,float periodSeconds){
  double p=phase+2.0*Math.PI*dt/periodSeconds;
  return (float)(p%(2.0*Math.PI));
 }
 /** Exponential approach, independent of frame rate. */
 static float approach(float current,float target,float dt,float rate){
  return current+(target-current)*(1f-(float)Math.exp(-rate*dt));
 }
 static float fraction(long positionMs,long durationMs){
  if(durationMs<=0)return 0f;
  return Math.max(0f,Math.min(1f,(float)positionMs/(float)durationMs));
 }
}
