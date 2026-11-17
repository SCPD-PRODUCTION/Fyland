package id.fyland;

public final class FeatureChecks {
 static int count;
 static void check(boolean value,String name){count++;if(!value)throw new AssertionError(name);}
 public static void main(String[] args){
  LayoutGeometry.Result fit=LayoutGeometry.fit(360,220,180,60,104);
  check(fit.width==152,"Shrink notification island to free center gap");
  check(fit.center-fit.width/2>=60,"Do not overlap clock reservation");
  check(fit.center+fit.width/2<=256,"Do not overlap network and battery reservation");
  fit=LayoutGeometry.fit(360,152,340,60,104);
  check(fit.center+fit.width/2<=256,"User offset remains inside reserved boundaries");
  fit=LayoutGeometry.fit(360,152,-100,60,104);
  check(fit.center-fit.width/2>=60,"Negative user offset remains bounded");
  check(LayoutGeometry.fit(360,220,180,200,200).width==0,"No gap means no overlapping island");
  for(int screen:new int[]{320,360,412,720,1080})for(int width:new int[]{90,136,152,220,600})for(int center:new int[]{-20,screen/2,screen+20}){
   LayoutGeometry.Result result=LayoutGeometry.fit(screen,width,center,60,104);
   check(result.width==0||(result.center-result.width/2>=60&&result.center+result.width/2<=screen-104),"Bounds on multiple screens and offsets");
  }
  LyricsDocument doc=LyricsDocument.parse("[ar:Synthetic test]\n[00:01.2]alpha\n[00:02.34][00:05.678]beta\n[00:03]gamma","");
  check(doc.lines.size()==4,"Multiple timestamps become separate lines");
  check(doc.lines.get(0).at==1200,"Tenths are milliseconds");
  check(doc.lines.get(1).at==2340,"Hundredths are milliseconds");
  check(doc.lines.get(3).at==5678,"Thousandths preserved and lines sorted");
  check(doc.indexAt(1199)==-1,"Do not show line before its start");
  check(doc.indexAt(1200)==0,"Exact line boundary");
  check(doc.indexAt(4000)==2,"Seek into middle picks matching lyric");
  check(doc.indexAt(9000)==3,"After last line remains at last lyric");
  doc=LyricsDocument.parse("[offset:-500]\n[00:00.20]first\n[00:02.00]second","");
  check(doc.lines.get(0).at==0,"Negative LRC offset clamps to zero");
  check(doc.lines.get(1).at==1500,"LRC offset applied");
  doc=LyricsDocument.parse("[00:75]invalid\n[00:02]valid\n[al:metadata]","");
  check(doc.lines.size()==1,"Ignore invalid seconds and metadata");
  doc=LyricsDocument.parse(null,"Synthetic plain lyric");
  check(doc.lines.isEmpty()&&doc.plain.equals("Synthetic plain lyric"),"Plain lyrics work without timestamps");
  check(LyricsDocument.parse(null,null).indexAt(1000)==-1,"Absent lyric returns no current line");
  System.out.println(count+" feature checks passed: status-bar gap, LRC timing, seek and plain-text fallback.");
 }
}
