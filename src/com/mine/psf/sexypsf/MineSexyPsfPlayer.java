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

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

public class MineSexyPsfPlayer {
	public static final int PSFPLAY=0;
	public static final int PSFPAUSE=1;
	
	private static final String LOGTAG = "MinePsfPlayer";
	
	private static final int MINE_AUDIO_BUFFER_TOTAL_LEN = 1024*256;
	private static final int MINE_AUDIO_BUFFER_PUT_GET_LEN = MINE_AUDIO_BUFFER_TOTAL_LEN/4;
	private AudioTrack PsfAudiotrack;
	private MineAudioCircularBuffer CircularBuffer;
	private boolean threadShallExit;
	private String PsfFileName;
	private boolean isPlaying;
	
	private PsfAudioGetThread GetThread;
	private PsfAudioPutThread PutThread;

	public MineSexyPsfPlayer() {
		CircularBuffer = new MineAudioCircularBuffer(MINE_AUDIO_BUFFER_TOTAL_LEN);
	}

	public void Open(String psfFile) {
		// 1) open audio device;
		PsfAudiotrack = new AudioTrack(AudioManager.STREAM_MUSIC, 44100,
        		AudioFormat.CHANNEL_CONFIGURATION_STEREO, 
        		AudioFormat.ENCODING_PCM_16BIT,
        		MINE_AUDIO_BUFFER_PUT_GET_LEN,
        		AudioTrack.MODE_STREAM);
		
		// 2) Open psf file
		PsfFileName = psfFile;
		MineSexyPsfLib.sexypsfopen(psfFile);
		// Let sexypsf play so we can have audio buffer now
		MineSexyPsfLib.sexypsfplay();
		isPlaying = false;
		
		// 3) Prepare get/put threads
		GetThread = new PsfAudioGetThread();
		PutThread = new PsfAudioPutThread();
	}
	
	public void Play(int playCmd) {
		if (playCmd == PSFPLAY) {
			if (!isPlaying) {
				// Start playing after opened
				threadShallExit = false;
				PsfAudiotrack.play();
				PsfAudiotrack.setStereoVolume(1, 1);
				GetThread.start();
				PutThread.start();
			}
			else {
				// Play after pause
				PsfAudiotrack.play();
			}
		}
		else if (playCmd == PSFPAUSE){
			// Pause, only pause audio track, let psf lib running
			PsfAudiotrack.pause();
		}
	}

	public void Stop() {
		threadShallExit = true;
		PsfAudiotrack.stop();
		MineSexyPsfLib.sexypsfstop();
		try {
			GetThread.join();
			PutThread.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	// The thread that read data from psf lib
	private class PsfAudioGetThread extends Thread {
		public void run(){
			int ret;
			while(true){
	        	if (threadShallExit) {
	        		CircularBuffer.Discard();
	        		break;
	        	}
	        	MineAudioCircularBuffer.BufferChunk chunk = 
	        		CircularBuffer.GetWriteBuffer(MINE_AUDIO_BUFFER_PUT_GET_LEN);
	        	
	        	ret = MineSexyPsfLib.sexypsfputaudiodataindex(chunk.buffer, chunk.index, chunk.len);
	        	if (ret != chunk.len){
	        		// the playback is end, we shall let the play_thread exit
	        		// and then let itself exit
	        		Log.d(LOGTAG, "sexypsfputaudiodataindex return " + ret + ", play to end");
	        		CircularBuffer.setAudioBufferEnd();
	        		PutThread.interrupt();
	        		break;
	        	}
	        	Log.d(LOGTAG, "Put audio data to buffer: " + ret);
	        }
			// TODO: should I call audiotrack's stop here?
			PsfAudiotrack.stop();
			Log.d(LOGTAG, "PsfAudioGetThread exit!");
		}
	}
	
	// The thread that write data to hw
	private class PsfAudioPutThread extends Thread {
		public void run() {
			int counter = 0;
			while(!isInterrupted())
			{
				if (threadShallExit) {
					CircularBuffer.Discard();
					break;
				}
				try {
					//len = m_audio_buffer.get(audioData, 0, DATA_LEN);
					MineAudioCircularBuffer.BufferChunk chunk =
						CircularBuffer.GetReadBuffer(MINE_AUDIO_BUFFER_PUT_GET_LEN);
					Log.d(LOGTAG, "Write data to HW: "+(counter++) +" len: "+chunk.len);
					PsfAudiotrack.write(chunk.buffer, chunk.index, chunk.len);
				} catch (InterruptedException e) {
					// No more audio data, exit the thread
					break;
				}
			}
			Log.d(LOGTAG, "PlayThread exit!");
		}
	}
}
