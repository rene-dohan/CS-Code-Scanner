package cs.codescanner.scanner;

import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import android.os.Handler;
import android.os.Looper;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.DecodeHintType;
import com.google.zxing.ResultPointCallback;

/**
 * This thread does all the heavy lifting of decoding the images.
 * 
 * @author dswitkin@google.com (Daniel Switkin)
 */
final class DecodeThread extends Thread {

	public static final String BARCODE_BITMAP = "barcode_bitmap";

	private final CaptureMainController _main;
	private final Map<DecodeHintType, Object> _hints;
	private Handler _handler;
	private final CountDownLatch _handlerInitLatch;

	DecodeThread(CaptureMainController activity, Collection<BarcodeFormat> decodeFormats, String characterSet,
			ResultPointCallback resultPointCallback) {
		this._main = activity;
		_handlerInitLatch = new CountDownLatch(1);
		_hints = new EnumMap<DecodeHintType, Object>(DecodeHintType.class);
		if (decodeFormats == null || decodeFormats.isEmpty()) {
			decodeFormats = EnumSet.noneOf(BarcodeFormat.class);
			decodeFormats.addAll(_main.getFormats());
		}
		_hints.put(DecodeHintType.POSSIBLE_FORMATS, decodeFormats);

		if (characterSet != null) _hints.put(DecodeHintType.CHARACTER_SET, characterSet);
		_hints.put(DecodeHintType.NEED_RESULT_POINT_CALLBACK, resultPointCallback);
	}

	@Override public void run() {
		Looper.prepare();
		_handler = new DecodeHandler(_main, _hints);
		_handlerInitLatch.countDown();
		Looper.loop();
	}

	Handler getHandler() {
		try {
			_handlerInitLatch.await();
		} catch (InterruptedException ie) {
			// continue?
		}
		return _handler;
	}

}
