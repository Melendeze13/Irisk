package com.example.todo.irisk;

import android.Manifest;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.objdetect.Objdetect;
import org.opencv.imgcodecs.Imgcodecs;
import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;


public class MainActivity extends AppCompatActivity implements CvCameraViewListener2 {
    static{ System.loadLibrary("opencv_java3");}
    private static final String TAG = "OCVSample::Activity";
    public static final int JAVA_DETECTOR = 0;
    private int frames;
    private Mat mRgba;
    private int burstLength = 100;
    private Mat mGray;
    private boolean capturing = true;
    public TextView myTextView;

    private CascadeClassifier mJavaDetectorLeftEye;

    private CameraBridgeViewBase mOpenCvCameraView;

    double xCenter = -1;
    double yCenter = -1;
    private double dilationPerFrame = 0.0;
    private Mat mIntermediateMat;
    private Mat hierarchy;

    private boolean captureFrame;

    private int count = 0;

    private Mat mZoomWindow;

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Log.i(TAG, "OpenCV loaded successfully");

                    // load cascade file from application resources
                    File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);

                    mJavaDetectorLeftEye = loadClassifier(R.raw.haarcascade_lefteye_2splits, "haarcascade_eye_left.xml",
                            cascadeDir);

                    cascadeDir.delete();
                    mOpenCvCameraView.setCameraIndex(0);
                    mOpenCvCameraView.enableFpsMeter();
                    mOpenCvCameraView.enableView();
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        System.out.println("HEYYYYYYYYYYYYYYYYYYYYY");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);
        Button button = findViewById(R.id.capture);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                capturing = true;
                frames = 0;
            }
        });
        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.HelloOpenCvView);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);
        myTextView = findViewById(R.id.dilationPerFrame);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }
    @Override
    public void onResume() {
        super.onResume();
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_6, this,
                mLoaderCallback);
    }


    private CascadeClassifier loadClassifier(int rawResId, String filename, File cascadeDir) {
        CascadeClassifier classifier = null;
        try {
            InputStream is = getResources().openRawResource(rawResId);
            File cascadeFile = new File(cascadeDir, filename);
            FileOutputStream os = new FileOutputStream(cascadeFile);

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            is.close();
            os.close();

            classifier = new CascadeClassifier(cascadeFile.getAbsolutePath());
            if (classifier.empty()) {
                Log.e(TAG, "Failed to load cascade classifier");
                classifier = null;
            } else
                Log.i(TAG, "Loaded cascade classifier from " + cascadeFile.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "Failed to load cascade. Exception thrown: " + e);
        }

        return classifier;
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        frames = 0;

    }

    @Override
    public void onCameraViewStopped() {


    }

    @Override
    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        frames = frames + 1;
        if(capturing && frames == burstLength+1){
            capturing = false;
        }
        if(capturing){

            mRgba = inputFrame.rgba();
            mGray = inputFrame.gray();
            if (mZoomWindow == null)
                createAuxiliaryMats();
            Rect area = new Rect(new Point(20, 20), new Point(mGray.width() - 20, mGray.height() - 20));
            detectEye(mJavaDetectorLeftEye, area, 100);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    myTextView.setText(Double.toString(dilationPerFrame));
                }
            });
        }
        return inputFrame.rgba();
    }

    private void createAuxiliaryMats() {
        if (mGray.empty())
            return;

        int rows = mGray.rows();
        int cols = mGray.cols();

        if (mZoomWindow == null) {
            mZoomWindow = mRgba.submat(rows / 2 + rows / 10, rows, cols / 2 + cols / 10, cols);
        }



    }


    private Mat detectEye(CascadeClassifier clasificator, Rect area, int size) {
        Mat template = new Mat();
        Mat mROI = mGray.submat(area);
        MatOfRect eyes = new MatOfRect();
        Point iris = new Point();

        //isolate the eyes first
        clasificator.detectMultiScale(mROI, eyes, 1.15, 2, Objdetect.CASCADE_FIND_BIGGEST_OBJECT
                | Objdetect.CASCADE_SCALE_IMAGE, new Size(30, 30), new Size());

        Rect[] eyesArray = eyes.toArray();
        for (int i = 0; i < eyesArray.length;) {
            Rect e = eyesArray[i];
            e.x = area.x + e.x;
            e.y = area.y + e.y;
            Rect eye_only_rectangle = new Rect((int) e.tl().x, (int) (e.tl().y + e.height * 0.4), (int) e.width,
                    (int) (e.height * 0.6));

            Core.MinMaxLocResult mmG = Core.minMaxLoc(mROI);

            iris.x = mmG.minLoc.x + eye_only_rectangle.x;
            iris.y = mmG.minLoc.y + eye_only_rectangle.y;
            //find the pupil inside the eye rect
            detectPupil(eye_only_rectangle);

            return template;
        }

        return template;
    }
    protected void detectPupil(Rect eyeRect) {
        hierarchy = new Mat();

        Mat img = mRgba.submat(eyeRect);
        Mat img_hue = new Mat();

        Mat circles = new Mat();

        // / Convert it to hue, convert to range color, and blur to remove false
        // circles
        Imgproc.cvtColor(img, img_hue, Imgproc.COLOR_RGB2HSV);// COLOR_BGR2HSV);

        Core.inRange(img_hue, new Scalar(0, 0, 0), new Scalar(255, 255, 32), img_hue);

        Imgproc.erode(img_hue, img_hue, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3, 3)));

        Imgproc.dilate(img_hue, img_hue, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(6, 6)));

        Imgproc.Canny(img_hue, img_hue, 170, 220);
        Imgproc.GaussianBlur(img_hue, img_hue, new Size(9, 9), 2, 2);
        // Apply Hough Transform to find the circles
        Imgproc.HoughCircles(img_hue, circles, Imgproc.CV_HOUGH_GRADIENT, 3, img_hue.rows(), 200, 75, 10, 25);

        if (circles.cols() > 0)
            for (int x = 0; x < circles.cols(); x++) {
                double vCircle[] = circles.get(0, x);

                if (vCircle == null)
                    break;
                double radius = vCircle[2];
                System.out.println(radius);
                System.out.println("HEYYYYYYYYYYYYYYYYYYYYYYYYYYYYYY");
                dilationPerFrame = dilationPerFrame + radius;

                // draw the found circle
            }
    }
}
