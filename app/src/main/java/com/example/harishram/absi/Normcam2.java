package com.example.harishram.absi;

import android.app.ProgressDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Environment;
import android.speech.tts.TextToSpeech;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;

import com.google.gson.Gson;
import com.microsoft.projectoxford.emotion.EmotionServiceClient;
import com.microsoft.projectoxford.emotion.EmotionServiceRestClient;
import com.microsoft.projectoxford.emotion.contract.RecognizeResult;
import com.microsoft.projectoxford.emotion.rest.EmotionServiceException;
import com.microsoft.projectoxford.face.FaceServiceClient;
import com.microsoft.projectoxford.face.FaceServiceRestClient;
import com.microsoft.projectoxford.face.contract.Face;
import com.microsoft.projectoxford.face.contract.FaceRectangle;

public class Normcam2 extends AppCompatActivity implements SurfaceHolder.Callback{

    SurfaceView sv;
    Camera cam;
    SurfaceHolder sh;
    Camera.PictureCallback jpegCallback;
    MediaRecorder mr;
    FileOutputStream fos;
    MediaController mc;
    ImageView iv;
    Bitmap bm;
    RelativeLayout rl;
    private ProgressDialog detectionProgressDialog;
    private FaceServiceClient faceServiceClient =
            new FaceServiceRestClient("0b695ba0444a458a9fe8b97941c0dd99");
    private EmotionServiceClient emotionServiceClient =
            new EmotionServiceRestClient("7ec36e7a9038486fbe564a9eadd7da7c");
    TextView textView,tv;
    TextToSpeech talk;
    ArrayList<Bitmap> bitmaps = new ArrayList<Bitmap>();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_normcam2);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        sv = (SurfaceView) findViewById(R.id.surfaceView4); //SurfaceView for showing the camera screen
        rl = (RelativeLayout) findViewById(R.id.activity_normcam2);
        sh = sv.getHolder();
        sh.addCallback(this);
        sh.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        iv = (ImageView) findViewById(R.id.imageView2); //ImageView for showing the pictures once it it taken by the camera
        textView = (TextView) findViewById(R.id.textView);
        tv = new TextView(this);
        detectionProgressDialog = new ProgressDialog(this);
        talk = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {

            @Override
            public void onInit(int status) { //TexttoSpeech for the speech notifications
                if(status != TextToSpeech.ERROR) {
                    talk.setLanguage(Locale.ENGLISH);
                }
            }
        });
        jpegCallback = new Camera.PictureCallback(){

            @Override
            public void onPictureTaken(byte[] bytes, Camera camera) {        //called once the picture is took by the camera

                  bm = BitmapFactory.decodeByteArray(bytes,0,bytes.length);  //Next 3 lines reducing the size of the image which is taken by the camera
                  int nh = (int) (bm.getHeight()*(512.0/bm.getWidth()));     //by reducing the size of the bitmap
                  Bitmap bmn = Bitmap.createScaledBitmap(bm,512,nh,true);
                  iv.setImageBitmap(bmn);    //Setting the reduced bitmap as the image in the imageview
                  iv.setVisibility(View.VISIBLE);
                  detectAndFrame(bmn);  // method called for face detection and emotion recognition
                  refreshCamera();      // camera refreshed once face is detected and emotion recognized in order to take the next picture


            }
        };
    }

    public void refreshCamera(){   //method called to refresh camera to take the next picture
        if(sh.getSurface()==null) {
            return;
        }
        try{
            cam.setPreviewDisplay(sh);
            cam.startPreview();
        }catch(Exception e){

        }
    }
    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {  //method called when app is started and camera will start
        try{
            cam = Camera.open();
        }catch(RuntimeException e){
            return;
        }
        try{
            cam.setPreviewDisplay(sh);
            cam.startPreview();

        }catch(Exception e){
            return;
        }
        new Thread(new Runnable(){
            @Override
            public void run() {       //new thread starts to run once the camera is started for face detection and emotion recognition
            for(int i=0;;i++){
                cam.takePicture(null,null,jpegCallback); //method started when camera takes a picture
                try{
                    Thread.sleep(5000);}catch(InterruptedException e2){   //Thread sleeps for five seconds and then camera takes picture again
                }
            }

            }
        }).start();

    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
        refreshCamera();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        cam.stopPreview();
        cam.release();

        cam = null;
    }
    /*
    This method is for face detection.
    It takes bitmap as input and extracts faces for that bitmap
     */
    private void detectAndFrame(final Bitmap imageBitmap) {

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        imageBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
        //Attributes that are returned when a face is detected. Here we use 'age' and 'gender'
        final FaceServiceClient.FaceAttributeType[] types = new FaceServiceClient.FaceAttributeType[]{
              FaceServiceClient.FaceAttributeType.Age,
              FaceServiceClient.FaceAttributeType.Gender,
        };
        ByteArrayInputStream inputStream =
                new ByteArrayInputStream(outputStream.toByteArray());
        //The following task takes the compressed bitmap and extracts faces using Face API
        AsyncTask<InputStream, String, Face[]> detectTask =
                new AsyncTask<InputStream, String, Face[]>() {
                    @Override
                    protected Face[] doInBackground(InputStream... params) {
                        try {
                            publishProgress("Detecting...");
                            //Following method call returns faces along with required parameters
                            Face[] result = faceServiceClient.detect(
                                    params[0],
                                    true,         // returnFaceId
                                    false,        // returnFaceLandmarks
                                    null           // returnFaceAttributes: a string like "age, gender"
                            );
                            if (result == null)
                            {
                                publishProgress("Detection Finished. Nothing detected");
                                return null;
                            }
                            publishProgress(
                                    String.format("Detection Finished. %d face(s) detected",
                                            result.length));
                            return result;
                        } catch (Exception e) {
                            publishProgress("Detection failed");
                            return null;
                        }

                    }
                    @Override
                    protected void onPreExecute() {
                        detectionProgressDialog.show();
                    }
                    @Override
                    protected void onProgressUpdate(String... progress) {
                        detectionProgressDialog.setMessage(progress[0]);
                    }
                    //This method is executed after the detection is complete
                    @Override
                    protected void onPostExecute(Face[] result) {
                        detectionProgressDialog.dismiss();
                        if (result == null) return;
                        //Setting the faces to the images
                        iv.setImageBitmap(drawFaceRectanglesOnBitmap(imageBitmap, result));
                        //Setting the detected faces on the image bitmap
                        doRecognize(imageBitmap, result); //This method is called for recognizing emotions by passing image bitmap and faces

                    }
                };
        detectTask.execute(inputStream); //executing the async task
    }

    /*
    This method calls the Emotion API and returns the emotion list for the bitmap
     */
    private List<RecognizeResult> processWithFaceRectangles(Bitmap mBitmap, Face[] faces) throws EmotionServiceException, com.microsoft.projectoxford.face.rest.ClientException, IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        mBitmap.compress(Bitmap.CompressFormat.JPEG, 100, output);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(output.toByteArray());
        com.microsoft.projectoxford.emotion.contract.FaceRectangle[] faceRectangles = null;
        //Extracting face rectangles from faces object
        if(faces != null) {
            faceRectangles = new com.microsoft.projectoxford.emotion.contract.FaceRectangle[faces.length];

            for (int i = 0; i < faceRectangles.length; i++) {
                com.microsoft.projectoxford.face.contract.FaceRectangle rect = faces[i].faceRectangle;
                faceRectangles[i] = new com.microsoft.projectoxford.emotion.contract.FaceRectangle(rect.left, rect.top, rect.width, rect.height);
            }
        }

        List<RecognizeResult> result = null;
        if (faceRectangles != null) {
            inputStream.reset();
            result = this.emotionServiceClient.recognizeImage(inputStream, faceRectangles);  //Calling the Emotion API
        }
        return result;  //Returning the emotion list result
    }

    /*
   This method is for emotion recognition.
   It takes bitmap and faces and recognizes emotion by calling Emotion API
    */
    private void doRecognize(final Bitmap b, final Face[] faces) {

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
        ByteArrayInputStream inputStream =
                new ByteArrayInputStream(outputStream.toByteArray());
        AsyncTask<String, String, List<RecognizeResult>> recognizeTask =
                new AsyncTask<String, String, List<RecognizeResult>>() {
                    @Override
                    protected List<RecognizeResult> doInBackground(String... args) {
                        try {
                            return processWithFaceRectangles(b, faces);  //Calling the method that returns the emotion list
                        }
                        catch(Exception e) {
                            e.printStackTrace();
                        }
                        return null;
                    }

                    //This method is executed after emotion is detected
                    @Override
                    protected void onPostExecute(List<RecognizeResult> result) {
                        super.onPostExecute(result);
                        Integer count = 0;
                        // Covert bitmap to a mutable bitmap by copying it
                        //To draw on the bitmap
                        Bitmap bitmapCopy = b.copy(Bitmap.Config.ARGB_8888, true);
                        Canvas faceCanvas = new Canvas(bitmapCopy);
                        faceCanvas.drawBitmap(b, 0, 0, null);
                        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(7);
                        paint.setColor(Color.GREEN);
                        //Initializing emotion class instance to store the emotion list
                        Emotions data[] = new Emotions[8];
                        Emotions req = new Emotions(null, 0);
                        //Extracting emotions from the result
                        for (RecognizeResult r : result) {

                            data[0] = new Emotions("Anger", r.scores.anger);
                            data[1] = new Emotions("Contempt", r.scores.contempt);
                            data[2] = new Emotions("Disgust", r.scores.disgust);
                            data[3] = new Emotions("Fear", r.scores.fear);
                            data[4] = new Emotions("Happiness", r.scores.happiness);
                            data[5] = new Emotions("Neutral", r.scores.neutral);
                            data[6] = new Emotions("Sadness", r.scores.sadness);
                            data[7] = new Emotions("Surprise", r.scores.surprise);
                            //The following code checks which emotion has the highest value
                            req = data[0];
                            for(int i=0; i<7; i++) {
                                if(req.value > data[i+1].value) {
                                    continue;
                                }
                                else {
                                    req = data[i+1];
                                }
                            }

                            textView.setText("\t"+req.name+String.format("\t %1$.5f", req.value)); //Displaying the recognized emotion of the person
                            talk.speak(req.name, TextToSpeech.QUEUE_FLUSH, null); //Sending a speech notification to the blind person


                            //Drawing again on the bitmap after emotion recognition
                            faceCanvas.drawRect(r.faceRectangle.left,
                                    r.faceRectangle.top,
                                    r.faceRectangle.left + r.faceRectangle.width,
                                    r.faceRectangle.top + r.faceRectangle.height,
                                    paint);
                            count++;
                        }
                        iv.setImageDrawable(new BitmapDrawable(getResources(), bitmapCopy));
                    }
                };
        recognizeTask.execute(); //executing the task
    }

    /*
   This method is called to draw the detected faces on the image bitmap
    */
    private static Bitmap drawFaceRectanglesOnBitmap(Bitmap originalBitmap, Face[] faces) {
        Bitmap bitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true);
        //Creating the canvas and paint class instances which are used for drawing
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.RED);
        int stokeWidth = 5;
        paint.setStrokeWidth(stokeWidth);
        //Extracting face rectangles from the faces
        if (faces != null) {
            for (Face face : faces) {
                FaceRectangle faceRectangle = face.faceRectangle;
                //Drawing the face rectangles on the image bitmap
                canvas.drawRect(
                        faceRectangle.left,
                        faceRectangle.top,
                        faceRectangle.left + faceRectangle.width,
                        faceRectangle.top + faceRectangle.height,
                        paint);
            }
        }
        return bitmap; //returns the bitmap on which face rectangles are drawn
    }
}

