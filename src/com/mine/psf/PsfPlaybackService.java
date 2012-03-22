/*************************************************************************
 * MinePsfPlayer is an Android App that plays psf and minipsf files.
 * Copyright (C) 2010  Lei YU
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ************************************************************************/

package com.mine.psf;

import java.util.LinkedList;
import java.util.Random;

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
    
	// playlist that should be set by browser
	private String[] playList = null;
	private boolean playShuffle = false;
	private int[] shuffleList = null;
	private int curPos;

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

	// Set the playlist with full path
	public void setPlaylist(String[] list, boolean shuffle) {
		synchronized(this) {
			playList = list;
			playShuffle = shuffle;
			generateShuffleList();
		}
	}

	public void openFile(String path) {
		synchronized(this) {
			if (PsfPlayer == null) {
				PsfPlayer = new MineSexyPsfPlayer();
			}
			if (PsfPlayer.isActive()) {
				PsfPlayer.Stop();
			}
			Log.d(LOGTAG, "openFile: " + path);
			PsfPlayer.Open(path);
		}
	}
	public void stop() {
		synchronized(this) {
			if (PsfPlayer != null) {
				Log.d(LOGTAG, "stop");
				PsfPlayer.Stop();
				notifyChange(PLAYSTATE_CHANGED);
			}
		}
	}
	public void pause() {
		synchronized(this) {
			if (PsfPlayer != null) {
				Log.d(LOGTAG, "pause");
				PsfPlayer.Play(MineSexyPsfPlayer.PSFPAUSE);
				notifyChange(PLAYSTATE_CHANGED);
			}
		}
	}
	public void play() {
		synchronized(this) {
			if (PsfPlayer != null) {
				Log.d(LOGTAG, "play");
				PsfPlayer.Play(MineSexyPsfPlayer.PSFPLAY);
				notifyChange(PLAYSTATE_CHANGED);
			}
		}
	}
	
	// This function opens the file in playlist and play it
	public void play(int pos) {
		synchronized(this) {
			if (pos < 0 || pos >= playList.length) {
				Log.e(LOGTAG, "play pos out of range, pos: " + pos
						+ ", len: " + playList.length);
				return;
			}
			curPos = pos;
			int playPos;
			if (playShuffle) {
				playPos = shuffleList[pos];
			}
			else {
				playPos = pos;
			}
			openFile(playList[playPos]);
			play();
		}
	}

	public void next() {
		synchronized(this) {
			Log.d(LOGTAG, "next");
			int pos = goNext();
			openFile(playList[pos]);
			play();
		}
	}

	public void prev() {
		synchronized(this) {
			Log.d(LOGTAG, "prev");
			int pos = goPrev();
			openFile(playList[pos]);
			play();
		}
	}

	public void setShuffle(boolean shuffle) {
		synchronized(this) {
			playShuffle = shuffle;
		}
	}

	public boolean isPlaying() {
		//TODO maybe this flag is not valid...
		if (PsfPlayer != null) {
			return PsfPlayer.isPlaying();
		}
		return false;
	}
	
	public boolean isActive() {
		//TODO maybe this flag is not valid...
		if (PsfPlayer != null) {
			return PsfPlayer.isActive();
		}
		return false;
	}
	
	public long duration() {
		synchronized(this) {
		//TODO
		return 0;
		}
	}
	public long position() {
		synchronized(this) {
		//TODO
		return 0;
		}
	}
	public String getTrackName() {
		synchronized(this) {
		//TODO
		return "";
		}
	}
	public String getAlbumName() {
		synchronized(this) {
		//TODO
		return "";
		}
	}
	
    public String getArtistName() {
		synchronized(this) {
			return "";
		}
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
	
    private void generateShuffleList() {
    	shuffleList = new int[playList.length];
    	if (playShuffle) {
    		// make a shuffle list
    		// algro: get rand(),
    		LinkedList<Integer> tmpList = new LinkedList<Integer>();
    		for (int i=0; i < playList.length; ++i) {
    			tmpList.add(i);
    		}
    		Random r = new Random();
    		for (int i=0; i < playList.length; ++i) {
    			int tmp = r.nextInt(playList.length-i);
    			shuffleList[i] = tmpList.get(tmp);
    			tmpList.remove(tmp);
    		}
    	}
    	else {
    		for (int i = 0; i<playList.length; ++i) {
    			shuffleList[i] = i;
    		}
    	}
    	StringBuilder sb = new StringBuilder();
    	for (int i = 0; i < playList.length; ++i) {
    		sb.append(shuffleList[i]);
    		sb.append(",");
    	}
    	Log.d(LOGTAG, "GetShuffleList: " + sb.toString());
    }
    
    private int goNext() {
    	if (shuffleList == null) {
    		return 0;
    	}
    	if (playShuffle) {
    		return shuffleList[++curPos];
    	}
    	else {
    		return ++curPos;
    	}
    }
    
    private int goPrev() {
    	if (shuffleList == null) {
    		return 0;
    	}
    	if (playShuffle) {
    		return shuffleList[--curPos];
    	}
    	else {
    		return --curPos;
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
		public boolean isActive() {
			return service.isActive();
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
