package cs.codescanner;

import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.WindowManager.LayoutParams;

import cs.android.viewbase.CSViewController;
import cs.android.viewbase.CSLayoutId;
import cs.codescanner.scanner.CaptureMainController;

public abstract class CaptureController extends CSViewController {

    protected final CaptureMainController capture = new CaptureMainController(this) {
        @Override
        protected void onDecodeDone() {
            CaptureController.this.onDecodeDone();
        }

        ;

        @Override
        protected void onFrameworkBug() {
        }
    };
    private int _requestedOrientationBefore;

    public CaptureController(CSViewController parent, CSLayoutId layout) {
        super(parent, layout);
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activity().getWindow().addFlags(LayoutParams.FLAG_KEEP_SCREEN_ON);
        _requestedOrientationBefore = activity().getRequestedOrientation();
        activity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    }

    public void onDestroy() {
        activity().setRequestedOrientation(_requestedOrientationBefore);
        super.onDestroy();
    }

    protected void onDecodeDone() {
    }

}
