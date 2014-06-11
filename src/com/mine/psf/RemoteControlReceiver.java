package com.mine.psf;

import java.lang.ref.WeakReference;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;

public class RemoteControlReceiver extends BroadcastReceiver {
    private static final String LOGTAG = "RemoteControlReceiver";
    private static final int MSG_ID_MULTIPLE_PRESS = 777;
    private static final long PRESS_INTERVAL = 500;
//    private static long lastPressTime;
    private static int clickCount = 0;

    private static class KeyDelayHandler extends Handler {
    	private final WeakReference<Context> mContext;
    	public KeyDelayHandler(Context context) {
    		mContext = new WeakReference<Context>(context);
    	}
    	@Override
    	public void handleMessage(Message msg) {
    		Context c = mContext.get();
    		if (c != null) {
            	final Intent psfIntent = GetPsfIntent(c);
                if (clickCount == 1) {
                	Log.d(LOGTAG, "Headset single");
                	psfIntent.putExtra(PsfPlaybackService.CMDNAME, PsfPlaybackService.CMDTOGGLEPAUSE);
                	c.startService(psfIntent);
                }
                else if (clickCount == 2) {
                	Log.d(LOGTAG, "Headset double");
                	psfIntent.putExtra(PsfPlaybackService.CMDNAME, PsfPlaybackService.CMDNEXT);
                	c.startService(psfIntent);
                }
                else if (clickCount == 3) {
            		Log.d(LOGTAG, "Headset triple");
                	psfIntent.putExtra(PsfPlaybackService.CMDNAME, PsfPlaybackService.CMDPREV);
                	c.startService(psfIntent);
                }
                else {
                	Log.d(LOGTAG, "Pressed " + clickCount);
                }
                clickCount = 0;
                //lastPressTime = 0;
            }
    		else {
    			Log.w(LOGTAG, "Context is null, ignore message");
    		}
    	}
    }

    private static Intent GetPsfIntent(Context c) {
    	Intent psfIntent = new Intent(c, PsfPlaybackService.class);
    	psfIntent.setAction(PsfPlaybackService.ACTION_CMD);
    	return psfIntent;
    }
 
    private static KeyDelayHandler mHandler;

    private void delayKey() {
        if (mHandler.hasMessages(MSG_ID_MULTIPLE_PRESS)) {
            mHandler.removeMessages(MSG_ID_MULTIPLE_PRESS);
        }
        mHandler.sendEmptyMessageDelayed(MSG_ID_MULTIPLE_PRESS, PRESS_INTERVAL);
    }

	@Override
	public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) {
        	KeyEvent event = (KeyEvent)intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
        	if (event.getAction() != KeyEvent.ACTION_UP) {
        		// Ignore, only handles key up event
        		return;
        	}
        	// Process keys
        	int keyCode = event.getKeyCode();
        	if (keyCode != KeyEvent.KEYCODE_MEDIA_PLAY
        			&& keyCode != KeyEvent.KEYCODE_MEDIA_PAUSE
        			&& keyCode != KeyEvent.KEYCODE_MEDIA_NEXT
        			&& keyCode != KeyEvent.KEYCODE_MEDIA_PREVIOUS
        			&& keyCode != KeyEvent.KEYCODE_HEADSETHOOK
        			&& keyCode != KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            	Log.d(LOGTAG, "Receive key: " + keyCode);
            	return;
        	}
        	final Intent psfIntent = GetPsfIntent(context);
            if (KeyEvent.KEYCODE_HEADSETHOOK == keyCode) {
            	//Log.d(LOGTAG, "Headerset hook key");
        		// Start a 500ms Timer and calculate the click count
        		// Single click: pause/play
        		// Double click: next
        		// Triple click: prev
            	// More clicks: ignore

            	if (mHandler == null) {
            		Log.v(LOGTAG, "Creating new handler");
            	    mHandler = new KeyDelayHandler(context);
            	}
//                long pressTime = System.currentTimeMillis();
//                String temp = String.format("Press time: %d, LastPressTime: %d, diff: %d",
//                		pressTime, lastPressTime, pressTime - lastPressTime);
//                Log.v(LOGTAG, temp);
            	clickCount++;
          		delayKey();
                // record the last time the menu button was pressed.
                //lastPressTime = pressTime;
            }
        	else if (KeyEvent.KEYCODE_MEDIA_PLAY == keyCode) {
                Log.d(LOGTAG, "Play key");
            	psfIntent.putExtra(PsfPlaybackService.CMDNAME, PsfPlaybackService.CMDPLAY);
            }
            else if (KeyEvent.KEYCODE_MEDIA_PAUSE == keyCode) {
                Log.d(LOGTAG, "Pause key");
            	psfIntent.putExtra(PsfPlaybackService.CMDNAME, PsfPlaybackService.CMDPAUSE);
            }
            else if (KeyEvent.KEYCODE_MEDIA_NEXT == keyCode) {
                Log.d(LOGTAG, "Next key");
            	psfIntent.putExtra(PsfPlaybackService.CMDNAME, PsfPlaybackService.CMDNEXT);
            }
            else if (KeyEvent.KEYCODE_MEDIA_PREVIOUS == keyCode) {
                Log.d(LOGTAG, "Previous key");
            	psfIntent.putExtra(PsfPlaybackService.CMDNAME, PsfPlaybackService.CMDPREV);
            }
            else if (KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE == keyCode) {
            	Log.d(LOGTAG, "Playpause key");
            	psfIntent.putExtra(PsfPlaybackService.CMDNAME, PsfPlaybackService.CMDTOGGLEPAUSE);
            }
            if(psfIntent.getStringExtra(PsfPlaybackService.CMDNAME) != null) {
                context.startService(psfIntent);
            }
        }
	}
}
