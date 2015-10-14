package me.hammarstrom.paint.drawing;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Created by Fredrik Hammarström on 13/10/15.
 */
public class DrawingSurfaceView extends SurfaceView implements SurfaceHolder.Callback {

    private Boolean threadRunning;
    protected DrawThread thread;
    private List<DrawingPath> drawingPathList;

    public DrawingSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        getHolder().addCallback(this);
        thread = new DrawThread(getHolder());
        drawingPathList = Collections.synchronizedList(new ArrayList<DrawingPath>());
    }

    /**
     * Add drawing path
     * @param drawingPath
     */
    public void addDrawingPath(DrawingPath drawingPath){
        drawingPathList.add(drawingPath);
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int width,  int height) {

    }


    public void surfaceCreated(SurfaceHolder holder) {
        thread.setRunning(true);
        thread.start();
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        boolean retry = true;
        thread.setRunning(false);
        while (retry) {
            try {
                thread.join();
                retry = false;
            } catch (InterruptedException e) {
                // we will try it again and again...
            }
        }
    }

    class DrawThread extends Thread {
        private SurfaceHolder mSurfaceHolder;

        public DrawThread(SurfaceHolder surfaceHolder){
            mSurfaceHolder = surfaceHolder;
        }

        /**
         * Set thread is running or nor
         * @param run A boolean value
         */
        public void setRunning(boolean run) {
            threadRunning = run;
        }

        @Override
        public void run() {
            Canvas canvas = null;
            while(threadRunning) {
                try{
                    canvas = mSurfaceHolder.lockCanvas(null);
                    if(canvas != null) {
                        if(drawingPathList != null) {
                            synchronized (drawingPathList) {
                                final Iterator i = drawingPathList.iterator();
                                while (i.hasNext()) {
                                    final DrawingPath dp = (DrawingPath) i.next();
                                    dp.draw(canvas);
                                }
                            }
                        }
                    }
                } finally {
                    if(canvas != null) {
                        mSurfaceHolder.unlockCanvasAndPost(canvas);
                    }
                }
            }

        }
    }
}
