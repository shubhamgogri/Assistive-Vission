package com.shubhamapps.asistivevision.classifier;

import static android.content.ContentValues.TAG;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.AsyncTask;
import android.util.Log;

import androidx.palette.graphics.Palette;

import com.shubhamapps.asistivevision.ml.Efficientnetlite4specModel;
import com.shubhamapps.asistivevision.util.ColorUtils;

import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.task.core.BaseOptions;
import org.tensorflow.lite.task.vision.detector.Detection;
import org.tensorflow.lite.task.vision.detector.ObjectDetector;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ClothesDetection {
    private static final String CLOTHES_TFLITE = "KurtaShirtSherwani.tflite";
    private static final String FINAL = "final_cloth_detection.tflite";
    private Context context;

    public ClothesDetection(Context context) {
        this.context = context;
    }

    public List<Detection> clothes_infer(Bitmap bitmap){
        ObjectDetector.ObjectDetectorOptions options = ObjectDetector.ObjectDetectorOptions.builder()
                .setBaseOptions(BaseOptions.builder().build())
                .build();

        try {
            ObjectDetector objectDetector = ObjectDetector.createFromFileAndOptions(context,
                    FINAL,options);
            List<Detection> result = objectDetector.detect(TensorImage.fromBitmap(bitmap));

            Log.d("Result Successful", "Clothes infer: " + result.size() + result.isEmpty());
            List<Detection> filter = new ArrayList<>();

            for (Detection d:result) {
                if (d.getCategories().get(0).getScore() >= 0.75) {
                    filter.add(d);
                }
            }
            return filter;
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    public Bitmap createImage(List<Detection> filter,Bitmap bitmap){
        Bitmap mutableImage = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        for (Detection d: filter ) {
            boundingBox(mutableImage, d);
        }
        return mutableImage;
    }

    public String getResult(List<Detection> filter, Bitmap bitmap){
        StringBuilder builder = new StringBuilder();

        if(!filter.isEmpty()){
            builder.append("There is ");

            bitmap = cropImage(bitmap, filter.get(0).getBoundingBox());
            String s = pattern_infer(bitmap);

//TODO THis where you left...
            Palette p = Palette.from(bitmap).generate();
            int dominantColor = p.getDominantColor(0x000000);
            Log.d(TAG, "colorIdentification: " +dominantColor);
            ColorUtils colorUtils = new ColorUtils();
            String colorNameFromHex = colorUtils.getColorNameFromHex(dominantColor);
            Log.d(TAG, "getResult: " + colorNameFromHex);
            builder.append(colorNameFromHex);

            for (Detection d: filter ) {
                Log.d(TAG, "Clothes infer: " + d.getCategories() + d.getBoundingBox());

                builder.append(" ");
                builder.append(" Coloured ");
                builder.append(s);
                builder.append(" ");
                builder.append(d.getCategories().get(0).getLabel());
                builder.append(" ");
            }

        }else {
            builder.append("No Clothes Recognized");
        }

        return builder.toString();
    }


    private String pattern_infer(Bitmap bitmap){
        String result = "";
        try {
            Efficientnetlite4specModel model = Efficientnetlite4specModel.newInstance(context);

            // Creates inputs for reference.
            TensorImage image = TensorImage.fromBitmap(bitmap);

            // Runs model inference and gets result.
            Efficientnetlite4specModel.Outputs outputs = model.process(image);
            List<Category> probability = outputs.getProbabilityAsCategoryList();
            Log.d(TAG, "pattern_infer: " + Arrays.toString(probability.toArray()));

            result = pattern_result(probability);
            // Releases model resources if no longer used.
            model.close();
        } catch (IOException e) {
            // TODO Handle the exception
        }

        return result;
    }

    private String pattern_result(List<Category> probability) {
        String result = "";
        for (Category c: probability) {
            if (c.getScore() >= 0.75) {
                result += c.getLabel();
            }
        }
        return result;
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
    public Bitmap cropImage(Bitmap bitmap, RectF boundingBox) {

        int centerX = (int) boundingBox.centerX();
        int centerY = (int) boundingBox.centerY();
        int width = (int) boundingBox.width()/4;
        int height = (int) boundingBox.height()/4;

        int x = centerX - width;
        int y =  centerY - height ;

        return Bitmap.createBitmap(bitmap,
                x,
                y,
                (int) (width*2),
                (int) (height*2),
                null,false);
    }

    private ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap){
        ByteBuffer imgData = ByteBuffer.allocate(4*224*224*3);
        imgData.order(ByteOrder.nativeOrder());

        int []values= new int[224*224];
        bitmap.getPixels(values, 0, bitmap.getWidth(), 0, 0,bitmap.getWidth(), bitmap.getHeight());

        int pixel = 0;
        for (int i = 0; i <224 ; i++) {
            for (int j = 0; j <224 ; j++) {
                final int val = values[pixel++];
                imgData.putFloat(((val >> 16) & 0xFF)/ 255.f);
                imgData.putFloat(((val >> 8) & 0xFF)/ 255.f);
                imgData.putFloat(((val) & 0xFF)/ 255.f);
            }
        }
        return imgData;
    }

}
