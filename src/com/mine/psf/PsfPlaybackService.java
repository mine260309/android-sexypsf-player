/*************************************************************************
 * MinePsfPlayer is an Android App that plays psf and minipsf files.
 * Copyright (C) 2010-2012  Lei YU
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

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.LinkedList;
import java.util.Random;

import com.mine.psf.sexypsf.MineSexyPsfPlayer;
import com.mine.psf.sexypsf.MineSexyPsfPlayer.RepeatState;
import com.mine.psfplayer.R;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;

/**
 * Provides background psf playback capabilities, allowing the
 * user to switch between activities without stopping playback.
 */
@TargetApi(Build.VERSION_CODES.FROYO)
public class PsfPlaybackService extends Service
    implements MineSexyPsfPlayer.PsfPlayerState {

  private static final String LOGTAG = "PsfPlaybackService";
  public static final int PLAYBACKSERVICE_STATUS = 1;

  private BroadcastReceiver mUnmountReceiver = null;
  private BroadcastReceiver mNoisyReceiver = null;
  private WakeLock mWakeLock;
  private int mServiceStartId = -1;
  private AudioManager PsfAudioManager;
  private ComponentName PsfControlResponder;
  private MineSexyPsfPlayer PsfPlayer;

  public static final String CMDNAME = "command";
  public static final String CMDTOGGLEPAUSE = "togglepause";
  public static final String CMDPLAY = "play";
  public static final String CMDSTOP = "stop";
  public static final String CMDPAUSE = "pause";
  public static final String CMDPREV = "previous";
  public static final String CMDNEXT = "next";
  public static final String ACTION_CMD = "com.mine.psf.servicecmd";
  //public static final String ACTION_TOGGLE_PLAYPAUSE = "com.mine.psf.psfservicecmd.togglepause";
  //public static final String ACTION_PAUSE = "com.mine.psf.psfservicecmd.pause";

  public static final String META_CHANGED = "com.mine.psf.metachanged";
  public static final String PLAYBACK_COMPLETE = "com.mine.psf.playbackcomplete";
  public static final String PLAYSTATE_CHANGED = "com.mine.psf.playstatechanged";
  //public static final String PLAYSTATE_FAILED = "com.mine.psf.playstatefailed";
  public static final String REPEATSTATE_CHANGED = "com.mine.psf.repeatstatechanged";

  public static final int MSG_JUMP_NEXT = STATE_MSG_MAX + 1;
  public static final int MSG_JUMP_PREV = STATE_MSG_MAX + 2;

  // interval after which we stop the service when idle
  private static final int IDLE_DELAY = 10 * 1000 * 60;

  // playlist that should be set by browser
  private String[] playList = null;
  private boolean playShuffle = false;
  private int[] shuffleList = null;
  private int curPos;
  private boolean mServiceInUse = false;
  private String playingFile;

  private SharedPreferences mPreferences;
  // Save playlist and shufflelist
  private static final String SavedListFileName = "savedList";

  @Override
  public void onCreate() {
    super.onCreate();

    PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
    mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, this.getClass().getName());
    mWakeLock.setReferenceCounted(false);
    Log.d(LOGTAG, "onCreate, Acquire Wake Lock");

    PsfAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
    PsfControlResponder = new ComponentName(getPackageName(),
        RemoteControlReceiver.class.getName());
    PsfAudioManager.registerMediaButtonEventReceiver(PsfControlResponder);

    mPreferences = PreferenceManager
        .getDefaultSharedPreferences(this);
    reloadQueue();

    // If the service was idle, but got killed before it stopped itself, the
    // system will relaunch it. Make sure it gets stopped again in that case.
    Message msg = mDelayedStopHandler.obtainMessage();
    mDelayedStopHandler.sendMessageDelayed(msg, IDLE_DELAY);

    // Register sd card mount/unmount listener
    registerExternalStorageListener();

    // Register noisy listener, it should
    // 1) Playing with headset, unplug headset, sound should be paused;
    // 2) Playing with speaker, plug headset, sound should keep playing.
    registerNoisyListener();
  }

  @Override
  public void onDestroy() {
    // Check that we're not being destroyed while something is still playing.
    if (isPlaying()) {
      Log.e(LOGTAG, "Service being destroyed while still playing.");
    }
    if (PsfPlayer != null) {
      cancelAudioFocus();
      PsfPlayer.Stop();
      closeFile();
      PsfPlayer = null;
    }

    // make sure there aren't any other messages coming
    mDelayedStopHandler.removeCallbacksAndMessages(null);
    mMediaplayerHandler.removeCallbacksAndMessages(null);

    //unregisterReceiver(mIntentReceiver);
    if (mUnmountReceiver != null) {
      unregisterReceiver(mUnmountReceiver);
      mUnmountReceiver = null;
    }

    if (mNoisyReceiver != null) {
      unregisterReceiver(mNoisyReceiver);
      mNoisyReceiver = null;
    }
    PsfAudioManager.unregisterMediaButtonEventReceiver(PsfControlResponder);

    Log.d(LOGTAG, "onDestroy, Release Wake Lock");
    mWakeLock.release();
    super.onDestroy();
  }

  @Override
  public IBinder onBind(Intent arg0) {
    mDelayedStopHandler.removeCallbacksAndMessages(null);
    mServiceInUse = true;
    PsfAudioManager.registerMediaButtonEventReceiver(PsfControlResponder);
    return binder;
  }

  @Override
  public void onRebind(Intent intent) {
    mDelayedStopHandler.removeCallbacksAndMessages(null);
    mServiceInUse = true;
    PsfAudioManager.registerMediaButtonEventReceiver(PsfControlResponder);
  }

  @Override
  public boolean onUnbind(Intent intent) {
    mServiceInUse = false;
    saveQueue();
    if (PsfPlayer != null) {
      if (PsfPlayer.isPlaying() /* || mPausedByTransientLossOfFocus*/) {
        // something is currently playing, don't stop the service now.
        return true;
      }

      // If there is a playlist but playback is paused, then wait a while
      // before stopping the service, so that pause/resume isn't slow.
      // Also delay stopping the service if we're transitioning between tracks.
      if (playList.length > 0 || mMediaplayerHandler.hasMessages(STATE_STOPPED)) {
        Message msg = mDelayedStopHandler.obtainMessage();
        mDelayedStopHandler.sendMessageDelayed(msg, IDLE_DELAY);
        Log.d(LOGTAG, "wait a while before stopping service");
        return true;
      }
    }
    Log.d(LOGTAG, "stop service");
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
    mDelayedStopHandler.removeCallbacksAndMessages(null);
    if (intent != null) {
      String action = intent.getAction();
      String cmd = intent.getStringExtra(CMDNAME);
      Log.v(LOGTAG, "onStartCommand " + action + " / " + cmd);
      if (action != null && cmd != null) {
        if (!action.equals(ACTION_CMD)) {
          Log.w(LOGTAG, "Unknown action");
        } else {
          if (CMDTOGGLEPAUSE.equals(cmd)) {
            if (isPlaying()) {
              pause();
            } else {
              play();
            }
          } else if (CMDPLAY.equals(cmd)) {
            if (!isPlaying()) {
              play();
            }
          } else if (CMDPAUSE.equals(cmd)) {
            if (isPlaying()) {
              pause();
            }
          } else if (CMDSTOP.equals(cmd)) {
            stop();
          } else if (CMDNEXT.equals(cmd)) {
            next();
          } else if (CMDPREV.equals(cmd)) {
            prev();
          }
        }
      }
    }
    PsfAudioManager.registerMediaButtonEventReceiver(PsfControlResponder);
    // make sure the service will shut down on its own if it was
    // just started but not bound to and nothing is playing
    mDelayedStopHandler.removeCallbacksAndMessages(null);
    Message msg = mDelayedStopHandler.obtainMessage();
    mDelayedStopHandler.sendMessageDelayed(msg, IDLE_DELAY);
  }

  private Handler mMediaplayerHandler = new Handler() {
    @Override
    public void handleMessage(Message msg) {
      switch (msg.what) {
        case STATE_STOPPED:
          Log.v(LOGTAG, "get STOPPED message");
          autoNext();
          //notifyChange(PLAYBACK_COMPLETE);
          break;
        //case RELEASE_WAKELOCK:
        //    mWakeLock.release();
        //    break;
        case MSG_JUMP_NEXT:
          Log.v(LOGTAG, "get NEXT message");
          next();
          break;
        case MSG_JUMP_PREV:
          Log.v(LOGTAG, "get PREV message");
          prev();
          break;
        default:
          break;
      }
    }
  };

  private Handler mDelayedStopHandler = new Handler() {
    @Override
    public void handleMessage(Message msg) {
      // Check again to make sure nothing is playing right now
      if (isPlaying() || /*mPausedByTransientLossOfFocus ||*/ mServiceInUse
          || mMediaplayerHandler.hasMessages(STATE_STOPPED)) {
        Log.d(LOGTAG, "ignore stop msg");
        return;
      }
      // save the queue again, because it might have changed
      // since the user exited the music app (because of
      // party-shuffle or because the play-position changed)
      saveQueue();
      Log.d(LOGTAG, "stop service");
      stopSelf(mServiceStartId);
    }
  };

  // Set the playlist with full path
  public void setPlaylist(String[] list, boolean shuffle) {
    synchronized (this) {
      playList = list;
      playShuffle = shuffle;
      generateShuffleList();
      saveQueue();
    }
  }

  private boolean openFile(String path) {
    synchronized (this) {
      if (PsfPlayer == null) {
        PsfPlayer = new MineSexyPsfPlayer();
        PsfPlayer.setHandler(mMediaplayerHandler);
      }
      if (PsfPlayer.isActive()) {
        cancelAudioFocus();
        PsfPlayer.Stop();
        closeFile();
      }
      boolean ret;
      playingFile = path;
      path = PsfFileNavigationUtils.getPsfPath(this, path);
      ret = PsfPlayer.Open(path);
      if (ret) {
        notifyChange(META_CHANGED);
        Log.d(LOGTAG, "openFile: " + path + " successfully");
      } else {
        Log.d(LOGTAG, "openFile: " + path + " failed!");
        Toast.makeText(this, R.string.psf_open_fail, Toast.LENGTH_LONG).show();
      }
      return ret;
    }
  }

  private void closeFile() {
    PsfFileNavigationUtils.cleanupPsfPath(this, playingFile);
  }

  public void stop() {
    synchronized (this) {
      if (PsfPlayer != null) {
        Log.d(LOGTAG, "stop");
        cancelAudioFocus();
        PsfPlayer.Stop();
        closeFile();
        gotoIdleState();
        notifyChange(PLAYSTATE_CHANGED);
      }
    }
  }

  public void pause() {
    synchronized (this) {
      if (PsfPlayer != null) {
        Log.d(LOGTAG, "pause");
        PsfPlayer.Play(MineSexyPsfPlayer.PSFPAUSE);
        cancelAudioFocus();
        gotoIdleState();
        notifyChange(PLAYSTATE_CHANGED);
      }
    }
  }

  public void play() {
    synchronized (this) {
      if (PsfPlayer != null) {
        Log.d(LOGTAG, "play");
        requestAudioFocus();
        PsfPlayer.Play(MineSexyPsfPlayer.PSFPLAY);
        notifyChange(PLAYSTATE_CHANGED);
        notifyPlaying();
      }
    }
  }

  private void open(int pos) {
    if (pos < 0 || pos >= playList.length) {
      Log.e(LOGTAG, "open pos out of range, pos: " + pos
          + ", len: " + playList.length);
      return;
    }
    curPos = pos;
    int playPos;
    if (playShuffle) {
      playPos = shuffleList[pos];
    } else {
      playPos = pos;
    }
    boolean ret;
    ret = openFile(playList[playPos]);
    if (!ret) {
      removeFromList(pos);
      curPos--;
    }
  }

  // This function opens the file in playlist and play it
  public void play(int pos) {
    synchronized (this) {
      open(pos);
      play();
      saveCurPos();
    }
  }

  public void next() {
    synchronized (this) {
      Log.d(LOGTAG, "next");
      int pos = goNext(true);
      if (pos == -1) {
        Log.d(LOGTAG, "No Next Track!");
        return;
      }
      boolean ret;
      ret = openFile(playList[pos]);
      if (ret) {
        play();
        saveCurPos();
      } else {
        // Remove the file from list and next
        removeFromList(pos);
        curPos--;
        mMediaplayerHandler.sendEmptyMessage(MSG_JUMP_NEXT);
      }
    }
  }

  public void prev() {
    synchronized (this) {
      Log.d(LOGTAG, "prev");
      int pos = goPrev();
      if (pos == -1) {
        Log.e(LOGTAG, "No Prev Track!");
        return;
      }
      boolean ret;
      ret = openFile(playList[pos]);
      if (ret) {
        play();
        saveCurPos();
      } else {
        // Remove the file from list and go prev
        removeFromList(pos);
        curPos++;
        mMediaplayerHandler.sendEmptyMessage(MSG_JUMP_PREV);
      }
    }
  }

  public void repeat() {
    synchronized (this) {
      if (PsfPlayer != null) {
        Log.d(LOGTAG, "repeat");
        PsfPlayer.ToggleRepeat();
        notifyChange(REPEATSTATE_CHANGED);
      }
    }
  }

  public int getRepeatState() {
    int ret = RepeatState.REPEAT_OFF;
    if (PsfPlayer != null) {
      ret = PsfPlayer.GetRepeatState();
    }
    Log.d(LOGTAG, "getRepeatState: " + ret);
    return ret;
  }

  public int getPlaylistPosition() {
    if (playShuffle && shuffleList != null) {
      return shuffleList[curPos];
    } else {
      return curPos;
    }
  }

  public String[] getPlaylist() {
    return playList;
  }

  public void setShuffle(boolean shuffle) {
    synchronized (this) {
      playShuffle = shuffle;
      saveShuffleState();
    }
  }

  public boolean isPlaying() {
    if (PsfPlayer != null) {
      return PsfPlayer.isPlaying();
    }
    return false;
  }

  public boolean isActive() {
    if (PsfPlayer != null) {
      return PsfPlayer.isActive();
    }
    return false;
  }

  public long duration() {
    synchronized (this) {
      if (PsfPlayer != null) {
        return PsfPlayer.GetDuration();
      }
      return 0;
    }
  }

  public long position() {
    synchronized (this) {
      if (PsfPlayer != null) {
        return PsfPlayer.GetPosition();
      }
      return 0;
    }
  }

  public String getTrackName() {
    synchronized (this) {
      if (PsfPlayer != null) {
        return PsfPlayer.GetTrack();
      }
      return "";
    }
  }

  public String getAlbumName() {
    synchronized (this) {
      if (PsfPlayer != null) {
        return PsfPlayer.GetAlbum();
      }
      return "";
    }
  }

  public String getArtistName() {
    if (PsfPlayer != null) {
      return PsfPlayer.GetArtist();
    }
    return "";
  }

  public void quit() {
    if (PsfPlayer != null) {
      cancelAudioFocus();
      PsfPlayer.Quit();
    }
    gotoIdleState();
    stopSelf(mServiceStartId);
  }

  private void notifyChange(String what) {
    Intent i = new Intent(what);
    //i.putExtra("album", getAlbumName());
    //i.putExtra("track", getTrackName());
    sendBroadcast(i);
  }

  private void removeFromList(int pos) {
    // Remove the file from the list at pos
    // This includes removing the file from playList
    // and update the shufflelist, curPos
    // It's a slow process since it's re-constructing the array
    if (pos < 0 || pos >= playList.length) {
      return;
    }
    Log.d(LOGTAG, "remove file " + playList[pos] + " from list, pos: " + pos);
    //Log.d(LOGTAG, "Before removing:");
    //dumpPlayList();

    String[] newList = new String[playList.length - 1];
    int[] newShuffleList = new int[shuffleList.length - 1];
    int shuffleIndex = 0;
    for (int i = 0; i < playList.length; ++i) {
      // copy to new play list
      if (i < pos) {
        newList[i] = playList[i];
      } else if (i > pos) {
        newList[i - 1] = playList[i];
      }

      // copy to new shuffle list
      if (shuffleList[i] == pos) {
        continue;
      }
      newShuffleList[shuffleIndex] = shuffleList[i];
      if (newShuffleList[shuffleIndex] > pos) {
        newShuffleList[shuffleIndex]--;
      }
      shuffleIndex++;
    }
    playList = newList;
    shuffleList = newShuffleList;
    //Log.d(LOGTAG, "After removing:");
    //dumpPlayList();
  }
/*
    private void dumpPlayList() {
    	Log.d(LOGTAG, "Dump Playlist...");
    	StringBuffer sb = new StringBuffer();
    	for (int i = 0; i < playList.length; ++i) {
    		Log.d(LOGTAG, playList[i]);
    		sb.append(shuffleList[i] + " ");
    	}
    	Log.d(LOGTAG, "Shuffle List: " + sb.toString());
    }
*/

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
            saveQueue();
            stop();
            notifyChange(META_CHANGED);
          } else if (action.equals(Intent.ACTION_MEDIA_MOUNTED)) {
            reloadQueue();
            notifyChange(META_CHANGED);
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

  private void registerNoisyListener() {
    if (mNoisyReceiver == null) {
      mNoisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          String action = intent.getAction();
          if (action.equals(AudioManager.ACTION_AUDIO_BECOMING_NOISY)) {
            Log.d(LOGTAG, "To become noisy, pause");
            pause();
          }
        }
      };
      IntentFilter filter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
      registerReceiver(mNoisyReceiver, filter);
    }
  }

  private void generateShuffleList() {
    shuffleList = new int[playList.length];
    if (playShuffle) {
      // make a shuffle list
      // algro: get rand(),
      LinkedList<Integer> tmpList = new LinkedList<Integer>();
      for (int i = 0; i < playList.length; ++i) {
        tmpList.add(i);
      }
      Random r = new Random();
      for (int i = 0; i < playList.length; ++i) {
        int tmp = r.nextInt(playList.length - i);
        shuffleList[i] = tmpList.get(tmp);
        tmpList.remove(tmp);
      }
    } else {
      for (int i = 0; i < playList.length; ++i) {
        shuffleList[i] = i;
      }
    }
//    	StringBuilder sb = new StringBuilder();
//    	for (int i = 0; i < playList.length; ++i) {
//    		sb.append(shuffleList[i]);
//    		sb.append(",");
//    	}
//    	Log.d(LOGTAG, "GetShuffleList: " + sb.toString());
  }

  // forceNext true to ignore REPEAT_ONE case
  private int goNext(boolean forceNext) {
    if (shuffleList == null) {
      return 0;
    }
    int repeatState = PsfPlayer.GetRepeatState();

    // Loop-one in case of auto next
    if (!forceNext && repeatState == RepeatState.REPEAT_ONE) {
      curPos--;
    }

    if (curPos + 1 >= playList.length) {
      if (repeatState != RepeatState.REPEAT_ALL) {
        // Play to the end
        return -1;
      } else {
        // Loop-all
        curPos = -1;
      }
    }
    if (playShuffle) {
      return shuffleList[++curPos];
    } else {
      return ++curPos;
    }
  }

  private int goPrev() {
    if (shuffleList == null) {
      return 0;
    }
    int repeatState = PsfPlayer.GetRepeatState();

    if (curPos == 0) {
      // At beginning of playlist
      if (repeatState != RepeatState.REPEAT_ALL) {
        return -1;
      } else {
        // Loop-all
        curPos = playList.length;
      }
    }
    if (playShuffle) {
      return shuffleList[--curPos];
    } else {
      return --curPos;
    }
  }

  /**
   * Go to next music when a psf play to the end
   * If it's the last item in the playlist, stop
   */
  private void autoNext() {
    synchronized (this) {
      Log.d(LOGTAG, "autonext");
      int pos = goNext(false);
      if (pos == -1) {
        Log.d(LOGTAG, "No Next Track!");
        return;
      }
      boolean ret;
      ret = openFile(playList[pos]);
      if (ret) {
        play();
        saveCurPos();
      } else {
        // Remove the file from list and next
        removeFromList(pos);
        curPos--;
        mMediaplayerHandler.sendEmptyMessage(MSG_JUMP_NEXT);
      }
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
  }

  private void gotoIdleState() {
    mDelayedStopHandler.removeCallbacksAndMessages(null);
    Message msg = mDelayedStopHandler.obtainMessage();
    mDelayedStopHandler.sendMessageDelayed(msg, IDLE_DELAY);
    notifyPauseStop();
  }

  private void saveQueue() {
    if (playList == null || shuffleList == null) {
      return;
    }
    // Save playlist and shufflelist
    try {
      FileOutputStream fos = openFileOutput(SavedListFileName, Context.MODE_PRIVATE);
      ObjectOutputStream oos = new ObjectOutputStream(fos);
      oos.writeObject(playList);
      oos.writeObject(shuffleList);
      oos.close();
      fos.close();
    } catch (Exception e) {
      Log.e(LOGTAG, "unable to save queue");
      e.printStackTrace();
      return;
    }

    Log.d(LOGTAG, "saveQueue");
    // Save other parameters
    Editor ed = mPreferences.edit();
    ed.putInt("curpos", curPos);
    ed.putBoolean("shufflemode", playShuffle);
    ed.commit();
  }

  private void reloadQueue() {
    // Get playlist and shufflelist
    try {
      FileInputStream fis = openFileInput(SavedListFileName);
      ObjectInputStream ois = new ObjectInputStream(fis);
      playList = (String[]) ois.readObject();
      shuffleList = (int[]) ois.readObject();
    } catch (Exception e) {
      // It's ok if SavedListFileName does not exist
      // So don't print stacktrace here
      Log.d(LOGTAG, "unable to reload queue");
      playList = null;
      shuffleList = null;
      curPos = 0;
      playShuffle = false;
      return;
    }

    Log.d(LOGTAG, "reloadQueue");
    // Get other parameters
    playShuffle = mPreferences.getBoolean("shufflemode", false);

    if (playList != null) {
      int pos = mPreferences.getInt("curpos", 0);
      if (pos < 0 || pos >= playList.length) {
        // The saved playlist is bogus, discard it
        playList = null;
        shuffleList = null;
        return;
      }
      curPos = pos;
      open(curPos);
    }
  }

  private void saveCurPos() {
    Editor ed = mPreferences.edit();
    ed.putInt("curpos", curPos);
    ed.commit();
  }

  private void saveShuffleState() {
    Editor ed = mPreferences.edit();
    ed.putBoolean("shufflemode", playShuffle);
    ed.commit();
  }

  // Start foreground service and show the notification
  private void notifyPlaying() {
    Intent notificationIntent = new Intent(this, PsfPlaybackActivity.class);
    PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
    Notification notification = new Notification();
    notification.icon = R.drawable.notification_psfplaying;
    notification.contentIntent = contentIntent;
    notification.flags |= Notification.FLAG_FOREGROUND_SERVICE;

    RemoteViews contentView = new RemoteViews(getPackageName(), R.layout.notification);
    contentView.setImageViewResource(R.id.notification_image, R.drawable.notification_psfplaying);
    contentView.setTextViewText(R.id.notification_track, getTrackName());
    contentView.setTextViewText(R.id.notification_album, getAlbumName());

    // Set next button, seems only work after v11
    Intent nextTrackIntent = new Intent(this, PsfPlaybackService.class);
    nextTrackIntent.putExtra(CMDNAME, CMDNEXT);
    PendingIntent nextTrackPendingIntent = PendingIntent.getService(this, 0, nextTrackIntent, 0);
    contentView.setOnClickPendingIntent(R.id.notification_next_btn, nextTrackPendingIntent);

    notification.contentView = contentView;
    startForeground(PLAYBACKSERVICE_STATUS, notification);
  }

  // Stop foreground service and remove the notification
  private void notifyPauseStop() {
    stopForeground(true);
  }

  private void pauseAndKeepAudioFocus() {
    synchronized (this) {
      if (PsfPlayer != null) {
        Log.d(LOGTAG, "pause");
        PsfPlayer.Play(MineSexyPsfPlayer.PSFPAUSE);
        gotoIdleState();
        notifyChange(PLAYSTATE_CHANGED);
      }
    }
  }

  private final OnAudioFocusChangeListener AudioFocusChangeListener = new OnAudioFocusChangeListener() {
    public void onAudioFocusChange(int focusChange) {
      switch (focusChange) {
        case AudioManager.AUDIOFOCUS_GAIN:
          Log.d(LOGTAG, "Gain audio focus");
          // Resume
          play();
          // TODO: reset volume
          break;
        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
          Log.d(LOGTAG, "Loss transient audio focus");
          // Pause
          pauseAndKeepAudioFocus();
          break;
        case AudioManager.AUDIOFOCUS_LOSS:
          Log.d(LOGTAG, "Permanent loss of audio focus");
          // Stop
          pause();
          break;
        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
          Log.d(LOGTAG, "Duck loss audio focus");
          // TODO: lower volume
          break;
      }
    }
  };

  // Request audio focus
  private void requestAudioFocus() {
    // Request audio focus for playback
    int result = PsfAudioManager.requestAudioFocus(AudioFocusChangeListener,
        // Use the music stream.
        AudioManager.STREAM_MUSIC,
        // Request permanent focus.
        AudioManager.AUDIOFOCUS_GAIN);

    if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
      Log.d(LOGTAG, "Audio focus granted");
    } else {
      Log.w(LOGTAG, "Audio focus not granted: " + result);
    }
  }

  // Request audio focus
  private void cancelAudioFocus() {
    PsfAudioManager.abandonAudioFocus(AudioFocusChangeListener);
  }
}
