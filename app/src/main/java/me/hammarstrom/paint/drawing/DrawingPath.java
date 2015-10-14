package me.hammarstrom.paint.drawing;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

/**
 * Created by Fredrik Hammarström on 12/10/15.
 */
public class DrawingPath {
    public Path path;
    public Paint paint;

    /**
     * Draw path on canvas
     * @param canvas
     */
    public void draw(Canvas canvas) {
        canvas.drawPath(path, paint);
    }
}
