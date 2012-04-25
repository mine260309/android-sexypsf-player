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

package com.mine.psf.sexypsf;

//import java.io.File;
//import java.io.FileNotFoundException;
//import java.io.FileOutputStream;
//import java.io.IOException;
//import java.util.concurrent.Semaphore;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.AudioTrack.OnPlaybackPositionUpdateListener;
import android.os.Handler;
import android.util.Log;

public class MineSexyPsfPlayer {
	
	public interface PsfPlayerState {
		public static final int STATE_IDLE = 0;
		public static final int STATE_PAUSED = 1;
		public static final int STATE_PLAYING = 2;
		public static final int STATE_STOPPED = 3;
		public static final int STATE_PENDING_PLAY = 4;
		public static final int STATE_OPENED = 5;
	}

	public static final int PSFPLAY=0;
	public static final int PSFPAUSE=1;
	
	private static final String LOGTAG = "MinePsfPlayer";
	
	private static final int MINE_AUDIO_BUFFER_TOTAL_LEN = 1024*256;
	private static final int MINE_AUDIO_BUFFER_PUT_GET_LEN = MINE_AUDIO_BUFFER_TOTAL_LEN/4;
	private AudioTrack PsfAudioTrack = null;
	private MineAudioCircularBuffer CircularBuffer;
	private boolean threadShallExit;
	private String PsfFileName;
	private PsfInfo PsfFileInfo;
	private boolean isAudioTrackOpened;
	private int PlayerState;
	private Handler mHandler;
	
	private PsfAudioGetThread GetThread;
	private PsfAudioPutThread PutThread;

	// TODO: Dump is for debugging, comment below code before release
//	private FileOutputStream DumpedFileWriteToHW;
//	private FileOutputStream DumpedFileReadFromNative;

	public MineSexyPsfPlayer() {
		CircularBuffer = new MineAudioCircularBuffer(MINE_AUDIO_BUFFER_TOTAL_LEN);
		setPsfState(PsfPlayerState.STATE_IDLE);
//		PsfPlaybackEndSemaphore = new Semaphore(1);
	}

	public void Open(String psfFile) {
		// 1) open audio device;
		if (PsfAudioTrack == null) {
			PsfAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, 44100,
	        		AudioFormat.CHANNEL_CONFIGURATION_STEREO, 
	        		AudioFormat.ENCODING_PCM_16BIT,
	        		MINE_AUDIO_BUFFER_PUT_GET_LEN,
	        		AudioTrack.MODE_STREAM);
		}
		Log.d(LOGTAG, "call AudioTrack.flush()");
		PsfAudioTrack.flush();

		// 2) Open psf file
		PsfFileName = psfFile;
		MineSexyPsfLib.sexypsfopen(psfFile);
		PsfFileInfo = MineSexyPsfLib.sexypsfgetpsfinfo(psfFile);
		//Log.d(LOGTAG, "Get psf info: " + PsfFileInfo.title +
		//		", duration: " + PsfFileInfo.duration);
		
		isAudioTrackOpened = false;
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

	public void Play(int playCmd) {
		if (playCmd == PSFPLAY) {
			if (!isAudioTrackOpened) {
				Log.d(LOGTAG, "Will open AudioTrack and play");
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
		threadShallExit = true;
		setPsfState(PsfPlayerState.STATE_IDLE);
		Log.d(LOGTAG, "In Stop() AudioTrack.stop()");
		PsfAudioTrack.stop();
		isAudioTrackOpened = false;
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
	
	public boolean isPlaying() {
		return PlayerState == PsfPlayerState.STATE_PLAYING
			|| PlayerState == PsfPlayerState.STATE_PENDING_PLAY;
	}

	public boolean isActive() {
		return PlayerState != PsfPlayerState.STATE_IDLE;
	}
	
	public int GetPosition() {
		if (isActive()) {
			return MineSexyPsfLib.sexypsfgetpos();
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
			int counter = 0;
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
	        		// the playback is end, we shall let the play_thread exit
	        		// and then let itself exit
	        		Log.d(LOGTAG, "sexypsfputaudiodataindex return " + ret + ", play to end");
	        		CircularBuffer.setAudioBufferEnd();
	        		// TODO: should I really need to interrupt? PutThread.interrupt();
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

				Log.d(LOGTAG, "Put data to buffer: " + (counter++) + " len: " + ret);
	        }
			Log.d(LOGTAG, "PsfAudioGetThread exit!");
		}
	}

	// The thread that write data to hw
	private class PsfAudioPutThread extends Thread {
		public void run() {
			int counter = 0;
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
						Thread.sleep(1);
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

					PsfAudioTrack.write(chunk.buffer, chunk.index, chunk.len);
					CircularBuffer.GetReadBufferDone(chunk.len);
					Log.d(LOGTAG, "Written data to HW: "+(counter++) +" len: "+chunk.len);

					if (getPsfState() == PsfPlayerState.STATE_PENDING_PLAY) {
						Log.d(LOGTAG, "call AudioTrack.play()");
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
					Log.d(LOGTAG, "PsfAudioPutThread end of playback, data left: " + left);
					if (left > 0) {
						MineAudioCircularBuffer.BufferChunk chunk =
							CircularBuffer.GetReadBufferPrepare(left);
						Log.d(LOGTAG, "PsfAudioPutThread write left data: " + chunk.len);
						PsfAudioTrack.write(chunk.buffer, chunk.index, chunk.len);
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
				// TODO: should I call audiotrack's stop here?
				Log.d(LOGTAG, "call AudioTrack.stop()");
				PsfAudioTrack.stop();
				PsfAudioTrack.setStereoVolume(0, 0);
				notifyStateChange(PsfPlayerState.STATE_STOPPED);
			}
			else {
				Log.e(LOGTAG, "Interrupted for unknown reason!");
			}
			Log.d(LOGTAG, "PsfAudioPutThread exit!");
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
}
