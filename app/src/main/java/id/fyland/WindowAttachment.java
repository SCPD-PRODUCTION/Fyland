package id.fyland;

/** Separates attaching a window from updating its contents; updates must never reattach it. */
final class WindowAttachment {
 private boolean attached;
 boolean attached(){return attached;}
 void show(Runnable attach,Runnable update){if(attached)update.run();else{attach.run();attached=true;}}
 /** Forget the window without touching it, after the system dropped it behind our back. */
 void reset(){attached=false;}
 void hide(Runnable detach){if(attached){attached=false;detach.run();}}
}
