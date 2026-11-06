package com.webhtml.app;

import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebView;

public class MenuControler {

    private int tapCount = 0;
    private long lastTapTime = 0;
    private float startX = 0;
    private float startY = 0;
    private final WebView overlayWebView;
    
    // Dozvoljeno vreme između tapova (u milisekundama)
    private static final long TAP_TIMEOUT = 400;
    // Dozvoljeno pomeranje prsta (u pikselima) da se i dalje smatra tapom, a ne skrolom
    private static final float MOVE_SLOP = 20.0f;

    public MenuControler(WebView overlayWebView) {
        this.overlayWebView = overlayWebView;
    }

    public boolean handleTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                // Ako je spušten više od jedan prst odjednom, poništi sve
                if (event.getPointerCount() > 1) {
                    tapCount = 0;
                    return false;
                }

                long currentTime = System.currentTimeMillis();
                startX = event.getX();
                startY = event.getY();

                // Ako je prošlo previše vremena od poslednjeg tapa, resetuj brojač
                if (currentTime - lastTapTime > TAP_TIMEOUT) {
                    tapCount = 0;
                }

                lastTapTime = currentTime;
                tapCount++;

                if (tapCount >= 6) {
                    tapCount = 0;
                    toggleMenu();
                    return true;
                }
                break;

            case MotionEvent.ACTION_MOVE:
                // Ako je korisnik počeo da pomera prst (skrolovanje), poništi tapove!
                if (tapCount > 0) {
                    float moveX = Math.abs(event.getX() - startX);
                    float moveY = Math.abs(event.getY() - startY);
                    if (moveX > MOVE_SLOP || moveY > MOVE_SLOP) {
                        tapCount = 0;
                    }
                }
                break;

            case MotionEvent.ACTION_POINTER_DOWN:
                // Ako se u toku tapkanja spusti drugi prst (multitouch), poništi
                tapCount = 0;
                break;
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
