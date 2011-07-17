package org.whiskeysierra.impairedvision;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.hardware.Camera;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.*;
import android.widget.TextView;
import de.cosmocode.collections.MoreLists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public final class VisionActivity extends Activity implements SurfaceHolder.Callback, Camera.PreviewCallback,
        SharedPreferences.OnSharedPreferenceChangeListener {

    private static final Logger LOG = LoggerFactory.getLogger(VisionActivity.class);

    private Camera camera;
    private SurfaceView preview;
    private SurfaceHolder holder;
    private Camera.Size size;

    private int currentIndex;
    private final List<Vision> visions = MoreLists.cycle(
            new NormalVision(),
            new Myopia(),
            new Protanopia(),
            new Deuteranopia(),
            new Tritanopia(),
            new Achromatopia(),
            new AchromatopiaAndMyopia()
    );

    private TextView currentName;

    private int[] rgb;
    private final Paint paint = new Paint();

    private boolean skipFrames;
    private int skipBelow;
    private int skipUntil;

    private int frameCount;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Window window = getWindow();
        window.requestFeature(Window.FEATURE_NO_TITLE);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.main);

        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        preferences.registerOnSharedPreferenceChangeListener(this);

        configure(preferences);

        preview = (SurfaceView) findViewById(R.id.preview);
        currentName = (TextView) findViewById(R.id.vision);

        holder = preview.getHolder();
        holder.addCallback(this);

        final SwipeDetector detector = new SwipeDetector(new SwipeListener(this));
        preview.setOnTouchListener(detector);
        // this is a noop implementation, but somehow required for the touch events to fire
        preview.setOnClickListener(detector);
    }

    void switchToPrevious() {
        switchTo(currentIndex - 1);
    }

    void switchToNext() {
        switchTo(currentIndex + 1);
    }

    private void switchTo(int index) {
        currentIndex = index;
        final Vision vision = visions.get(currentIndex);
        if (camera != null) {
            vision.configure(camera);
        }
        paint.setColorFilter(vision.getFilter());
        currentName.setText(vision.getName());
        LOG.debug("Switched to {}", vision);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        OptionsMenu.createMenu(this, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (camera == null) {
            camera = Camera.open();
            size = camera.getParameters().getPreviewSize();
        }
    }

    @Override
    protected void onPause() {
        if (camera != null) {
            camera.setPreviewCallback(null);
            camera.stopPreview();
            camera.release();
            camera = null;
        }
        super.onPause();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (camera != null) {
            preview.setWillNotDraw(false);
            camera.startPreview();
            switchTo(0);
            rgb = new int[size.width * size.height];
            camera.setPreviewCallback(this);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (camera != null) {
            camera.setPreviewCallback(null);
            camera.stopPreview();
            camera.release();
            camera = null;
        }
    }

    @Override
    public void onPreviewFrame(byte[] yuv, Camera camera) {
        if (holder != null) {
            if (skipFrames) {
                if (frameCount < skipBelow) {
                    frameCount++;
                    return;
                } else if (frameCount >= skipUntil) {
                    frameCount = 0;
                    return;
                }
            }

            frameCount++;

            Canvas canvas = null;

            try {
                canvas = holder.lockCanvas(null);
                Yuv420.decode(yuv, rgb, size.width, size.height);
                canvas.drawBitmap(rgb, 0, size.width, 0, 0, size.width, size.height, false, paint);
            } finally {
                if (canvas != null) {
                    holder.unlockCanvasAndPost(canvas);
                }
            }
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences preferences, String s) {
        configure(preferences);
    }

    private void configure(SharedPreferences preferences) {
        skipFrames = preferences.getBoolean("skipFrames", false);

        final int rate = Integer.parseInt(preferences.getString("skipRate", "25"));
        final int divisor = MoreMath.gcd(rate, 100);
        skipBelow = rate / divisor;
        skipUntil = 100 / divisor;
    }

}
