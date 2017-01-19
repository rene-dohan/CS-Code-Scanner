package cs.codescanner.scanner;

import static cs.java.lang.CSLang.info;

import java.util.Collection;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.Result;

import cs.codescanner.R;
import cs.codescanner.scanner.camera.CameraManager;

public final class CaptureActivityHandler extends Handler {

	private enum State {
		PREVIEW, SUCCESS, DONE
	}

	private final CaptureMainController capture;
	private final DecodeThread decodeThread;
	private State state;
	private final CameraManager cameraManager;

	CaptureActivityHandler(CaptureMainController activity, Collection<BarcodeFormat> decodeFormats,
			String characterSet, CameraManager cameraManager) {
		capture = activity;
		decodeThread = new DecodeThread(activity, decodeFormats, characterSet,
				new ViewfinderResultPointCallback(activity.getViewfinderView()));
		decodeThread.start();
		state = State.SUCCESS;
		this.cameraManager = cameraManager;
		cameraManager.startPreview();
		restartPreviewAndDecode();
	}

	@Override public void handleMessage(Message message) {
		if (message.what == R.id.auto_focus) {
			if (state == State.PREVIEW) cameraManager.requestAutoFocus(this, R.id.auto_focus);
		} else if (message.what == R.id.restart_preview) {
			info("Got restart preview message");
			restartPreviewAndDecode();
		} else if (message.what == R.id.decode_succeeded) {
			info("Got decode succeeded message");
			state = State.SUCCESS;
			Bundle bundle = message.getData();
			Bitmap barcode = bundle == null ? null : (Bitmap) bundle
					.getParcelable(DecodeThread.BARCODE_BITMAP);
			capture.handleDecode((Result) message.obj, barcode);
		} else if (message.what == R.id.decode_failed) {
			state = State.PREVIEW;
			cameraManager.requestPreviewFrame(decodeThread.getHandler(), R.id.decode);
		} else if (message.what == R.id.return_scan_result) {
			info("Got return scan result message");
			capture.activity().setResult(Activity.RESULT_OK, (Intent) message.obj);
			capture.activity().finish();
		} else if (message.what == R.id.launch_product_query) {
			info("Got product query message");
			String url = (String) message.obj;
			Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
			intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
			capture.activity().startActivity(intent);
		}
	}

	public void quitSynchronously() {
		state = State.DONE;
		cameraManager.stopPreview();
		Message quit = Message.obtain(decodeThread.getHandler(), R.id.quit);
		quit.sendToTarget();
		try {
			decodeThread.join(500L);// Wait
		} catch (InterruptedException e) {
		}
		removeMessages(R.id.decode_succeeded);
		removeMessages(R.id.decode_failed);
	}

	private void restartPreviewAndDecode() {
		if (state == State.SUCCESS) {
			state = State.PREVIEW;
			cameraManager.requestPreviewFrame(decodeThread.getHandler(), R.id.decode);
			cameraManager.requestAutoFocus(this, R.id.auto_focus);
			capture.drawViewfinder();
		}
	}

}
