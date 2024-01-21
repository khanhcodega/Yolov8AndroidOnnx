package com.example.yolov8androidonnx;


import android.graphics.RectF;
import android.util.Log;

public class Result {
    private   int classIndex;
    private   float score;
    private   RectF rectF;
    public Result(int classIndex, float score, RectF rectF) {
        this.classIndex = classIndex;
        this.score = score;
        this.rectF = rectF;
        Log.d("Reusult", "classIndex = " + classIndex + "score =" +score +"rectF =" +rectF );
    }

//    public int compareTo(Result other) {
//        return Float.compare(this.score, other.score);
//    }

    @Override
    public String toString() {
        return "Result{" +
                "classIndex=" + classIndex +
                ", score=" + score +
                ", rectF=" + rectF +
                '}';
    }
    public int getClassIndex() {
        return classIndex;
    }

    public void setClassIndex(int classIndex) {
        this.classIndex = classIndex;
    }

    public float getScore() {
        return score;
    }

    public void setScore(float score) {
        this.score = score;
    }

    public RectF getRectF() {
        return rectF;
    }

    public void setRectF(RectF rectF) {
        this.rectF = rectF;
    }
}
