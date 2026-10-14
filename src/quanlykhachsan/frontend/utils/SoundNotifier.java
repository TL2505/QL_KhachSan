package quanlykhachsan.frontend.utils;

import javax.sound.sampled.*;

public final class SoundNotifier {
    private SoundNotifier(){ }
    public static void settled(){Thread worker=new Thread(()->{try{
        float rate=22050;int samples=(int)(rate*.22);byte[] pcm=new byte[samples*2];for(int i=0;i<samples;i++){double envelope=Math.sin(Math.PI*i/samples);short sample=(short)(Math.sin(2*Math.PI*880*i/rate)*envelope*4000);pcm[i*2]=(byte)sample;pcm[i*2+1]=(byte)(sample>>8);}
        Clip clip=AudioSystem.getClip();clip.addLineListener(event->{if(event.getType()==LineEvent.Type.STOP)clip.close();});clip.open(new AudioFormat(rate,16,1,true,false),pcm,0,pcm.length);clip.start();
    }catch(Exception ignored){/* Audio is optional; visual settlement remains authoritative. */}},"hotel-settlement-chime");worker.setDaemon(true);worker.start();}
}
