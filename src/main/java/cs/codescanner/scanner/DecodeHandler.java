package cs.codescanner.scanner;

import static cs.java.lang.CSLang.is;

import java.util.Map;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import cs.codescanner.R;
import cs.codescanner.scanner.camera.CameraManager;

final class DecodeHandler extends Handler {

	private final CaptureMainController activity;
	private final MultiFormatReader multiFormatReader;
	private boolean running = true;

	DecodeHandler(CaptureMainController activity, Map<DecodeHintType, Object> hints) {
		multiFormatReader = new MultiFormatReader();
		multiFormatReader.setHints(hints);
		this.activity = activity;
	}

	@Override public void handleMessage(Message message) {
		if (!running) return;
		if (message.what == R.id.decode) decode((byte[]) message.obj, message.arg1, message.arg2);
		else if (message.what == R.id.quit) {
			running = false;
			Looper.myLooper().quit();
		}
	}

	private void decode(byte[] data, int width, int height) {
		try {
			CameraManager cameraManager = activity.getCameraManager();
			PlanarYUVLuminanceSource source = cameraManager.buildLuminanceSource(data, width, height);
			BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
			Result rawResult = multiFormatReader.decodeWithState(bitmap);
			Message message = Message.obtain(activity.handler(), R.id.decode_succeeded, rawResult);
			Bundle bundle = new Bundle();
			bundle.putParcelable(DecodeThread.BARCODE_BITMAP, source.renderCroppedGreyscaleBitmap());
			message.setData(bundle);
			message.sendToTarget();
		} catch (Exception ex) {
			if (is(activity.handler()))
				Message.obtain(activity.handler(), R.id.decode_failed).sendToTarget();
		} finally {
			multiFormatReader.reset();
		}
	}

}
