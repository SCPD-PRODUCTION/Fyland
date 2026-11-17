package id.fyland;

/** Host checks for the extra music buttons: which custom action maps to which button, and the repeat cycle. */
public final class ControlChecks {
 static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
 public static void main(String[] args){
  check(MediaKeys.kindOf("SHUFFLE","Shuffle")==MediaKeys.SHUFFLE,"shuffle by name");
  check(MediaKeys.kindOf("x","Acak")==MediaKeys.SHUFFLE,"shuffle in Indonesian");
  check(MediaKeys.kindOf("TOGGLE_REPEAT","")==MediaKeys.REPEAT,"repeat by id");
  check(MediaKeys.kindOf("a","Ulangi")==MediaKeys.REPEAT,"repeat in Indonesian");
  check(MediaKeys.kindOf("a","Add to Liked Songs")==MediaKeys.HEART,"liked songs is the heart, not add");
  check(MediaKeys.kindOf("a","Suka")==MediaKeys.HEART,"like in Indonesian");
  check(MediaKeys.kindOf("a","Add to playlist")==MediaKeys.ADD,"playlist is add");
  check(MediaKeys.kindOf("a","Simpan")==MediaKeys.ADD,"save in Indonesian");
  check(MediaKeys.kindOf("a","Close")==-1,"unrelated action ignored");
  check(MediaKeys.kindOf(null,null)==-1,"nulls ignored");
  check(MediaKeys.activeTitle("Remove from Liked Songs"),"remove means already liked");
  check(MediaKeys.activeTitle("Turn shuffle off"),"turn off means on");
  check(!MediaKeys.activeTitle("Add to Liked Songs"),"add means not yet liked");
  check(!MediaKeys.activeTitle(null),"null title");
  int m=0;int[] seen=new int[3];
  for(int i=0;i<3;i++){m=MediaKeys.nextRepeat(m);seen[i]=m;}
  check(seen[0]==2&&seen[1]==1&&seen[2]==0,"repeat cycles off -> all -> one -> off");
  System.out.println("14 control checks passed: custom-action mapping and repeat cycle.");
 }
}
