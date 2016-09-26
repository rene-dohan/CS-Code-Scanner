package cs.codescanner.scanner.camera;

import static cs.java.lang.Lang.info;
import static cs.java.lang.Lang.warn;

import java.util.Collection;

import android.app.Activity;
import android.content.Context;
import android.graphics.Point;
import android.hardware.Camera;
import android.view.Display;
import android.view.WindowManager;

/**
 * A class which deals with reading, parsing, and setting the camera parameters
 * which are used to configure the camera hardware.
 */
final class CameraConfigurationManager {

	private static final int MIN_PREVIEW_PIXELS = 320 * 240; // small screen
	private static final int MAX_PREVIEW_PIXELS = 800 * 480; // large/HD screen

	private static void doSetTorch(Camera.Parameters parameters, boolean newSetting) {
		String flashMode;
		if (newSetting) flashMode = findSettableValue(parameters.getSupportedFlashModes(),
				Camera.Parameters.FLASH_MODE_TORCH, Camera.Parameters.FLASH_MODE_ON);
		else flashMode = findSettableValue(parameters.getSupportedFlashModes(),
				Camera.Parameters.FLASH_MODE_OFF);
		if (flashMode != null) parameters.setFlashMode(flashMode);
	}

	private static Point findBestPreviewSizeValue(Camera.Parameters parameters,
			Point screenResolution, boolean portrait) {
		Point bestSize = null;
		int diff = Integer.MAX_VALUE;
		for (Camera.Size supportedPreviewSize : parameters.getSupportedPreviewSizes()) {
			int pixels = supportedPreviewSize.height * supportedPreviewSize.width;
			if (pixels < MIN_PREVIEW_PIXELS || pixels > MAX_PREVIEW_PIXELS) continue;
			int supportedWidth = portrait ? supportedPreviewSize.height : supportedPreviewSize.width;
			int supportedHeight = portrait ? supportedPreviewSize.width : supportedPreviewSize.height;
			int newDiff = Math.abs(screenResolution.x * supportedHeight - supportedWidth
					* screenResolution.y);
			if (newDiff == 0) {
				bestSize = new Point(supportedWidth, supportedHeight);
				break;
			}
			if (newDiff < diff) {
				bestSize = new Point(supportedWidth, supportedHeight);
				diff = newDiff;
			}
		}
		if (bestSize == null) {
			Camera.Size defaultSize = parameters.getPreviewSize();
			bestSize = new Point(defaultSize.width, defaultSize.height);
		}
		return bestSize;
	}

	private static String findSettableValue(Collection<String> supportedValues,
			String... desiredValues) {
		info("Supported values: " + supportedValues);
		String result = null;
		if (supportedValues != null) for (String desiredValue : desiredValues)
			if (supportedValues.contains(desiredValue)) {
				result = desiredValue;
				break;
			}
		info("Settable value: " + result);
		return result;
	}

	private final Activity activity;
	private Point screenResolution;
	private Point cameraResolution;

	CameraConfigurationManager(Activity context) {
		activity = context;
	}

	Point getCameraResolution() {
		return cameraResolution;
	}

	Point getScreenResolution() {
		return screenResolution;
	}

	/**
	 * Reads, one time, values from the camera that are needed by the app.
	 */
	void initFromCameraParameters(Camera camera) {
		Camera.Parameters parameters = camera.getParameters();
		WindowManager manager = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
		Display display = manager.getDefaultDisplay();
		int width = display.getWidth();
		int height = display.getHeight();
		if (width < height) {
			info("Display reports portrait orientation; assuming this is incorrect");
			int temp = width;
			width = height;
			height = temp;
		}
		// height -= context.getSupportActionBar().getHeight();
		screenResolution = new Point(width, height);
		info("Screen resolution: " + screenResolution);
		cameraResolution = findBestPreviewSizeValue(parameters, screenResolution, false);
		info("Camera resolution: " + cameraResolution);
	}

	void setDesiredCameraParameters(Camera camera) {
		Camera.Parameters parameters = camera.getParameters();
		if (parameters == null) {
			warn("Device error: no camera parameters are available. Proceeding without configuration.");
			return;
		}
		doSetTorch(parameters, false);
		String focusMode = findSettableValue(parameters.getSupportedFocusModes(),
				Camera.Parameters.FOCUS_MODE_AUTO, Camera.Parameters.FOCUS_MODE_MACRO);
		if (focusMode != null) parameters.setFocusMode(focusMode);

		parameters.setPreviewSize(cameraResolution.x, cameraResolution.y);
		camera.setParameters(parameters);
	}

	void setTorch(Camera camera, boolean newSetting) {
		Camera.Parameters parameters = camera.getParameters();
		doSetTorch(parameters, newSetting);
		camera.setParameters(parameters);
	}

}
