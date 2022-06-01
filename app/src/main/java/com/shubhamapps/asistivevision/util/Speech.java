package com.shubhamapps.asistivevision.util;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import com.google.mlkit.vision.text.Text;

import java.util.Locale;

public class Speech {
    private Context context;
    private TextToSpeech tts;

    public Speech(Context context) {
        this.context = context;
        tts = new TextToSpeech(this.context, status -> {
            if (status == TextToSpeech.SUCCESS){
                int re = tts.setLanguage(Locale.ENGLISH);
                if (re == TextToSpeech.LANG_NOT_SUPPORTED || re == TextToSpeech.LANG_MISSING_DATA) {
                    Log.d("Speech", "onInit: Lang not Supported");
                }else {
//                    CAll Method to speak
                    Log.d("Speech", "onInit: " + "tts setup Success");
                    String mess ="Front Camera is Running.";
//                    setMessage("Hello! " + Option.IMAGE_CLASSIFIER.name().replace("_", " ") + "is Selected." + mess);
                }
            }
        });
    }

    public void speak_option(String option){
        String message = option.replace("_", " ");
        setMessage(message);
    }
    public void setMessage(String message) {
        if (message !=null && !message.isEmpty()){
            tts.speak(message,TextToSpeech.QUEUE_FLUSH,null);
        }
    }

    public void stop(){
        tts.stop();
    }

    public boolean isSpeaking(){
        return tts.isSpeaking();
    }
    public void destroy() {
        if (tts!=null){
            tts.stop();
            tts.shutdown();
        }
    }
}
