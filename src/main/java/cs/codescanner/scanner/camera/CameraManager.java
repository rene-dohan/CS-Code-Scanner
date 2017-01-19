package cs.codescanner.scanner.camera;

import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.view.SurfaceHolder;

import java.io.IOException;

import cs.android.viewbase.CSViewController;
import cs.codescanner.scanner.CaptureMainController;
import cs.codescanner.scanner.PlanarYUVLuminanceSource;

/**
 * This object wraps the Camera service object and expects to be the only one
 * talking to it. The implementation encapsulates the steps needed to take
 * preview-sized images, which are used for both preview and decoding.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
public final class CameraManager {

    private static final String TAG = CameraManager.class.getSimpleName();

    private static final int MIN_FRAME_WIDTH = 240;
    private static final int MIN_FRAME_HEIGHT = 240;
    private static final int MAX_FRAME_WIDTH = 700;
    private static final int MAX_FRAME_HEIGHT = 400;

    private final CameraConfigurationManager _configManager;
    private final CSViewController _controller;
    private Camera _camera;
    private Rect _framingRect;
    private Rect _framingRectInPreview;
    private boolean _initialized;
    private boolean _previewing;
    private boolean _reverseImage;
    private int _requestedFramingRectWidth;
    private int _requestedFramingRectHeight;
    /**
     * Preview frames are delivered here, which we pass on to the registered
     * handler. Make sure to clear the handler so it will only receive one
     * message.
     */
    private final PreviewCallback previewCallback;
    /**
     * Autofocus callbacks arrive here, and are dispatched to the Handler which
     * requested them.
     */
    private final AutoFocusCallback autoFocusCallback;

    public CameraManager(CaptureMainController controller) {
        _controller = controller;
        _configManager = new CameraConfigurationManager(controller);
        previewCallback = new PreviewCallback(_configManager);
        autoFocusCallback = new AutoFocusCallback();
    }

    /**
     * A factory method to build the appropriate LuminanceSource object based on
     * the format of the preview buffers, as described by Camera.Parameters.
     *
     * @param data   A preview frame.
     * @param width  The width of the image.
     * @param height The height of the image.
     * @return A PlanarYUVLuminanceSource instance.
     */
    public PlanarYUVLuminanceSource buildLuminanceSource(byte[] data, int width, int height) {
        Rect rect = getFramingRectInPreview();
        if (rect == null) return null;
        // Go ahead and assume it's YUV rather than die.
        return new PlanarYUVLuminanceSource(data, width, height, rect.left, rect.top, rect.width(),
                rect.height(), _reverseImage);
    }

    /**
     * Closes the camera driver if still in use.
     */
    public void closeDriver() {
        if (_camera != null) {
            _camera.release();
            _camera = null;
            // Make sure to clear these each time we close the camera, so that
            // any scanning rect
            // requested by intent is forgotten.
            _framingRect = null;
            _framingRectInPreview = null;
        }
    }

    /**
     * Calculates the framing rect which the UI should draw to show the user where
     * to place the barcode. This target helps with alignment as well as forces
     * the user to hold the device far enough away to ensure the image will be in
     * focus.
     *
     * @return The rectangle to draw on screen in window coordinates.
     */
    public Rect getFramingRect() {
        if (_framingRect == null) {
            if (_camera == null) return null;
            Point screenResolution = _configManager.getScreenResolution();
            int width = screenResolution.x * 3 / 4;
            if (width < MIN_FRAME_WIDTH) width = MIN_FRAME_WIDTH;
            else if (width > MAX_FRAME_WIDTH) width = MAX_FRAME_WIDTH;
            int height = screenResolution.y * 4 / 5;
            if (height < MIN_FRAME_HEIGHT) height = MIN_FRAME_HEIGHT;
            else if (height > MAX_FRAME_HEIGHT) height = MAX_FRAME_HEIGHT;
            int leftOffset = (screenResolution.x - width) / 2;
            int topOffset = (screenResolution.y - height) / 2;
            _framingRect = new Rect(leftOffset, topOffset, leftOffset + width, topOffset + height);
            Log.d(TAG, "Calculated framing rect: " + _framingRect);
        }
        return _framingRect;
    }

    /**
     * Like {@link #getFramingRect} but coordinates are in terms of the preview
     * frame, not UI / screen.
     */
    public Rect getFramingRectInPreview() {
        if (_framingRectInPreview == null) {
            Rect framingRect = getFramingRect();
            if (framingRect == null) return null;
            Rect rect = new Rect(framingRect);
            Point cameraResolution = _configManager.getCameraResolution();
            Point screenResolution = _configManager.getScreenResolution();
            rect.left = rect.left * cameraResolution.x / screenResolution.x;
            rect.right = rect.right * cameraResolution.x / screenResolution.x;
            rect.top = rect.top * cameraResolution.y / screenResolution.y;
            rect.bottom = rect.bottom * cameraResolution.y / screenResolution.y;
            _framingRectInPreview = rect;
        }
        return _framingRectInPreview;
    }

    /**
     * Opens the camera driver and initializes the hardware parameters.
     *
     * @param holder The surface object which the camera will draw preview frames into.
     * @throws IOException Indicates the camera driver failed to open.
     */
    public void openDriver(SurfaceHolder holder) throws IOException {
        Camera theCamera = _camera;
        if (theCamera == null) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD) theCamera = Camera.open();
            else theCamera = Camera.open(0);
            if (theCamera == null) throw new IOException();
            _camera = theCamera;
        }
        theCamera.setPreviewDisplay(holder);

        if (!_initialized) {
            _initialized = true;
            _configManager.initFromCameraParameters(theCamera);
            if (_requestedFramingRectWidth > 0 && _requestedFramingRectHeight > 0) {
                setManualFramingRect(_requestedFramingRectWidth, _requestedFramingRectHeight);
                _requestedFramingRectWidth = 0;
                _requestedFramingRectHeight = 0;
            }
        }
        _configManager.setDesiredCameraParameters(theCamera);
        _reverseImage = false;
    }

    /**
     * Asks the camera hardware to perform an autofocus.
     *
     * @param handler The Handler to notify when the autofocus completes.
     * @param message The message to deliver.
     */
    public void requestAutoFocus(Handler handler, int message) {
        if (_camera != null && _previewing) {
            autoFocusCallback.setHandler(handler, message);
            try {
                _camera.autoFocus(autoFocusCallback);
            } catch (RuntimeException re) {
                // Have heard RuntimeException reported in Android 4.0.x+;
                // continue?
                Log.w(TAG, "Unexpected exception while focusing", re);
            }
        }
    }

    /**
     * A single preview frame will be returned to the handler supplied. The data
     * will arrive as byte[] in the message.obj field, with width and height
     * encoded as message.arg1 and message.arg2, respectively.
     *
     * @param handler The handler to send the message to.
     * @param message The what field of the message to be sent.
     */
    public void requestPreviewFrame(Handler handler, int message) {
        Camera theCamera = _camera;
        if (theCamera != null && _previewing) {
            previewCallback.setHandler(handler, message);
            theCamera.setOneShotPreviewCallback(previewCallback);
        }
    }

    /**
     * Allows third party apps to specify the scanning rectangle dimensions,
     * rather than determine them automatically based on screen resolution.
     *
     * @param width  The width in pixels to scan.
     * @param height The height in pixels to scan.
     */
    public void setManualFramingRect(int width, int height) {
        if (_initialized) {
            Point screenResolution = _configManager.getScreenResolution();
            if (width > screenResolution.x) width = screenResolution.x;
            if (height > screenResolution.y) height = screenResolution.y;
            int leftOffset = (screenResolution.x - width) / 2;
            int topOffset = (screenResolution.y - height) / 2;
            _framingRect = new Rect(leftOffset, topOffset, leftOffset + width, topOffset + height);
            Log.d(TAG, "Calculated manual framing rect: " + _framingRect);
            _framingRectInPreview = null;
        } else {
            _requestedFramingRectWidth = width;
            _requestedFramingRectHeight = height;
        }
    }

    public void setTorch(boolean isChecked) {
        _configManager.setTorch(_camera, isChecked);
    }

    /**
     * Asks the camera hardware to begin drawing preview frames to the screen.
     */
    public void startPreview() {
        Camera theCamera = _camera;
        if (theCamera != null && !_previewing) {
            theCamera.startPreview();
            _previewing = true;
        }
    }

    /**
     * Tells the camera to stop drawing preview frames.
     */
    public void stopPreview() {
        if (_camera != null && _previewing) {
            _camera.stopPreview();
            previewCallback.setHandler(null, 0);
            autoFocusCallback.setHandler(null, 0);
            _previewing = false;
        }
    }

}
