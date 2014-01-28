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

package com.mine.psf.sexypsf;

import com.mine.psf.PsfFileNavigationUtils;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.AudioTrack.OnPlaybackPositionUpdateListener;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;

public class MineSexyPsfPlayer {
	
	public interface PsfPlayerState {
		public static final int STATE_IDLE = 0;
		public static final int STATE_PAUSED = 1;
		public static final int STATE_PLAYING = 2;
		public static final int STATE_STOPPED = 3;
		public static final int STATE_PENDING_PLAY = 4;
		public static final int STATE_OPENED = 5;
		public static final int STATE_MSG_MAX = 10;
	}
	public interface RepeatState {
		public static final int REPEAT_OFF = 0;
		public static final int REPEAT_ONE = 1;
		public static final int REPEAT_ALL = 2;
	}

	public static final int PSFPLAY=0;
	public static final int PSFPAUSE=1;
	
	private static final int MSG_REPEAT = 0;
	private static final String LOGTAG = "MinePsfPlayer";
	private static final int UNTIMED_TRACK_DURATION = 3*60*1000;
	private static final int MINE_AUDIO_BUFFER_TOTAL_LEN = 1024*256;
	private static final int MINE_AUDIO_BUFFER_PUT_GET_LEN = MINE_AUDIO_BUFFER_TOTAL_LEN/4;
	private String PsfFileName;
	private AudioTrack PsfAudioTrack = null;
	private MineAudioCircularBuffer CircularBuffer;
	private boolean threadShallExit;
	private PsfInfo PsfFileInfo;
	private boolean isAudioTrackOpened;
	private boolean isPsfUntimed; // some psf has no duration
	private int repeatState;
	private int PlayerState;
	private Handler mHandler;
	private int SampleDataSizePlayed;
	
	private PsfAudioGetThread GetThread;
	private PsfAudioPutThread PutThread;

	// TODO: Dump is for debugging, comment below code before release
//	private FileOutputStream DumpedFileWriteToHW;
//	private FileOutputStream DumpedFileReadFromNative;

	public MineSexyPsfPlayer() {
		CircularBuffer = new MineAudioCircularBuffer(MINE_AUDIO_BUFFER_TOTAL_LEN);
		setPsfState(PsfPlayerState.STATE_IDLE);
		repeatState = RepeatState.REPEAT_OFF;
//		PsfPlaybackEndSemaphore = new Semaphore(1);
	}

	public boolean Open(String psfFile) {
		boolean ret;
		PsfFileName = psfFile;
		setPsfState(PsfPlayerState.STATE_IDLE);
		// 1) open audio device;
		if (PsfAudioTrack == null) {
			PsfAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, 44100,
	        		AudioFormat.CHANNEL_CONFIGURATION_STEREO, 
	        		AudioFormat.ENCODING_PCM_16BIT,
	        		MINE_AUDIO_BUFFER_PUT_GET_LEN,
	        		AudioTrack.MODE_STREAM);
		}
		//Log.d(LOGTAG, "call AudioTrack.flush()");
		// PsfAudioTrack.flush(); flush() does not work for MODE_STREAM
		isAudioTrackOpened = false;
		isPsfUntimed = false;

		// 2) Open psf file
		ret = MineSexyPsfLib.sexypsfopen(psfFile,
				PsfFileNavigationUtils.GetFileType(psfFile));
		if (ret) {
			PsfFileInfo = MineSexyPsfLib.sexypsfgetpsfinfo(psfFile);
			//Log.d(LOGTAG, "Get psf info: " + PsfFileInfo.title +
			//		", duration: " + PsfFileInfo.duration);
			if (PsfFileInfo.duration <= 0) {
				// For untimed tracks, use default duration 
				isPsfUntimed = true;
				PsfFileInfo.duration = UNTIMED_TRACK_DURATION;
				Log.d(LOGTAG, "Untimed track");
			}
			if (PsfFileInfo.title.equals("")) {
				// For untitled tracks, use filename as title
				PsfFileInfo.title = psfFile.substring(psfFile.lastIndexOf('/')+1);
				Log.d(LOGTAG, "Untitled: " + PsfFileInfo.title);
			}
			setPsfState(PsfPlayerState.STATE_OPENED);
			// 3) Prepare get/put threads
			CircularBuffer.reInit();
			GetThread = new PsfAudioGetThread();
			PutThread = new PsfAudioPutThread();
	//		if (PsfPlaybackEndSemaphore.availablePermits() != 0) {
	//			PsfPlaybackEndSemaphore.acquireUninterruptibly();
	//			Log.d(LOGTAG, "Acquire PsfPlaybackEndSemaphore in Open");
	//		}
			// TODO: Dump is for debugging, comment below code before release 
	//        try {
	//			DumpedFileWriteToHW = new FileOutputStream(new File("/sdcard/psf", "dumped_file_write_to_hw"));
	//        	DumpedFileReadFromNative = new FileOutputStream(new File("/sdcard/psf", "dumped_file_read_from_native"));
	//		} catch (FileNotFoundException e) {
	//			e.printStackTrace();
	//			DumpedFileWriteToHW = DumpedFileReadFromNative = null;
	//		}
		}
		return ret;
	}

	public void Play(int playCmd) {
		if (getPsfState() == PsfPlayerState.STATE_IDLE) {
			// TODO: maybe I can add more check of the state
			// Currently only verify it's not in IDLE state
			return;
		}
		if (playCmd == PSFPLAY) {
			if (!isAudioTrackOpened) {
				Log.d(LOGTAG, "Will open AudioTrack and play");
				ClearPositionSampleDataSize();
				MineSexyPsfLib.sexypsfplay();
				setPsfState(PsfPlayerState.STATE_PENDING_PLAY);
				// Start playing after opened
				threadShallExit = false;
				PsfAudioTrack.setStereoVolume(1, 1);
				GetThread.start();
				PutThread.start();
				// Will switch to STATE_PLAYING in put thread
			}
			else {
				Log.d(LOGTAG, "Resume");
				// Play after pause
				PsfAudioTrack.play();
				MineSexyPsfLib.sexypsfpause(false);
				setPsfState(PsfPlayerState.STATE_PLAYING);
			}
		}
		else if (playCmd == PSFPAUSE){
			// Pause, only pause audio track, let psf lib running
			Log.d(LOGTAG, "Pause");
			PsfAudioTrack.pause();
			MineSexyPsfLib.sexypsfpause(true);
			setPsfState(PsfPlayerState.STATE_PAUSED);
		}
	}

	public void Stop() {
		if (getPsfState() == PsfPlayerState.STATE_IDLE) {
			return;
		}
		threadShallExit = true;
		setPsfState(PsfPlayerState.STATE_IDLE);
		Log.d(LOGTAG, "Stop");
		PsfAudioTrack.stop();
		isAudioTrackOpened = false;
		isPsfUntimed = false;
		MineSexyPsfLib.sexypsfstop();
		try {
			CircularBuffer.destroy();
			if (GetThread != null && GetThread.isAlive()) {
				GetThread.join();
			}
			if (PutThread != null && PutThread.isAlive()) {
				PutThread.join();
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		// TODO: Dump is for debugging, comment below code before release 
//		try {
//			if (DumpedFileWriteToHW != null) {
//				DumpedFileWriteToHW.close();
//			}
//			if (DumpedFileReadFromNative != null) {
//				DumpedFileReadFromNative.close();
//			}
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
	}

	public void ToggleRepeat() {
		// Currently only support REPEAT_OFF and REPEAT_ONE
		if (repeatState == RepeatState.REPEAT_OFF) {
			repeatState = RepeatState.REPEAT_ONE;
		}
		else if (repeatState == RepeatState.REPEAT_ONE) {
			repeatState = RepeatState.REPEAT_OFF;
		}
	}

	public int GetRepeatState() {
		return repeatState;
	}

	public boolean isPlaying() {
		return PlayerState == PsfPlayerState.STATE_PLAYING
			|| PlayerState == PsfPlayerState.STATE_PENDING_PLAY;
	}

	public boolean isActive() {
		return PlayerState != PsfPlayerState.STATE_IDLE;
	}

	public int GetPosition() {
		if (isActive()) {
			// The native position is the decoded position
			// which is typically 2~5s faster than the actual playback
			// So here I need to calculate the position by my self
			return GetPositionFromSampleDataSize();
			//return MineSexyPsfLib.sexypsfgetpos();
		}
		else {
			return 0;
		}
	}

	public int GetDuration() {
		if (isActive()) {
			return PsfFileInfo.duration / 1000;
		}
		else {
			return 0;
		}
	}

	public String GetArtist() {
		if (isActive()) {
			return PsfFileInfo.artist;
		}
		else {
			return "";
		}
	}

	public String GetAlbum() {
		if (isActive()) {
			return PsfFileInfo.game;
		}
		else {
			return "";
		}
	}
	
	public String GetTrack() {
		if (isActive()) {
			return PsfFileInfo.title;
		}
		else {
			return "";
		}
	}

	public void Quit() {
		MineSexyPsfLib.sexypsfquit();
	}

	// The thread that read data from psf lib
	private class PsfAudioGetThread extends Thread {
		public void run(){
			int ret;
			//int counter = 0;
			while(true){
	        	if (threadShallExit) {
	        		CircularBuffer.Discard();
	        		break;
	        	}
	        	MineAudioCircularBuffer.BufferChunk chunk = 
	        		CircularBuffer.GetWriteBufferPrepare(MINE_AUDIO_BUFFER_PUT_GET_LEN);
	        	
	        	ret = MineSexyPsfLib.sexypsfputaudiodataindex(chunk.buffer, chunk.index, chunk.len);
	        	CircularBuffer.GetWriteBufferDone(chunk.len);
	        	if (ret != chunk.len){
	        		// If the returned data is less than requested data
	        		// Either the playback is end, we shall let the play_thread exit and notify end
	        		// Or the playback is interrupted, we shall not set end flag
	        		// TODO: remove below log
	        		//Log.d(LOGTAG, "sexypsfputaudiodataindex return " + ret + ", check if play to end");
	        		// If the state is idle, it means Stop() is called
	        		if (getPsfState() != PsfPlayerState.STATE_IDLE) {
	        			CircularBuffer.setAudioBufferEnd();
	        		}
	        		break;
	        	}

				// TODO: Dump is for debugging, comment below code before release 
//				try {
//					if (DumpedFileReadFromNative != null) {
//						DumpedFileReadFromNative.write(chunk.buffer, chunk.index, chunk.len);
//					}
//				} catch (IOException e) {
//					e.printStackTrace();
//				}

				//Log.d(LOGTAG, "Put data to buffer: " + (counter++) + " len: " + ret);
	        }
			//Log.d(LOGTAG, "PsfAudioGetThread exit!");
		}
	}

	// The thread that write data to hw
	private class PsfAudioPutThread extends Thread {
		public void run() {
			//int counter = 0;
			while(!isInterrupted() && !CircularBuffer.getEndFlag())
			{
				if (threadShallExit) {
					CircularBuffer.Discard();
					break;
				}
				while(PlayerState != PsfPlayerState.STATE_PLAYING
						&& PlayerState != PsfPlayerState.STATE_PENDING_PLAY
						&& !threadShallExit) {
					// TODO: to pause the psf lib, should prevent getting data from it
					// so now I do a loop here until it's playing or exit
					// But this is really bad.
					// Find an alternative or use OpenGL Audio API in native code
					try {
						Thread.sleep(500);
					} catch (InterruptedException e) {
						CircularBuffer.Discard();
						break;
					}
				}
				try {
					MineAudioCircularBuffer.BufferChunk chunk =
						CircularBuffer.GetReadBufferPrepare(MINE_AUDIO_BUFFER_PUT_GET_LEN);

					// TODO: Dump is for debugging, comment below code before release 
//					try {
//						if (DumpedFileWriteToHW != null) {
//							DumpedFileWriteToHW.write(chunk.buffer, chunk.index, chunk.len);
//						}
//					} catch (IOException e) {
//						e.printStackTrace();
//					}

					SampleDataSizePlayed += chunk.len;
					if (!CircularBuffer.getEndFlag()) {
					    PsfAudioTrack.write(chunk.buffer, chunk.index, chunk.len);
						//Log.d(LOGTAG, "Written data to HW: "+(counter++) +" len: "+chunk.len);
					}
					CircularBuffer.GetReadBufferDone(chunk.len);

					if (repeatState != RepeatState.REPEAT_ONE) {
						// TODO: A better solution may come up, for now it's a hack.
						// Handle untimed track: check the position,
						// if it exceeds the duration, treat it as end
						if (isPsfUntimed) {
							if (GetPositionFromSampleDataSize() >=
									PsfFileInfo.duration / 1000)
							{
								Log.d(LOGTAG, "End of untimed track");
								CircularBuffer.setAudioBufferEnd();
								threadShallExit = true;
							}
						}
					}
					if (getPsfState() == PsfPlayerState.STATE_PENDING_PLAY) {
						//Log.d(LOGTAG, "call AudioTrack.play()");
						PsfAudioTrack.play();
						isAudioTrackOpened = true;
						setPsfState(PsfPlayerState.STATE_PLAYING);
					}
				} catch (InterruptedException e) {
					break;
				}
			}
			
			// Check buffer size and if it's end
			if (CircularBuffer.getEndFlag()) {
				try {
					int left = CircularBuffer.GetBufferAvailable();
					//Log.d(LOGTAG, "PsfAudioPutThread end of playback, data left: " + left);
					if (left > 0) {
						MineAudioCircularBuffer.BufferChunk chunk =
							CircularBuffer.GetReadBufferPrepare(left);
						//Log.d(LOGTAG, "PsfAudioPutThread write left data: " + chunk.len);
						PsfAudioTrack.write(chunk.buffer, chunk.index, chunk.len);
						SampleDataSizePlayed += chunk.len;
						CircularBuffer.GetReadBufferDone(chunk.len);

						// set end marker
						PsfAudioTrack.setNotificationMarkerPosition(chunk.len/2);
						PsfAudioTrack.setPlaybackPositionUpdateListener(new OnPlaybackPositionUpdateListener() {
				            @Override
				            public void onPeriodicNotification(AudioTrack track) {
				                // nothing to do
				            }
				            @Override
				            public void onMarkerReached(AudioTrack track) {
				                Log.d(LOGTAG, "Audio track end of file reached...");
//				                notifyPsfEnd();
				            }
				        });
//						waitPsfEnd();
					}
				} catch (InterruptedException e1) {}
				if (repeatState != RepeatState.REPEAT_ONE) {
					PsfAudioTrack.stop();
					PsfAudioTrack.setStereoVolume(0, 0);
					notifyStateChange(PsfPlayerState.STATE_STOPPED);
				}
				else {
					// Repeat the psf
					selfHandler.sendEmptyMessage(MSG_REPEAT);
				}
			}
			else {
				Log.d(LOGTAG, "Interrupted");
			}
			//Log.d(LOGTAG, "PsfAudioPutThread exit!");
		}
	}

	private int LastSampleDataSize = 0;
	private long LastPosTime = 0;
	private int LastPos = 0;
	private void ClearPositionSampleDataSize() {
		SampleDataSizePlayed = 0;
		LastSampleDataSize = 0;
		LastPosTime = 0;
		LastPos = 0;		
	}
	private int GetPositionFromSampleDataSize() {
		// Calculate position
		// Case 1: if SampleDataSizePlayed is not changed
		//         pos = last pos + time elapsed
		// Case 2: if it's changed
		// 	       pos = samplecount / samplerate
		//         where samplecount = datasize/4 (16bit per sample, 2 channels)
		//         and samplerate = 44100
		if (LastSampleDataSize != SampleDataSizePlayed) {
			LastSampleDataSize = SampleDataSizePlayed;
			LastPosTime = SystemClock.uptimeMillis();
			LastPos = SampleDataSizePlayed/4/44100;
			return LastPos;
		}
		else {
			if (isPlaying() && (LastPosTime != 0)) {
				return (int)((SystemClock.uptimeMillis()-LastPosTime)/1000
					+ LastPos);
			}
			else {
				return LastPos;
			}
		}
	}
	// The semaphore of psf playback's end
//	Semaphore PsfPlaybackEndSemaphore;
//	private void notifyPsfEnd() {
//		Log.d(LOGTAG, "Release PsfPlaybackEndSemaphore");
//		PsfPlaybackEndSemaphore.release();
//	}
//
//	private void waitPsfEnd() throws InterruptedException {
//		Log.d(LOGTAG, "wait PsfPlaybackEndSemaphore");
//		PsfPlaybackEndSemaphore.acquire();
//	}

    public void setHandler(Handler handler) {
        mHandler = handler;
    }
    
    private void notifyStateChange(int state) {
    	setPsfState(state);
    	mHandler.sendEmptyMessage(state);
    }
    private void setPsfState(int state) {
    	PlayerState = state;
    }
    private int getPsfState() {
    	return PlayerState;
    }
    
    private Handler selfHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
        	if (msg.what == MSG_REPEAT) {
        		Log.v(LOGTAG, "Auto repeat " + PsfFileName);
        		Stop();
        		if (!PsfFileName.equals("")) {
	        		Open(PsfFileName);
	        		Play(PSFPLAY);
        		}
        	}
        }
    };
}
