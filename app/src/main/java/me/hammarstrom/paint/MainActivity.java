package me.hammarstrom.paint;

import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Iterator;
import java.util.List;

import me.hammarstrom.paint.connections.ConnectionsHandler;
import me.hammarstrom.paint.connections.PathMessage;
import me.hammarstrom.paint.drawing.DrawingPath;
import me.hammarstrom.paint.drawing.DrawingSurfaceView;

public class MainActivity extends AppCompatActivity implements View.OnTouchListener, View.OnClickListener, ConnectionsHandler.OnRemoteDrawingReceivedListener {

    private final String TAG = MainActivity.this.getClass().getName();

    private DrawingSurfaceView drawingSurfaceView;
    private DrawingPath currentDrawingPath;
    private Paint currentPaint;
    private ConnectionsHandler connectionsHandler;
    private PathMessage pm;

    private DrawingPath remoteDrawing;
    private Paint remotePaint = null;
    float currentPosX, currentPosY, relativeX, relativeY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setCurrentPaint();
        drawingSurfaceView = (DrawingSurfaceView) findViewById(R.id.drawingSurface);
        drawingSurfaceView.setOnTouchListener(this);

        connectionsHandler = new ConnectionsHandler(this, this);

        findViewById(R.id.advertise).setOnClickListener(this);
        findViewById(R.id.discover).setOnClickListener(this);
    }

    @Override
    public void onStart() {
        super.onStart();
        connectionsHandler.connect();
    }

    @Override
    public void onStop() {
        super.onStop();
        if(connectionsHandler != null) {
            connectionsHandler.disconnect();
        }
    }

    private void setCurrentPaint() {
        currentPaint = new Paint();
        currentPaint.setDither(true);
        currentPaint.setColor(0xFFFFFF00);
        currentPaint.setStyle(Paint.Style.STROKE);
        currentPaint.setStrokeJoin(Paint.Join.ROUND);
        currentPaint.setStrokeCap(Paint.Cap.ROUND);
        currentPaint.setStrokeWidth(3);
    }

    private void setRemotePaint() {
        remotePaint = new Paint();
        remotePaint.setDither(true);
        remotePaint.setColor(0xFFFF00FF);
        remotePaint.setStyle(Paint.Style.STROKE);
        remotePaint.setStrokeJoin(Paint.Join.ROUND);
        remotePaint.setStrokeCap(Paint.Cap.ROUND);
        remotePaint.setStrokeWidth(3);
    }

    @Override
    public boolean onTouch(View v, MotionEvent motionEvent) {
        currentPosX = motionEvent.getX();
        currentPosY = motionEvent.getY();
        relativeX = (currentPosX / getScreenSize().x);
        relativeY = (currentPosY / getScreenSize().y);

        if(motionEvent.getAction() == MotionEvent.ACTION_DOWN){
            currentDrawingPath = new DrawingPath();
            currentDrawingPath.paint = currentPaint;
            currentDrawingPath.path = new Path();
            currentDrawingPath.path.moveTo(currentPosX, currentPosY);
            currentDrawingPath.path.lineTo(currentPosX, currentPosY);

            // Add position to message
            PathMessage.clearList();
            PathMessage.addCoords(relativeX, relativeY);

        } else if(motionEvent.getAction() == MotionEvent.ACTION_MOVE) {
            currentDrawingPath.path.lineTo(currentPosX, currentPosY);
            PathMessage.addCoords(relativeX, relativeY);

            try {
                /*
                * The Connections message size limit is 4096 bytes,
                * make sure we don't add more than this. If we reach the limit,
                * send chunks instead.
                 */
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(bos);
                oos.writeObject(PathMessage.getCoords());
                byte[] bytes = bos.toByteArray();

                if(bytes.length >= 4000) {
                    connectionsHandler.sendMessage(PathMessage.getCoords());
                    PathMessage.clearList();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

        } else if(motionEvent.getAction() == MotionEvent.ACTION_UP) {
            PathMessage.addCoords(relativeX, relativeY);
            currentDrawingPath.path.lineTo(currentPosX, currentPosY);
            drawingSurfaceView.addDrawingPath(currentDrawingPath);

            // Send the message
            connectionsHandler.sendMessage(PathMessage.getCoords());
        }
        return true;
    }

    private Point getScreenSize() {
        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        return size;
    }

    @Override
    public void onClick(View v) {
        switch(v.getId()) {
            case R.id.advertise:
                connectionsHandler.startAdvertising();
                break;
            case R.id.discover:
                connectionsHandler.startDiscovery();
                break;
        }
    }

    @Override
    public void onRemoteDrawingReceived(List<String> coords) {
        Iterator i = coords.iterator();
        boolean first = true;
        while(i.hasNext()) {
            String c = (String) i.next();
            String[] cArr = c.split(",");

            float x, y;
            Point size = getScreenSize();
            x = (Float.valueOf(cArr[0]) * size.x);
            y = (Float.valueOf(cArr[1]) * size.y);

            if(remotePaint == null) {
                setRemotePaint();
            }

            if(first) {
                first = false;
                remoteDrawing = new DrawingPath();
                remoteDrawing.paint = remotePaint;
                remoteDrawing.path = new Path();
                remoteDrawing.path.moveTo(x, y);
            } else {
                remoteDrawing.path.lineTo(x, y);
            }
        }

        drawingSurfaceView.addDrawingPath(remoteDrawing);
    }
}

