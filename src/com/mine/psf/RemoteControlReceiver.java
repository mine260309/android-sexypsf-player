package com.mine.psf;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.KeyEvent;

public class RemoteControlReceiver extends BroadcastReceiver {
    private static final String LOGTAG = "RemoteControlReceiver";


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
        	final Intent psfIntent = new Intent(context, PsfPlaybackService.class);
        	psfIntent.setAction(PsfPlaybackService.ACTION_CMD);
            if (KeyEvent.KEYCODE_MEDIA_PLAY == keyCode) {
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
            else if (KeyEvent.KEYCODE_HEADSETHOOK == keyCode) {
            	Log.d(LOGTAG, "Headerset hook key");
            	psfIntent.putExtra(PsfPlaybackService.CMDNAME, PsfPlaybackService.CMDTOGGLEPAUSE);
            }
            else if (KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE == keyCode) {
            	Log.d(LOGTAG, "Playpause key");
            	psfIntent.putExtra(PsfPlaybackService.CMDNAME, PsfPlaybackService.CMDTOGGLEPAUSE);
            }
            context.startService(psfIntent);
        }
	}
}
