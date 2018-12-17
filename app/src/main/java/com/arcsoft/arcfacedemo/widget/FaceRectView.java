package com.arcsoft.arcfacedemo.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Rect;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;

import com.arcsoft.arcfacedemo.model.DrawInfo;
import com.arcsoft.arcfacedemo.util.DrawHelper;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class FaceRectView extends View {
    private static final String TAG = "FaceRectView";
    private CopyOnWriteArrayList<DrawInfo> faceRectList = new CopyOnWriteArrayList<>();
    private int radius = 0;

    public FaceRectView(Context context) {
        this(context, null);
    }

    public FaceRectView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (faceRectList != null && faceRectList.size() > 0) {
            for (int i = 0; i < faceRectList.size(); i++) {
                DrawHelper.drawFaceRect(canvas, faceRectList.get(i), Color.YELLOW, 5);
            }
        }
    }

    public void clearFaceInfo() {
        faceRectList.clear();
        postInvalidate();
    }

    public void addFaceInfo(DrawInfo faceInfo) {
        faceRectList.add(faceInfo);
        postInvalidate();
    }

    public void addFaceInfo(List<DrawInfo> faceInfoList) {
        faceRectList.addAll(faceInfoList);
        postInvalidate();
    }

    public void turnRound() {
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                Rect rect = new Rect(0,0, view.getMeasuredWidth() , view.getMeasuredHeight());
                outline.setRoundRect(rect, radius);
            }
        });
        setClipToOutline(true);
    }

    public void setRadius(int radius) {
        this.radius = radius;
    }

    public int getRadius() {
        return radius;
    }
}