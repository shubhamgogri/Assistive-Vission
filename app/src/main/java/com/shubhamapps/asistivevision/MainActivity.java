package com.shubhamapps.asistivevision;

import static android.content.ContentValues.TAG;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.palette.graphics.Palette;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.label.ImageLabel;
import com.google.mlkit.vision.label.ImageLabeler;
import com.google.mlkit.vision.label.ImageLabeling;
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.otaliastudios.cameraview.CameraListener;
import com.otaliastudios.cameraview.CameraView;
import com.otaliastudios.cameraview.PictureResult;
import com.otaliastudios.cameraview.controls.Mode;
import com.shubhamapps.asistivevision.classifier.ClothesDetection;
import com.shubhamapps.asistivevision.classifier.MoneyDetection;
import com.shubhamapps.asistivevision.util.ColorUtils;
import com.shubhamapps.asistivevision.util.Option;
import com.shubhamapps.asistivevision.util.Speech;

import org.tensorflow.lite.task.vision.detector.Detection;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_IMAGE_CAPTURE = 1010;
    private CameraView cameraView;
    private BottomNavigationView bottomNavigationView;
    private ImageView refresh_camera;
    private ImageView capture_image;
    private ImageView upload_image;
    private ImageView image_container;
    private static Option option = Option.IMAGE_CAPTIONING;
    private Speech speech;
    private AppCompatButton show_result;

    private ImageLabeler imageLabeler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        speech = new Speech(MainActivity.this);

        imageLabeler = ImageLabeling.getClient(new ImageLabelerOptions.Builder().
                setConfidenceThreshold(0.8f)
                .build());

        cameraView = findViewById(R.id.face_detection_camera_view);
        cameraView.setLifecycleOwner(this);
        cameraView.setMode(Mode.PICTURE);

        upload_image = findViewById(R.id.upload_file);
        refresh_camera = findViewById(R.id.rotate_camera);
        capture_image = findViewById(R.id.capture_image);
        image_container = findViewById(R.id.image_container);
        show_result = findViewById(R.id.show_result);

        bottomNavigationView = findViewById(R.id.bottomNavigationView);
        bottomNavigationView.setOnItemSelectedListener(navigation);
        bottomNavigationView.setOnItemReselectedListener(navigation_reselected);

        refresh_camera.setOnClickListener(v -> {
            cameraView.open();
            image_container.setBackgroundColor(Color.TRANSPARENT);
            image_container.setImageBitmap(null);
            show_result.setText("");
            speech.stop();
        });

        capture_image.setOnClickListener((view) -> {
            cameraView.takePicture();
        });

        upload_image.setOnClickListener(v -> {

            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            startActivityForResult(intent, REQUEST_IMAGE_CAPTURE);
            image_container.setVisibility(View.VISIBLE);
        });

        cameraView.addCameraListener(new CameraListener() {
            @Override
            public void onPictureTaken(@NonNull PictureResult result) {
                Log.d("TAG", "onPictureTaken: Picture taken");
                super.onPictureTaken(result);

                if(speech.isSpeaking()){
                    speech.stop();
                }
                Bitmap bitmap = BitmapFactory.decodeByteArray(result.getData(), 0,result.getData().length);
                cameraView.close();
                bitmap = rotateBitmap(bitmap);
                afterImage(bitmap);
                image_container.setBackgroundColor(Color.BLACK);
                image_container.setImageBitmap(bitmap);
            }
        });

    }

    private Bitmap rotateBitmap(Bitmap bitmap){
        Matrix matrix = new Matrix();
        matrix.postRotate(90);
        return Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),matrix,true);
    }

    private void clothes(Bitmap bit){
        ClothesDetection clothesDetection = new ClothesDetection(MainActivity.this);
        List<Detection> clothes_infer = clothesDetection.clothes_infer(bit);
//        Log.d(TAG, "clothes_infer: Color " + colorIdentification(bit));
        if(!clothes_infer.isEmpty() ){
            speech.setMessage(clothesDetection.getResult(clothes_infer, bit));
            show_result.setText(clothesDetection.getResult(clothes_infer, bit));
            image_container.setImageBitmap(clothesDetection.createImage(clothes_infer,bit));
        }else {
            speech.setMessage("No Clothes Recognised");
            show_result.setText("No Clothes Recognised");
            image_container.setImageBitmap(bit);
        }
    }

    private void money_infer(Bitmap bitmap){
        MoneyDetection detection = new MoneyDetection(MainActivity.this);
        List<Detection> result = detection.money_infer(bitmap);
        if(result!=null){
            List<StringBuilder> moneyResult = detection.getMoneyResult(result);
            show_result.setText(moneyResult.get(0));
            speech.setMessage(String.valueOf(moneyResult.get(1)));
            image_container.setImageBitmap(detection.mutateImage(result,bitmap));
        }else{
            show_result.setText("No Money detected");
            speech.setMessage("No Money detected");
            image_container.setImageBitmap(bitmap);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data)  {
        super.onActivityResult(requestCode, resultCode, data);
        cameraView.close();

        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            assert data != null;
//            Bundle extras = data.getExtras();
            Uri uri = data.getData();
            Bitmap bit = null;
            try {
                bit = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
            } catch (IOException e) {
                e.printStackTrace();
            }

            if(speech.isSpeaking()){
                speech.stop();
            }

            image_container.setImageBitmap(bit);
            image_container.setBackgroundColor(Color.BLACK);
            afterImage((bit));
        }
    }

    private void afterImage(Bitmap bit){
        if (option.name().equals(Option.IMAGE_CAPTIONING.name())){
            Log.d("afterImage", "afterImage: Image captioning" );
            imageClasifier_mlkit(bit, 0);

        }else if (option.name().equals(Option.MONEY_CALCULATOR.name())) {
            Log.d("afterImage", "afterImage: money" );
            money_infer(bit);

        } else if (option.name().equals(Option.TEXT_RECOGNITION.name())) {
            Log.d("afterImage", "afterImage: text" );
            textRecogntition(bit);

        }else if(option.name().equals(Option.Cloth_PATTERN.name())){
            Log.d(TAG, "afterImage: cloth");

            clothes(bit);
        }

    }

    private void textRecogntition(Bitmap bit) {
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        InputImage inputImage = InputImage.fromBitmap(bit,0);
        Task<Text> result = recognizer.process(inputImage)
                .addOnSuccessListener(new OnSuccessListener<Text>() {
                    @Override
                    public void onSuccess(@NonNull Text text) {
//                        show_result.setText("Speaking...");
                        cameraView.close();
                        image_container.setBackgroundColor(Color.BLACK);

                        Paint paint = new Paint();
                        paint.setColor(Color.BLUE);
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(15);
                        if (text.getTextBlocks().size()>1){
                            Bitmap mutableImage = bit.copy(Bitmap.Config.ARGB_8888, true);
                            Canvas canvas = new Canvas(mutableImage);

                            for (Text.TextBlock block: text.getTextBlocks()){
                                Rect blockFrame = block.getBoundingBox();
                                canvas.drawRect(blockFrame, paint);
                                image_container.setImageBitmap(mutableImage);
                            }
                            speech.setMessage(text.getText());
                            show_result.setText("Speaking ...");
                            if (!speech.isSpeaking()){
                               show_result.setText("");
                            }
                        }

                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.d("TEXT_RECOGNITION", "onFailure: "+ e.getMessage());
                    }
                });
    }

    @SuppressLint("NonConstantResourceId")
    NavigationBarView.OnItemSelectedListener navigation = item -> {
        switch (item.getItemId()) {
            case R.id.htr:
                option = Option.TEXT_RECOGNITION;
                speech.speak_option(option.toString());
                Toast.makeText(MainActivity.this, option.toString(), Toast.LENGTH_SHORT).show();
                return true;
            case R.id.icc:

                option = Option.MONEY_CALCULATOR;
                speech.speak_option(option.toString());
                Toast.makeText(MainActivity.this, option.toString(), Toast.LENGTH_SHORT).show();
                return true;
            case R.id.cpr:
                option = Option.Cloth_PATTERN;
                speech.speak_option(option.toString());
                Toast.makeText(MainActivity.this, option.toString(), Toast.LENGTH_SHORT).show();
                return true;

            case R.id.ric:
                option = Option.IMAGE_CAPTIONING;
                speech.speak_option(option.toString());
                Toast.makeText(MainActivity.this, option.toString(), Toast.LENGTH_SHORT).show();
                return true;
            default:
                speech.speak_option(option.toString());
                Toast.makeText(MainActivity.this, option.toString(), Toast.LENGTH_SHORT).show();
        }
        return false;
    };

    NavigationBarView.OnItemReselectedListener navigation_reselected = item -> {
        switch (item.getItemId()) {
            case R.id.htr:
                option = Option.TEXT_RECOGNITION;
                speech.speak_option(option.toString());
                Toast.makeText(MainActivity.this, option.toString(), Toast.LENGTH_SHORT).show();
                break;

            case R.id.icc:
                option = Option.MONEY_CALCULATOR;
                speech.speak_option(option.toString());
                Toast.makeText(MainActivity.this, option.toString(), Toast.LENGTH_SHORT).show();
                break;

            case R.id.cpr:
                option = Option.Cloth_PATTERN;
                speech.speak_option(option.toString());
                Toast.makeText(MainActivity.this, option.toString(), Toast.LENGTH_SHORT).show();
                break;

            case R.id.ric:
                option = Option.IMAGE_CAPTIONING;
                speech.speak_option(option.toString());
                Toast.makeText(MainActivity.this, option.toString(), Toast.LENGTH_SHORT).show();
                break;
        }
    };

    private void imageClasifier_mlkit(Bitmap bitmap,int rotation) {
        InputImage image = InputImage.fromBitmap(bitmap, rotation);
        imageLabeler.process(image)
                .addOnSuccessListener(new OnSuccessListener<List<ImageLabel>>() {
                    @Override
                    public void onSuccess(@NonNull List<ImageLabel> imageLabels) {
                        if (!imageLabels.isEmpty()){
                            StringBuilder builder = new StringBuilder();
                            builder.append("The picture contains");

                            for (int i = 0; i <imageLabels.size() ; i++) {
                                builder.append(" ");
                                builder.append(imageLabels.get(i).getText());
                                Log.d("Real", "onSuccess: " +imageLabels.get(i).getText()
                                        + " "+ imageLabels.get(i).getConfidence());
                                builder.append(i == imageLabels.size()-1 ? ".":",");
                            }
                            cameraView.close();
                            image_container.setBackgroundColor(Color.BLACK);
                            show_result.setText(builder);
                            speech.setMessage(String.valueOf(builder));
                        }else{
                            Log.d("ImageLabeling", "onSuccess: NO PREDICTIONS");
                            show_result.setText("No Classification");
                            speech.setMessage("No Classification");
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        e.printStackTrace();
                    }
                });

    }

    String getSentence(List<String> input){

        RequestQueue queue = Volley.newRequestQueue(MainActivity.this);
        String url = "";

        StringRequest stringRequest = new StringRequest(Request.Method.POST, url, new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                Log.d(TAG, "onResponse: " + response);
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.d(TAG, "onErrorResponse: " +error.getMessage());
            }
        }) ;
        queue.add(stringRequest);

        return "";
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        speech.destroy();
        cameraView=null;
    }

}