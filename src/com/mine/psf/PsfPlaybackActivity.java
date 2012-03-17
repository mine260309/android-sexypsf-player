package com.mine.psf;

import com.mine.psf.PsfUtils.ServiceToken;
import com.mine.psfplayer.R;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.Window;
import android.view.View.OnLongClickListener;
import android.view.View.OnTouchListener;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class PsfPlaybackActivity extends Activity implements OnTouchListener,
		OnLongClickListener {
	private static final String LOGTAG = "PsfPlaybackActivity";
	
    private boolean mOneShot = false;
    private boolean mSeeking = false;
    private boolean mDeviceHasDpad;
    private long mStartSeekPos = 0;
    private long mLastSeekEventTime;
    private PsfPlaybackService mService = null;
    private RepeatingImageButton mPrevButton;
    private ImageButton mPauseButton;
    private RepeatingImageButton mNextButton;
    private ImageButton mRepeatButton;
    private ImageButton mShuffleButton;
    private ImageButton mQueueButton;
    private Toast mToast;
    private int mTouchSlop;
    private ServiceToken mToken;
    
    private ImageView mAlbum;
    private TextView mCurrentTime;
    private TextView mTotalTime;
    private TextView mArtistName;
    private TextView mAlbumName;
    private TextView mTrackName;
    private ProgressBar mProgress;
    private long mPosOverride = -1;
    private boolean mFromTouch = false;
    private long mDuration;
    private int seekmethod;
    private boolean paused;

    private static final int REFRESH = 1;
    private static final int QUIT = 2;
    private static final int GET_ALBUM_ART = 3;
    private static final int ALBUM_ART_DECODED = 4;

	@Override
	public boolean onTouch(View arg0, MotionEvent arg1) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean onLongClick(View v) {
		// TODO Auto-generated method stub
		return false;
	}

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.audio_player);
        
        mCurrentTime = (TextView) findViewById(R.id.currenttime);
        mTotalTime = (TextView) findViewById(R.id.totaltime);
        mProgress = (ProgressBar) findViewById(android.R.id.progress);
        mAlbum = (ImageView) findViewById(R.id.album);
        mArtistName = (TextView) findViewById(R.id.artistname);
        mAlbumName = (TextView) findViewById(R.id.albumname);
        mTrackName = (TextView) findViewById(R.id.trackname);

        View v = (View)mArtistName.getParent(); 
        v.setOnTouchListener(this);
        v.setOnLongClickListener(this);

        v = (View)mAlbumName.getParent();
        v.setOnTouchListener(this);
        v.setOnLongClickListener(this);

        v = (View)mTrackName.getParent();
        v.setOnTouchListener(this);
        v.setOnLongClickListener(this);
        
//        mPrevButton = (RepeatingImageButton) findViewById(R.id.prev);
//        mPrevButton.setOnClickListener(mPrevListener);
//        mPrevButton.setRepeatListener(mRewListener, 260);
        mPauseButton = (ImageButton) findViewById(R.id.pause);
        mPauseButton.requestFocus();
        mPauseButton.setOnClickListener(mPauseListener);
//        mNextButton = (RepeatingImageButton) findViewById(R.id.next);
//        mNextButton.setOnClickListener(mNextListener);
//        mNextButton.setRepeatListener(mFfwdListener, 260);
        seekmethod = 1;

        //mDeviceHasDpad = (getResources().getConfiguration().navigation ==
        //    Configuration.NAVIGATION_DPAD);
        
//        mQueueButton = (ImageButton) findViewById(R.id.curplaylist);
//        mQueueButton.setOnClickListener(mQueueListener);
//        mShuffleButton = ((ImageButton) findViewById(R.id.shuffle));
//        mShuffleButton.setOnClickListener(mShuffleListener);
//        mRepeatButton = ((ImageButton) findViewById(R.id.repeat));
//        mRepeatButton.setOnClickListener(mRepeatListener);
        
        if (mProgress instanceof SeekBar) {
            SeekBar seeker = (SeekBar) mProgress;
//            seeker.setOnSeekBarChangeListener(mSeekListener);
        }
        mProgress.setMax(1000);
        
        if (icicle != null) {
            mOneShot = icicle.getBoolean("oneshot");
        } else {
            mOneShot = getIntent().getBooleanExtra("oneshot", false);
        }

        mTouchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
    }
    
    private View.OnClickListener mPauseListener = new View.OnClickListener() {
        public void onClick(View v) {
            doPauseResume();
        }
    };

    private void doPauseResume() {
    	Log.d(LOGTAG, "doPauesResume");
    	if(mService != null) {
    		if (mService.isPlaying()) {
    	    	Log.d(LOGTAG, "call pause");
    			mService.pause();
    		} else {
    	    	Log.d(LOGTAG, "call play");
    			mService.play();
    		}
    	}
    }
    
    @Override
    public void onStop() {
        paused = true;
        if (mService != null && mOneShot && getChangingConfigurations() == 0) {
            mService.stop();
        }
        unregisterReceiver(mStatusListener);
        PsfUtils.unbindFromService(mToken);
        mService = null;
        super.onStop();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putBoolean("oneshot", mOneShot);
        super.onSaveInstanceState(outState);
    }
    
    @Override
    public void onStart() {
        super.onStart();
        paused = false;

        mToken = PsfUtils.bindToService(this, osc);
        if (mToken == null) {
            // something went wrong
        }
        
        IntentFilter f = new IntentFilter();
        f.addAction(PsfPlaybackService.PLAYSTATE_CHANGED);
        f.addAction(PsfPlaybackService.PLAYBACK_COMPLETE);
        registerReceiver(mStatusListener, new IntentFilter(f));
        // updateTrackInfo();
    }

    @Override
    public void onNewIntent(Intent intent) {
    	Log.d(LOGTAG, "onNewIntent");
        setIntent(intent);
        mOneShot = intent.getBooleanExtra("oneshot", false);
    }
    
    @Override
    public void onResume() {
        super.onResume();
    	Log.d(LOGTAG, "onResume");
        setPauseButtonImage();
    }
    
    @Override
    public void onDestroy()
    {
        super.onDestroy();
    	Log.d(LOGTAG, "onDestroy");
    }
 
    private BroadcastReceiver mStatusListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.v(LOGTAG, "mStatusListener receive: " + action);
            if (action.equals(PsfPlaybackService.PLAYBACK_COMPLETE)) {
                if (mOneShot) {
                    finish();
                } else {
                    setPauseButtonImage();
                }
            } else if (action.equals(PsfPlaybackService.PLAYSTATE_CHANGED)) {
                setPauseButtonImage();
            }
        }
    };

    private void setPauseButtonImage() {
    	Log.d(LOGTAG, "setPauseButtonImage");
    	if (mService != null && mService.isPlaying()) {
    		mPauseButton.setImageResource(android.R.drawable.ic_media_pause);
    	} else {
    		mPauseButton.setImageResource(android.R.drawable.ic_media_play);
    	}
    }
    
    private ServiceConnection osc = new ServiceConnection() {
        public void onServiceConnected(ComponentName classname, IBinder obj) {
            try {
                mService = ((PsfPlaybackService.ServiceBinder)obj).getService();
                // Assume something is playing when the service says it is,
                // but also if the audio ID is valid but the service is paused.
                if ( mService.isPlaying()) {
                	// TODO: set all other widgets
                    setPauseButtonImage();
                    return;
                }
            } catch (Exception ex) {
            	ex.printStackTrace();
            }
            // Service is dead or not playing anything. If we got here as part
            // of a "play this file" Intent, exit. Otherwise go to the Music
            // app start screen.
            if (getIntent().getData() == null) {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.setClass(PsfPlaybackActivity.this, PsfPlaybackActivity.class);
                startActivity(intent);
            }
            finish();
        }
        public void onServiceDisconnected(ComponentName classname) {
            mService = null;
        }
};
}
