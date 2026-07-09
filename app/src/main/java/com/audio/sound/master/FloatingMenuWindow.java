package com.audio.sound.master;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;

public class FloatingMenuWindow {
    private WindowManager wm;
    private WindowManager.LayoutParams params;
    private Button floatingIcon;
    private VMP_ControllerView menuView;
    private Context mContext;
    public boolean isShowing = false;

    public FloatingMenuWindow(Context context) {
        this.mContext = context;
        wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_SECURE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 100;
        params.y = 100;

        createFloatingIcon();
        menuView = new VMP_ControllerView(context, this);

        showIcon();
    }

    private void createFloatingIcon() {
        floatingIcon = new Button(mContext);
        floatingIcon.setText("VMP");
        floatingIcon.setTextColor(Color.CYAN);
        floatingIcon.setBackgroundColor(Color.parseColor("#CC000000"));
        floatingIcon.setPadding(20, 20, 20, 20);

        floatingIcon.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private boolean isClick;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x; initialY = params.y;
                        initialTouchX = event.getRawX(); initialTouchY = event.getRawY();
                        isClick = true; return true;
                    case MotionEvent.ACTION_MOVE:
                        if (Math.abs(event.getRawX() - initialTouchX) > 10 || Math.abs(event.getRawY() - initialTouchY) > 10) isClick = false;
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        wm.updateViewLayout(floatingIcon, params); return true;
                    case MotionEvent.ACTION_UP:
                        if (isClick) expandMenu();
                        return true;
                }
                return false;
            }
        });
    }

    public void showIcon() {
        if (!isShowing) {
            wm.addView(floatingIcon, params);
            isShowing = true;
        }
    }

    public void expandMenu() {
        wm.removeView(floatingIcon);
        wm.addView(menuView, params);
        
        menuView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x; initialY = params.y;
                        initialTouchX = event.getRawX(); initialTouchY = event.getRawY(); return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        wm.updateViewLayout(menuView, params); return true;
                }
                return false;
            }
        });
    }

    public void minimizeMenu() {
        wm.removeView(menuView);
        wm.addView(floatingIcon, params);
    }

    public void hideUI() {
        // Yeh sirf UI ko screen se hatayega, Engine background mein chalta rahega!
        try {
            if (menuView.getWindowToken() != null) wm.removeView(menuView);
            if (floatingIcon.getWindowToken() != null) wm.removeView(floatingIcon);
            isShowing = false;
        } catch (Exception e) {}
    }
}
