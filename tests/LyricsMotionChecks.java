package id.fyland;

public final class LyricsMotionChecks {
 static int count;
 static void check(boolean value,String label){count++;if(!value)throw new AssertionError(label);}
 static void near(float actual,float expected,String label){check(Math.abs(actual-expected)<.0001f,label);}
 public static void main(String[] args){
  LyricsDocument document=LyricsDocument.parse("[00:01]Synthetic first line\n[00:05]Synthetic next line\n[00:09]Final test line","");
  near(LyricsTimeline.reveal(document,0,999,12000),0,"No light before line begins");
  near(LyricsTimeline.reveal(document,0,1000,12000),0,"Do not reveal whole line at boundary");
  // "Synthetic first line" has 20 characters: 1600 ms of singing inside a 4000 ms gap, so the sweep ends before the next line.
  near(LyricsTimeline.reveal(document,0,1400,12000),.25f,"Reveal one quarter in time");
  near(LyricsTimeline.reveal(document,0,1800,12000),.5f,"Reveal half in time");
  near(LyricsTimeline.reveal(document,0,2200,12000),.75f,"Reveal three quarters in time");
  near(LyricsTimeline.reveal(document,0,2600,12000),1,"Line finishes when its singing time is over, not at the next timestamp");
  near(LyricsTimeline.reveal(document,0,4900,12000),1,"Light stays full during the instrumental gap");
  near(LyricsTimeline.reveal(document,1,5000,12000),0,"Next line starts a fresh sweep");
  near(LyricsTimeline.reveal(document,1,5760,12000),.5f,"Seek recalculates fraction directly");
  near(LyricsTimeline.reveal(document,2,9600,12000),.5f,"Final line uses its own singing time");
  near(LyricsTimeline.reveal(document,2,9600,0),.5f,"Unknown track length uses bounded last-line estimate");
  near(LyricsTimeline.reveal(document,0,100000,12000),1,"Progress clamps after end");
  check(LyricsTimeline.singSpan("a very fast line of lyrics here",2000)==2000,"A line sung almost non-stop uses the whole gap");
  check(LyricsTimeline.singSpan("hi",8000)==900,"Very short lines still take a moment");
  near(LyricsTimeline.reveal(document,-1,1000,12000),0,"No active lyric produces no reveal");
  near(LyricsTimeline.reveal(null,0,1000,12000),0,"Absent document is safe");
  LyricsDocument duplicates=LyricsDocument.parse("[00:01]a\n[00:01]b\n[00:05]c","");
  near(LyricsTimeline.reveal(duplicates,0,1450,8000),.5f,"Duplicate timestamps do not divide by zero");
  check(LyricsTimeline.scrollTarget(200,200,1000)==146,"Keep active line near upper third");
  check(LyricsTimeline.scrollTarget(10,200,1000)==0,"First lines do not scroll above start");
  check(LyricsTimeline.scrollTarget(980,200,1000)==800,"Final lines do not scroll past end");
  check(LyricsTimeline.scrollTarget(20,200,80)==0,"Short document has no empty overscroll");
  System.out.println(count+" lyric motion checks passed: gradual reveal, seeking, final-line timing and scroll limits.");
 }
}
