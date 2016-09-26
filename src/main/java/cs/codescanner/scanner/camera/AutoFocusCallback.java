package cs.codescanner.scanner.camera;

import static cs.java.lang.Lang.info;
import android.hardware.Camera;
import android.os.Handler;
import android.os.Message;

final class AutoFocusCallback implements Camera.AutoFocusCallback {

	private static final long AUTOFOCUS_INTERVAL_MS = 1500L;
	private Handler autoFocusHandler;
	private int autoFocusMessage;

	public void onAutoFocus(boolean success, Camera camera) {
		if (autoFocusHandler != null) {
			Message message = autoFocusHandler.obtainMessage(autoFocusMessage, success);
			autoFocusHandler.sendMessageDelayed(message, AUTOFOCUS_INTERVAL_MS);
			autoFocusHandler = null;
		} else info("Got auto-focus callback, but no handler for it");
	}

	void setHandler(Handler autoFocusHandler, int autoFocusMessage) {
		this.autoFocusHandler = autoFocusHandler;
		this.autoFocusMessage = autoFocusMessage;
	}

}
