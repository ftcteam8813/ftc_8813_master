package org.firstinspires.ftc.teamcode.autonomous.util.opencv;

import android.app.Activity;
import android.view.View;
import android.widget.LinearLayout;

import org.firstinspires.ftc.robotcontroller.internal.FtcRobotControllerActivity;
import org.firstinspires.ftc.robotcore.internal.system.AppUtil;
import org.firstinspires.ftc.teamcode.autonomous.BaseAutonomous;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

/**
 * CameraStream - Listen for camera input and return Mat frames when it is available. This class
 * **MUST** be initialized (e.g. by CameraStream.class) BEFORE using ANY OpenCV classes!!!
 */

public class CameraStream {

    private JavaCameraView cameraView;
    private Activity activity;
    private volatile boolean uiRunning;
    //Smurf mode -- swap red and blue color channels in the output image for EPIC results :)
    private static final boolean SMURF_MODE = true;

    private volatile List<CameraListener> listeners = new ArrayList<>();

    //Default modifier: do nothing to the image
    public static final OutputModifier defaultModifier =
            new OutputModifier() { public Mat process(Mat rgba) {return rgba;} };
    private OutputModifier modifier = defaultModifier;

    public void addListener(CameraListener l) {
        listeners.add(l);
    }

    public void removeListener(CameraListener l) {
        listeners.remove(l);
    }

    public void setModifier(OutputModifier m) {
        modifier = m;
    }

    public void stop() {
        FtcRobotControllerActivity rc = (FtcRobotControllerActivity) activity;
        final LinearLayout cameraLayout = rc.cameraMonitorLayout;
        uiRunning = true;
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                cameraView.disableView();
                cameraLayout.removeView(cameraView);
                uiRunning = false;
            }
        });
        while (uiRunning);
    }

    public static interface CameraListener {
        public void processFrame(Mat rgba);
    }

    public static interface OutputModifier {
        public Mat process(Mat rgba);
    }

    private void startProcessing() {
        cameraView.setVisibility(View.VISIBLE);
        cameraView.setCvCameraViewListener(new CameraBridgeViewBase.CvCameraViewListener2() {
            @Override
            public void onCameraViewStarted(int width, int height) { }

            @Override
            public void onCameraViewStopped() { }

            @Override
            public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
                Mat frame = inputFrame.rgba();
                if (frame.width() == 0 || frame.height() == 0) return frame;
                //Log.i("Image Conversion", "Image type: " + CvType.typeToString(frame.type()));
                //Convert to BGR to make it a 'normal' image
                Imgproc.cvtColor(frame, frame, Imgproc.COLOR_RGBA2BGR);
                //Make a copy of the current listeners list to try to be more thread-safe
                CameraListener[] currentListeners = listeners.toArray(new CameraListener[0]);
                for (CameraListener l : currentListeners) {
                    //Make a copy of the frame so that processors cannot modify the image
                    Mat copyFrame = new Mat();
                    frame.copyTo(copyFrame);
                    l.processFrame(frame);
                    copyFrame.release();
                }
                Mat out =  modifier.process(frame);

                if (!SMURF_MODE)
                    Imgproc.cvtColor(out, out, Imgproc.COLOR_BGR2RGBA);
                Mat t = new Mat(out.height(), out.width(), CvType.CV_8UC4);
                Core.transpose(out, t);
                Imgproc.resize(t, out, out.size(), 0,0, 0);
                Core.flip(out, out, 1 );
                t.release();
                //Run the garbage collector as fast as possible to delete old images and keep enough
                //memory for our program to function, avoid blowing up the phone :)
                System.gc();
                return out;
            }
        });
    }

    public CameraStream() {
        activity = AppUtil.getInstance().getActivity();
        FtcRobotControllerActivity rc = (FtcRobotControllerActivity) activity;
        final LinearLayout cameraLayout = rc.cameraMonitorLayout;
        uiRunning = true;
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                cameraView = new JavaCameraView(activity, JavaCameraView.CAMERA_ID_BACK);
                cameraView.setVisibility(View.INVISIBLE);
                //cameraView.setRotation(90);
                cameraLayout.addView(cameraView);
                uiRunning = false;
            }
        });
        while (uiRunning);
        startProcessing();
        cameraView.enableView();
    }
}
