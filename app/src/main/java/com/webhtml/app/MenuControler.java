package com.webhtml.app;

import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebView;

public class MenuControler {

    private int tapCount = 0;
    private long lastTapTime = 0;
    private final WebView overlayWebView;
    
    // Vremenski prozor u milisekundama za 6 tapa (npr. 400-500ms)
    private static final long TAP_TIMEOUT = 450;

    public MenuControler(WebView overlayWebView) {
        this.overlayWebView = overlayWebView;
    }

    public boolean handleTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            // Ako je stavljeno više prstiju na ekran (npr. gest za skrolovanje ili zumiranje), resetuj tapove
            if (event.getPointerCount() > 1) {
                tapCount = 0;
                return false;
            }

            long currentTime = System.currentTimeMillis();

            // Ako je prošlo previše vremena od poslednjeg tapa, kreći iznova
            if (currentTime - lastTapTime > TAP_TIMEOUT) {
                tapCount = 0;
            }

            lastTapTime = currentTime;
            tapCount++;

            if (tapCount >= 6) {
                tapCount = 0;
                toggleMenu();
                return true; // Potvrđujemo da smo obradili događaj
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

    public void closeMenu() {
        if (overlayWebView != null) {
            overlayWebView.setVisibility(View.GONE);
        }
    }

    public boolean isMenuVisible() {
        return overlayWebView != null && overlayWebView.getVisibility() == View.VISIBLE;
    }
}
