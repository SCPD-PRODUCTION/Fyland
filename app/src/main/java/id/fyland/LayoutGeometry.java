package id.fyland;

/** A bounded gap for the island; never pretends to move Android's SystemUI icons. */
final class LayoutGeometry {
 static final class Result {
  final int width,center;
  Result(int width,int center){this.width=width;this.center=center;}
 }
 static Result fit(int screen,int wantedWidth,int wantedCenter,int reservedLeft,int reservedRight){
  int left=Math.max(0,Math.min(screen,reservedLeft));
  int right=Math.max(left,screen-Math.max(0,reservedRight));
  int gap=right-left;
  if(gap<48)return new Result(0,screen/2);
  int center=Math.max(left+24,Math.min(right-24,wantedCenter));
  int width=Math.min(Math.max(0,wantedWidth),2*Math.min(center-left,right-center));
  return new Result(width,center);
 }
}
