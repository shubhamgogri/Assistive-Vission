package com.shubhamapps.asistivevision.classifier;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.task.core.BaseOptions;
import org.tensorflow.lite.task.vision.detector.Detection;
import org.tensorflow.lite.task.vision.detector.ObjectDetector;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

public class MoneyDetection {
    private static final String MONEY_TFLITE = "Money.tflite";
    private Context context;

    public MoneyDetection(Context context){
        this.context = context;
    }

    public List<Detection> money_infer(Bitmap bitmap){
        ObjectDetector.ObjectDetectorOptions options = ObjectDetector.ObjectDetectorOptions.builder()
                .setBaseOptions(BaseOptions.builder().build())
                .build();

        try {
            ObjectDetector objectDetector = ObjectDetector.createFromFileAndOptions(context, MONEY_TFLITE,options);
            List<Detection> result = objectDetector.detect(TensorImage.fromBitmap(bitmap));
            return result;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
    private void boundingBox(Bitmap bitmap, Detection d) {
        RectF box = d.getBoundingBox();
        Paint paint = new Paint();
        paint.setColor(Color.YELLOW);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawRect(box, paint);
    }

    public Bitmap mutateImage (List<Detection> result, Bitmap bitmap){
            Bitmap mutableImage = bitmap.copy(Bitmap.Config.ARGB_8888, true);

            for (Detection d: result ) {
                if(d.getCategories().get(0).getScore()>=0.75){
                    boundingBox(mutableImage, d);
                }
            }
            return mutableImage;
    }
    public List<StringBuilder> getMoneyResult(List<Detection> result) {

        HashMap<Integer,Integer> count = new HashMap<>();

        for (Detection d: result) {
            Category c =  d.getCategories().get(0);
            int label = Integer.parseInt(c.getLabel());
            if(c.getScore() >= 0.75){
                if (count.containsKey(label)){
                    count.put((label), count.get((label)) + 1);
                }else {
                    count.put(label, 1);
                }
            }
        }
        TreeMap<Integer, Integer> sorted = new TreeMap<>(count);
        AtomicInteger total = new AtomicInteger();
        StringBuilder builder = new StringBuilder();
        StringBuilder speech_text = new StringBuilder();
        speech_text.append("There are ");

        if (!sorted.isEmpty()){
            sorted.forEach((key, value) -> {
                builder.append("Rs. ");
                builder.append(key);
                builder.append(" x ");
                builder.append(value);
                builder.append(" = ");
                builder.append(key*value);
                builder.append("\n");

                total.set(total.get() + (key * value));

                speech_text.append(value);
                speech_text.append(" notes of");
                speech_text.append(" ");
                speech_text.append(key);
                speech_text.append(" ");

            });
            builder.append("Total:");
            builder.append("Rs.");
            builder.append(total);

            speech_text.append("Total ");
            speech_text.append(total);
            speech_text.append("Rs.");

        }else{
            builder.append("No Classification.");
            speech_text.append("no Classifications.");
        }
        List<StringBuilder> out = new ArrayList<>();
        out.add(builder);
        out.add(speech_text);
        return out;
    }

}
