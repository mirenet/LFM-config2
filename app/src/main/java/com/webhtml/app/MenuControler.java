package com.webhtml.app;

import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebView;

public class MenuControler {

    private int tapCount = 0;
    private long lastTapTime = 0;
    private final WebView overlayWebView;

    public MenuControler(WebView overlayWebView) {
        this.overlayWebView = overlayWebView;
    }

    // Metoda za detekciju 6 tapa (ili bilo kog drugog gesta)
    public boolean handleTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            long currentTime = System.currentTimeMillis();
            
            if (currentTime - lastTapTime > 500) {
                tapCount = 0;
            }

            lastTapTime = currentTime;
            tapCount++;

            if (tapCount >= 6) {
                tapCount = 0;
                toggleMenu();
                return true;
            }
        }
        return false;
    }

    public void toggleMenu() {
        if (overlayWebView != null) {
            if (overlayWebView.getVisibility() == View.VISIBLE) {
                overlayWebView.setVisibility(View.GONE);
            } else {
                overlayWebView.setVisibility(View.VISIBLE);
            }
        }
    }

    public boolean isMenuVisible() {
        return overlayWebView != null && overlayWebView.getVisibility() == View.VISIBLE;
    }
}
