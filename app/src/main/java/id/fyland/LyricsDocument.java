package id.fyland;

import java.util.*;
import java.util.regex.*;

/** LRC parsing without Android or network dependencies. */
final class LyricsDocument {
 static final class Line {
  final long at;final String text;
  Line(long at,String text){this.at=at;this.text=text;}
 }
 final List<Line> lines;
 final String plain;
 LyricsDocument(List<Line> lines,String plain){this.lines=Collections.unmodifiableList(lines);this.plain=plain==null?"":plain;}
 static LyricsDocument parse(String synced,String plain){
  List<Line> result=new ArrayList<>();String raw=synced==null?"":synced;
  Matcher offsetMatcher=Pattern.compile("\\[offset:([+-]?\\d+)\\]",Pattern.CASE_INSENSITIVE).matcher(raw);
  long offset=0;if(offsetMatcher.find())try{offset=Long.parseLong(offsetMatcher.group(1));}catch(NumberFormatException ignored){ErrorLog.w(ignored);}
  Pattern stamp=Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?\\]");
  for(String line:raw.split("\\r?\\n")){
   Matcher m=stamp.matcher(line);List<Long> times=new ArrayList<>();int end=0;
   while(m.find()){
    try{long min=Long.parseLong(m.group(1)),sec=Long.parseLong(m.group(2));if(sec>=60)continue;String frac=m.group(3);long fraction=frac==null?0:Long.parseLong((frac+"000").substring(0,3));times.add(Math.max(0,min*60000+sec*1000+fraction+offset));end=m.end();}catch(NumberFormatException ignored){ErrorLog.w(ignored);}
   }
   String text=line.substring(end).trim();for(long at:times)result.add(new Line(at,text));
  }
  result.sort(new Comparator<Line>(){public int compare(Line x,Line y){return Long.compare(x.at,y.at);}});return new LyricsDocument(result,plain);
 }
 int indexAt(long ms){int lo=0,hi=lines.size()-1,answer=-1;while(lo<=hi){int mid=(lo+hi)>>>1;if(lines.get(mid).at<=ms){answer=mid;lo=mid+1;}else hi=mid-1;}return answer;}
}
