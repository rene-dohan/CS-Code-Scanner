package cs.codescanner.scanner;

import static cs.java.lang.CSLang.error;

import java.io.IOException;

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.os.Vibrator;
import cs.codescanner.R;

final class BeepManager {

	private static final float BEEP_VOLUME = 0.10f;
	private static final long VIBRATE_DURATION = 200L;

	private static MediaPlayer buildMediaPlayer(Context activity) {
		MediaPlayer mediaPlayer = new MediaPlayer();
		mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
		mediaPlayer.setOnCompletionListener(new OnCompletionListener() {
			public void onCompletion(MediaPlayer player) {
				player.seekTo(0);
			}
		});
		AssetFileDescriptor file = activity.getResources().openRawResourceFd(R.raw.beep);
		try {
			mediaPlayer.setDataSource(file.getFileDescriptor(), file.getStartOffset(), file.getLength());
			file.close();
			mediaPlayer.setVolume(BEEP_VOLUME, BEEP_VOLUME);
			mediaPlayer.prepare();
		} catch (IOException ioe) {
			error(ioe);
			mediaPlayer = null;
		}
		return mediaPlayer;
	}

	private final Activity activity;
	private MediaPlayer mediaPlayer;
	private boolean playBeep;
	private boolean vibrate;

	BeepManager(Activity activity) {
		this.activity = activity;
		mediaPlayer = null;
		updatePrefs();
	}

	void playBeepSoundAndVibrate() {
		if (playBeep && mediaPlayer != null) mediaPlayer.start();
		if (vibrate) {
			Vibrator vibrator = (Vibrator) activity.getSystemService(Context.VIBRATOR_SERVICE);
			vibrator.vibrate(VIBRATE_DURATION);
		}
	}

	void updatePrefs() {
		playBeep = true;
		vibrate = false;
		if (playBeep && mediaPlayer == null) {
			activity.setVolumeControlStream(AudioManager.STREAM_MUSIC);
			mediaPlayer = buildMediaPlayer(activity);
		}
	}

}
