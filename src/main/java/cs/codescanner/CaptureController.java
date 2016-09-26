package cs.codescanner;

import android.os.Bundle;
import android.view.WindowManager.LayoutParams;
import cs.android.viewbase.LayoutId;
import cs.android.viewbase.CSViewController;
import cs.codescanner.scanner.CaptureMainController;

public abstract class CaptureController extends CSViewController {

	protected final CaptureMainController capture = new CaptureMainController(this) {
		@Override protected void onDecodeDone() {
			CaptureController.this.onDecodeDone();
		};

		@Override protected void onFrameworkBug() {
		}
	};

	public CaptureController(CSViewController parent, LayoutId layout) {
		super(parent, layout);
	}

	@Override protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		activity().getWindow().addFlags(LayoutParams.FLAG_KEEP_SCREEN_ON);
	}
	
	protected void onDecodeDone() {
	}

}
