package id.fyland;

/** Line-time based reveal. It does not claim word timestamps the source did not supply, but it does not spread the light over instrumental gaps either: the sweep lasts about as long as the line takes to sing. */
final class LyricsTimeline {
 private LyricsTimeline(){}
 static float reveal(LyricsDocument document,int line,long position,long duration){
  if(document==null||line<0||line>=document.lines.size())return 0;
  long start=document.lines.get(line).at,end=0;
  for(int next=line+1;next<document.lines.size();next++)if(document.lines.get(next).at>start){end=document.lines.get(next).at;break;}
  if(end<=start)end=duration>start?duration:start+4000;
  long span=singSpan(document.lines.get(line).text,end-start);
  return Math.max(0f,Math.min(1f,(float)((double)(position-start)/span)));
 }
 /** How long the sweep takes: ~80 ms per character (a normal singing pace, at least 0.9 s), or the whole gap to the next line when the line is sung almost non-stop. */
 static long singSpan(String text,long gap){
  long estimate=Math.max(900,(text==null?0:text.length())*80L);
  return estimate>=gap*0.8?gap:estimate;
 }
 static int scrollTarget(int lineTop,int viewportHeight,int contentHeight){return scrollTarget(lineTop,viewportHeight,contentHeight,.27f);}
 static int scrollTarget(int lineTop,int viewportHeight,int contentHeight,float anchor){
  return Math.max(0,Math.min(Math.max(0,contentHeight-viewportHeight),lineTop-Math.round(viewportHeight*anchor)));
 }
}
