package ch.bulletproof.countdown;

import java.sql.Date;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import ch.bulletproof.countdown.announcer.Announcement;
import ch.bulletproof.countdown.announcer.Announcer;
import ch.bulletproof.countdown.announcer.SoundAnnouncer;
import ch.bulletproof.countdown.announcer.VibrateAnnouncer;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

public class CountdownService extends Service {
	public static final String EXTRA_END_TIME = "endtime";
	public static final String EXTRA_VIBRATE = "vibrate";
	public static final String EXTRA_SOUND = "sound";

	private static final String LOG_TAG = "CountdownService";

	private Timer timer;
	private PowerManager.WakeLock wakeLock;
	private NotificationManager notificationManger;
	private CountDownTimer countdownTimer;

	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * Schedules sounds of the default voiceset
	 * 
	 * @param endTime
	 * @param sound
	 * @param vibrate
	 */
	private void scheduleSounds(final long endTime, boolean vibrate,
			boolean sound) {
		timer = new Timer();
		notificationManger = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

		List<Announcer> announcers = new ArrayList<Announcer>();

		if (vibrate) {
			announcers.add(new VibrateAnnouncer(this));
		}
		if (sound) {
			try {
				announcers.add(new SoundAnnouncer(this));
			} catch (SoundpackNotPresentException e) {
				Toast.makeText(this, "Default soundpack wasn't found", Toast.LENGTH_SHORT).show();
			}
		}
		for (Announcer announcer : announcers) {
			for (final Announcement announcement : announcer.generateAnnouncements()) {
				long schedule = endTime - 1000 * announcement.getSecondsToEnd()
						- 1000;
				if (schedule >= Calendar.getInstance().getTimeInMillis()) {
					timer.schedule(new TimerTask() {

						@Override
						public void run() {
							announcement.play();
						}

					}, new Date(schedule));
				}
			}
		}

		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				// wakeLock.release();
				try {
					// give the last sound some time to finish
					Thread.sleep(5000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				stopSelf();
			}
		}, new Date(endTime));

		countdownTimer = new CountDownTimer(endTime
				- Calendar.getInstance().getTimeInMillis() + 1000, 1000) {

			@Override
			public void onTick(long millisUntilFinished) {
				Calendar remainingTime = Calendar.getInstance();
				remainingTime.setTimeInMillis(millisUntilFinished - 1000);
				notificationManger.notify(1, buildNotification(endTime));

			}

			@Override
			public void onFinish() {
			}
		}.start();
	}

	/**
	 * Returns the given second integer as a zero-padded string
	 * 
	 * @param seconds
	 * @return
	 */
	public static String secondsAsString(int seconds) {
		String out = Integer.valueOf(seconds).toString();
		if (out.length() == 1)
			return "0" + out;
		return out;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		//TODO prevent multiple execution
		PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
		wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
				"shiny nemesis");
		wakeLock.acquire();

		long endTime = intent.getLongExtra(EXTRA_END_TIME, -1);
		boolean vibrate = intent.getBooleanExtra(EXTRA_VIBRATE, false);
		boolean sound = intent.getBooleanExtra(EXTRA_SOUND, false);

		scheduleSounds(endTime, vibrate, sound);

		Log.d(LOG_TAG, "Starting Countdown Service in Foreground");
		startForeground(1, buildNotification(endTime));
		Toast.makeText(this, getFormattedTimeRemaining(endTime) + " remaining",
				Toast.LENGTH_SHORT).show();
		return super.onStartCommand(intent, flags, startId);
	}

	@Override
	public void onCreate() {
	}

	private Notification buildNotification(long endTime) {
		String remainingString = getFormattedTimeRemaining(endTime);

		Intent notificationIntent = new Intent(this, DialogActivity.class);
		notificationIntent.putExtra(DialogActivity.INTENT_EXTRA_KILL, true);
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
				notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

		NotificationCompat.Builder builder = new NotificationCompat.Builder(
				this).setContentTitle(getString(R.string.app_name))
				.setContentText(remainingString + " left. Tap to cancel.")
				.setSmallIcon(R.drawable.ic_stat_general).setOngoing(true)
				.setWhen(endTime).setContentIntent(contentIntent);
		return builder.build();
	}

	private String getFormattedTimeRemaining(long endTime) {
		long millisUntilFinished = endTime
				- Calendar.getInstance().getTimeInMillis();

		Calendar remainingTime = Calendar.getInstance();
		remainingTime.setTimeInMillis(millisUntilFinished - 1000);
		return remainingTime.get(Calendar.MINUTE) + ":"
				+ secondsAsString(remainingTime.get(Calendar.SECOND));
	}

	@Override
	public void onDestroy() {
		Log.d(LOG_TAG, "Service destroyed");
		timer.cancel();
		countdownTimer.cancel();
		if (wakeLock.isHeld())
			wakeLock.release();
		super.onDestroy();
	}

}
