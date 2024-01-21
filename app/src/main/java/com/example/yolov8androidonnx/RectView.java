package com.example.yolov8androidonnx;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import com.example.yolov8androidonnx.DataProcess;

import java.util.ArrayList;



public final class RectView extends View {

    private ArrayList<Result> results;
    private String[] classes;
    private Paint textPaint = new Paint();

    public RectView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        textPaint.setTextSize(60);
        textPaint.setColor(Color.GREEN);
    }

    public void transformRect(ArrayList<Result> results , Bitmap bitmap) {
        int bitmapWidth = bitmap.getWidth();
        int bitmapHeight = bitmap.getHeight();
        int calculatedWidth = BitmapUtils.calculateBitmapWidth(bitmap, getWidth(), getHeight());
        int calculatedHeight = BitmapUtils.calculateBitmapHeight(bitmap, getWidth(), getHeight());

        float scaleX =  calculatedWidth/ (float) DataProcess.INPUT_SIZE;
        float scaleY = calculatedHeight / (float) DataProcess.INPUT_SIZE;
        float realY = calculatedHeight - getHeight();
        float realX = calculatedWidth - getWidth();

        Log.d("BitmapSizeTest","width : " +calculatedWidth + " height : " +calculatedHeight);
        Log.d("BitmapSizeTest","width : " +getWidth() + " height : " +getHeight());
        for (Result result : results) {
            if (getWidth()>calculatedWidth ){
                result.getRectF().left  = result.getRectF().left * scaleX -realX/2;
                result.getRectF().right  = result.getRectF().right * scaleX-realX/2;
            }else {
                result.getRectF().left  = result.getRectF().left * scaleX;
                result.getRectF().right  = result.getRectF().right * scaleX;
            }

            if (getWidth()>bitmapWidth ){
                result.getRectF().top = result.getRectF().top * scaleY -realY/2;
                result.getRectF().bottom = result.getRectF().bottom * scaleY -realY/2;

            }else  {
                result.getRectF().top = result.getRectF().top * scaleY ;
                result.getRectF().bottom = result.getRectF().bottom * scaleY ;
            }

        }
        Log.d("TransformRect", "Results after transformation:" + results);
        this.results = results;
    }

    @Override
    protected void onDraw(Canvas canvas) {
       if (results !=null){
           for (Result result :results){
               canvas.drawRect(result.getRectF(),findPaint(result.getClassIndex()));

               String text = classes[result.getClassIndex()] + "," + Math.round(result.getScore() *100)+"%";
               canvas.drawText(text,
                       result.getRectF().left +10,
                       result.getRectF().top + 40,
                       textPaint);
           }
       }
        super.onDraw(canvas);
    }

    public void setClassLabel(String[] classes) {
        this.classes = classes;
    }

    private Paint findPaint(int classIndex) {

        Paint paint = new Paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(10.0f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeMiter(100f);

        switch (classIndex) {
            case 0:
                paint.setColor(Color.RED);
                break;
            case 1:
                paint.setColor(Color.BLUE);
                break;
            default:
                paint.setColor(Color.DKGRAY);
                break;
        }
        Log.d("RectView", "findPaint: ClassIndex = " + classIndex);
        return paint;
    }

    public void clearCanvas() {
        results = null;
        // Xóa hình chữ nhật bằng cách gọi invalidate()
        invalidate();
    }
}