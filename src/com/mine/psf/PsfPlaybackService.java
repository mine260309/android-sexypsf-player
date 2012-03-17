package com.mine.psf;

import com.mine.psf.sexypsf.MineSexyPsfPlayer;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;

/**
 * Provides background psf playback capabilities, allowing the
 * user to switch between activities without stopping playback.
 */
public class PsfPlaybackService extends Service {
	private static final String LOGTAG = "PsfPlaybackService";
	private BroadcastReceiver mUnmountReceiver = null;
    private WakeLock mWakeLock;
    private int mServiceStartId = -1;
    private MineSexyPsfPlayer PsfPlayer = null;
    
    public static final String CMDNAME = "command";
    public static final String CMDTOGGLEPAUSE = "togglepause";
    public static final String CMDSTOP = "stop";
    public static final String CMDPAUSE = "pause";
    public static final String CMDPREVIOUS = "previous";
    public static final String CMDNEXT = "next";
    public static final String TOGGLEPAUSE_ACTION = "com.mine.psf.psfservicecmd.togglepause";
    public static final String PAUSE_ACTION = "com.mine.psf.psfservicecmd.pause";
    
	public static final String PLAYBACK_COMPLETE = "com.mine.psf.playbackcomplete";
	public static final String PLAYSTATE_CHANGED = "com.mine.psf.playstatechanged";
    
    @Override
    public void onCreate() {
    	super.onCreate();
    	
    	PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, this.getClass().getName());
        mWakeLock.setReferenceCounted(false);
        Log.d(LOGTAG, "onCreate, Acquire Wake Lock");
    }
    
    @Override
    public void onDestroy() {
    	Log.d(LOGTAG, "onDestroy, Release Wake Lock");
        mWakeLock.release();
        super.onDestroy();
    }
    
	@Override
	public IBinder onBind(Intent arg0) {
		return binder;
	}
	
    @Override
    public boolean onUnbind(Intent intent) {
        stopSelf(mServiceStartId);
        return true;
    }

    // This is the old onStart method that will be called on the pre-2.0
	// platform.  On 2.0 or later we override onStartCommand() so this
	// method will not be called.
	@Override
	public void onStart(Intent intent, int startId) {
	    handleCommand(intent, startId);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
	    handleCommand(intent, startId);
	    // We want this service to continue running until it is explicitly
	    // stopped, so return sticky.
	    return START_STICKY;
	}

	private void handleCommand(Intent intent, int startId) {
        mServiceStartId = startId;
        if (intent != null) {
            String action = intent.getAction();
            String cmd = intent.getStringExtra("command");
            Log.v(LOGTAG, "onStartCommand " + action + " / " + cmd);

            if (CMDTOGGLEPAUSE.equals(cmd) || TOGGLEPAUSE_ACTION.equals(action)) {
                if (isPlaying()) {
                    pause();
                } else {
                    play();
                }
            } else if (CMDPAUSE.equals(cmd) || PAUSE_ACTION.equals(action)) {
                pause();
            } else if (CMDSTOP.equals(cmd)) {
                stop();
            }
        }
	}

	public void openFile(String path) {
		if (PsfPlayer == null) {
			PsfPlayer = new MineSexyPsfPlayer();
		}
		PsfPlayer.Open(path);
	}
	public void stop() {
		if (PsfPlayer != null) {
			PsfPlayer.Stop();
			notifyChange(PLAYSTATE_CHANGED);
		}
	}
	public  void pause() {
		if (PsfPlayer != null) {
			PsfPlayer.Play(MineSexyPsfPlayer.PSFPAUSE);
			notifyChange(PLAYSTATE_CHANGED);
		}
	}
	public  void play() {
		if (PsfPlayer != null) {
			PsfPlayer.Play(MineSexyPsfPlayer.PSFPLAY);
			notifyChange(PLAYSTATE_CHANGED);
		}
	}
	public  boolean isPlaying() {
		//TODO maybe this flag is not valid...
		return PsfPlayer.isPlaying();
	}
	public  long duration() {
		//TODO
		return 0;
	}
	public  long position() {
		//TODO
		return 0;
	}
	public  String getTrackName() {
		//TODO
		return "";
	}
	public  String getAlbumName() {
		//TODO
		return "";
	}
	
	
    private void notifyChange(String what) {
        Intent i = new Intent(what);
        i.putExtra("album",getAlbumName());
        i.putExtra("track", getTrackName());
        sendBroadcast(i);
    }

	/**
     * Registers an intent to listen for ACTION_MEDIA_EJECT notifications.
     * The intent will call closeExternalStorageFiles() if the external media
     * is going to be ejected, so applications can clean up any files they have open.
     */
    public void registerExternalStorageListener() {
        if (mUnmountReceiver == null) {
            mUnmountReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (action.equals(Intent.ACTION_MEDIA_EJECT)) {
                        Log.d(LOGTAG, "SD Card Ejected, stop...");
                        //TODO: stop palyback and close file
                    } else if (action.equals(Intent.ACTION_MEDIA_MOUNTED)) {
                    	//TODO: ???
                        Log.d(LOGTAG, "SD Card Mounted...");
                    }
                }
            };
            IntentFilter iFilter = new IntentFilter();
            iFilter.addAction(Intent.ACTION_MEDIA_EJECT);
            iFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
            iFilter.addDataScheme("file");
            registerReceiver(mUnmountReceiver, iFilter);
        }
    }
	
	private final IBinder binder = new ServiceBinder(this);
	
	public static class ServiceBinder extends Binder {
		private final PsfPlaybackService service;
		public ServiceBinder(PsfPlaybackService service) {
			this.service = service;
		}
		public PsfPlaybackService getService() {
			return service;
		}
		public void openFile(String path) {
			service.openFile(path);
		}
		public void stop() {
			service.stop();
		}
		public void pause() {
			service.pause();
		}
		public void play() {
			service.play();
		}
		public boolean isPlaying() {
			return service.isPlaying();
		}
		public long duration() {
			return service.duration();
		}
		public long position() {
			return service.position();
		}
		public String getTrackName() {
			return service.getTrackName();
		}
		public String getAlbumName() {
			return service.getAlbumName();
		}
	}


	
}
