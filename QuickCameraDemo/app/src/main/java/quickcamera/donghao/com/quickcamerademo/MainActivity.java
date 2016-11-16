package quickcamera.donghao.com.quickcamerademo;

import android.hardware.Camera;
import android.location.Location;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;

import java.io.IOException;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements View.OnClickListener,SurfaceHolder.Callback {

    private Button btnTakePicture;
    private SurfaceView mSurfaceView;
    private int NUM_QUICK_CAPTURE_PICTURE_MAX = 8;// 拍摄照片的数量
    private android.hardware.Camera mCameraDevice;
    private Camera.Parameters mParameters;
    private final PreviewPictureCallback mPreviewPictureCallback = new PreviewPictureCallback();

    private static final int MESSAGE_RAPID_CAPTUARE = 0x00;
    private static final int PREVIEW_STOPPED = 1;
    private static final int IDLE = 2;
    private int mCameraState = PREVIEW_STOPPED;
    private boolean rapidFlag = false;
    private SurfaceHolder mSurfaceHolder;
    private ImageSaver mImageSaver;
    private String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        btnTakePicture = (Button) findViewById(R.id.btn_takepicture);
//        mSurfaceView = new SurfaceView(MainActivity.this);
        mSurfaceView = (SurfaceView) findViewById(R.id.frame);
        btnTakePicture.setOnClickListener(this);
        mImageSaver = new ImageSaver();
        mSurfaceHolder = mSurfaceView.getHolder();
        mSurfaceHolder.addCallback(MainActivity.this);
        mCameraDevice = android.hardware.Camera.open(0);// 0 后置 1 前置
        mParameters = mCameraDevice.getParameters();

        rapidFlag = true;
        btnTakePicture.setEnabled(false);
        if (NUM_QUICK_CAPTURE_PICTURE_MAX == 0) {
            NUM_QUICK_CAPTURE_PICTURE_MAX = 8;
        }

    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_takepicture:
                rapidFlag = true;
                btnTakePicture.setEnabled(false);
                if (NUM_QUICK_CAPTURE_PICTURE_MAX == 0) {
                    NUM_QUICK_CAPTURE_PICTURE_MAX = 8;
                }
                break;
        }
    }

    private final class PreviewPictureCallback implements Camera.PreviewCallback {

        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            if (NUM_QUICK_CAPTURE_PICTURE_MAX > 0) {
                if (rapidFlag) {
                    Camera.Size previewSize = mParameters.getPreviewSize();
                    // playRapidCaptureShutter(); you can play sound
                    mImageSaver.addImage(data, null, previewSize.width,
                            previewSize.height,
                            mImageSaver.PICTURE_DATA_FORMAT_YUV);
                    --NUM_QUICK_CAPTURE_PICTURE_MAX;
                    rapidFlag = false;
                    mRapidHandler.sendEmptyMessageDelayed(
                            MESSAGE_RAPID_CAPTUARE, 100);//1 second take 10 pictures
                }
            }else {
                btnTakePicture.setEnabled(true);
            }
        }
    }

    @Override
    public void surfaceCreated(final SurfaceHolder holder) {
        Thread setDisplayThread = new Thread(new Runnable() {
            public void run() {
                try {
                    if (mCameraDevice != null) {
                        mCameraDevice.setPreviewDisplay(holder);
                    }
                } catch (IOException exception) {
                    mCameraDevice.release();
                    mCameraDevice = null;
                }
            }
        });
        setDisplayThread.start();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Thread previewThread = new Thread(new Runnable() {
            public void run() {
                if (mCameraDevice != null) {
                    mParameters.setPreviewSize(640, 480);
                    mParameters.setPictureSize(640, 480);
                    mCameraDevice.setParameters(mParameters);
                    mCameraDevice.startPreview();
                    mCameraDevice.setPreviewCallback(mPreviewPictureCallback);
                }
            }
        });
        previewThread.start();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        stopPreview();
        mSurfaceHolder = null;
    }

    private void stopPreview() {
        Thread destoryThread = new Thread(new Runnable() {
            public void run() {
                if (mCameraDevice != null) {
                    mCameraDevice.cancelAutoFocus();
                    mCameraDevice.stopPreview();
                }
            }
        });
        destoryThread.start();
        try {
            destoryThread.join();
        } catch (InterruptedException ex) {
            // ignore
        }
    }

    Handler mRapidHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            // TODO Auto-generated method stub
            switch (msg.what) {
                case MESSAGE_RAPID_CAPTUARE:
                    rapidFlag = true;
                    break;
            }
            super.handleMessage(msg);
        }

    };

    private static class SaveRequest {
        byte[] data;
        Location loc;
        int width, height;
        long dateTaken;
        int previewWidth;
        int format;// 0 jpeg 1 yuv420sp
    }

    private class ImageSaver extends Thread {
        private static final int QUEUE_LIMIT = 3;
        private static final int PICTURE_DATA_FORMAT_JPEG = 0;
        private static final int PICTURE_DATA_FORMAT_YUV = 1;
        private ArrayList<SaveRequest> mQueue;
        private Object mUpdateThumbnailLock = new Object();
        private boolean mStop;
        private SimpleDateFormat mFormat;
        // The date (in milliseconds) used to generate the last name.
        private long mLastDate;

        // Number of names generated for the same second.
        private int mSameSecondCount;

        // Runs in main thread
        public ImageSaver() {
            mQueue = new ArrayList<SaveRequest>();
            start();
        }

        // Runs in main thread
        public void addImage(final byte[] data, Location loc, int width,
                             int height, int format) {
            SaveRequest r = new SaveRequest();
            r.data = data;
            r.loc = (loc == null) ? null : new Location(loc); // make a copy
            r.width = width;
            r.height = height;
            r.format = format;
            r.dateTaken = System.currentTimeMillis();
            r.previewWidth = mParameters.getPreviewSize().width;

            synchronized (this) {
                while (mQueue.size() >= QUEUE_LIMIT) {
                    try {
                        wait();
                    } catch (InterruptedException ex) {
                        // ignore.
                    }
                }
                mQueue.add(r);
                notifyAll(); // Tell saver thread there is new work to do.
            }
        }

        // Runs in saver thread
        @Override
        public void run() {
            while (true) {
                SaveRequest r;
                synchronized (this) {
                    if (mQueue.isEmpty()) {
                        notifyAll(); // notify main thread in waitDone

                        // Note that we can only stop after we saved all images
                        // in the queue.
                        if (mStop)
                            break;

                        try {
                            wait();
                        } catch (InterruptedException ex) {
                            // ignore.
                        }
                        continue;
                    }
                    r = mQueue.get(0);
                }
                storeImage(r.data, r.loc, r.width, r.height, r.dateTaken,
                        r.previewWidth, r.format);
                synchronized (this) {
                    mQueue.remove(0);
                    notifyAll(); // the main thread may wait in addImage
                }
            }
        }

        // Runs in main thread
        public void waitDone() {
            synchronized (this) {
                while (!mQueue.isEmpty()) {
                    try {
                        wait();
                    } catch (InterruptedException ex) {
                        // ignore.
                    }
                }
            }
        }

        // Runs in main thread
        public void finish() {
            waitDone();
            synchronized (this) {
                mStop = true;
                notifyAll();
            }
            try {
                join();
            } catch (InterruptedException ex) {
                // ignore.
            }
        }

        // Runs in saver thread
        private void storeImage(final byte[] data, Location loc, int width,
                                int height, long dateTaken, int previewWidth, int format) {
            Log.v(TAG,"data:"+data);
            String pictureFormat = "yuv420sp";
            String title = createJpegName(dateTaken);
            int orientation = 0;
            Uri uri;
            uri = Storage.addImage(getContentResolver(), title, pictureFormat,
                    dateTaken, loc, orientation, data, width, height);
        }

        private String createJpegName(long dateTaken) {
            Date date = new Date(dateTaken);
            mFormat = new SimpleDateFormat("'IMG'_yyyyMMdd_HHmmss");
            String result = mFormat.format(date);

            // If the last name was generated for the same second,
            // we append _1, _2, etc to the name.
            if (dateTaken / 1000 == mLastDate / 1000) {
                mSameSecondCount++;
                result += "_" + mSameSecondCount;
            } else {
                mLastDate = dateTaken;
                mSameSecondCount = 0;
            }

            return result;
        }
    }
}
