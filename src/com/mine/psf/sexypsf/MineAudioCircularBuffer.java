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

import java.util.concurrent.Semaphore;

//import android.util.Log;

public class MineAudioCircularBuffer {
//	private static String LOGTAG = "MineCircularBuffer";
    private byte[] m_buffer;
    private int m_len;
    private int put_index, get_index;
    private Semaphore put, get;
    private boolean m_EndFlag;

    public class BufferChunk {
    	public byte[] buffer;
    	public int index;
    	public int len;
    }
    private BufferChunk writeBufferChunk;
    private BufferChunk readBufferChunk;

    public MineAudioCircularBuffer(int len){
    	init(len);
    }
    
    public void Discard() {
    	int avail = get.availablePermits();
    	try {
			get.acquire(avail);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
    	put.release(avail);
    }
    
    public void init(int len) {
    	m_buffer = new byte[len];
    	m_len = len;
    	put_index = 0; get_index = 0;
    	put = new Semaphore(m_len);
    	get = new Semaphore(0);
    	m_EndFlag = false;
    	
    	writeBufferChunk = new BufferChunk();
    	readBufferChunk = new BufferChunk();
    	writeBufferChunk.buffer = readBufferChunk.buffer = m_buffer;
    }
    // Discard the buffer and release all the Semaphore
    public void destroy() {
    	Discard();
    	put.release(m_len);
    	get.release(m_len);
    }
    

    public boolean getEndFlag() {return m_EndFlag;}
    public void setAudioBufferEnd()
    {
    	m_EndFlag = true;
    }
 
    public BufferChunk GetWriteBuffer(int requiredWriteLength) {
    	int avail = put.availablePermits();
    	int put_len = (avail>requiredWriteLength) ? (requiredWriteLength) : (avail);
    	if (put_len == 0) {
    		// if no available buffer, let's require
    		put_len = requiredWriteLength;
    	}
    	if (put_len + put_index > m_len) {
    	    // reach the end, set put_len to the end
    		put_len = m_len - put_index;
    	}
//    	Log.v(LOGTAG, "GetWriteBuffer: req " + requiredWriteLength +
//    			", avail " + avail + ", put_index " + put_index +", put_len " + put_len);
    	try {
			put.acquire(put_len);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
    	writeBufferChunk.index = put_index;
    	writeBufferChunk.len = put_len;
    	put_index += put_len;
//    	Log.d(LOGTAG, "Move put_index with "+put_len);
    	if (put_index >= m_len) {
    		put_index = 0;
//    		Log.d(LOGTAG, "Move put_index back to 0");
    	}
    	get.release(put_len);
    	return writeBufferChunk;
    }
    
    public BufferChunk GetReadBuffer(int requiredReadLength)
    		throws InterruptedException {
    	int get_len;
    	int avail = get.availablePermits();
    	if(avail != 0) {
    		get_len = avail>requiredReadLength?requiredReadLength:avail;
    	}
    	else {
    		get_len = requiredReadLength;
    	}
    	if (get_len + get_index > m_len) {
    		// reach the end, set get_len to the end
    		get_len = m_len - get_index;
    	}
//    	Log.v(LOGTAG, "GetReadBuffer: req " + requiredReadLength +
//    			", avail " + avail + ", get_index " + get_index +", gett_len " + get_len);

    	try {
			get.acquire(get_len);
		} catch (InterruptedException e) {
			// if it's interrupted, it means there's no more audio data
			throw e;
		}
    	readBufferChunk.index = get_index;
    	readBufferChunk.len = get_len;
    	get_index += get_len;
//    	Log.v(LOGTAG, "Move get_index with "+get_len);
    	if (get_index >= m_len) {
    		get_index = 0;
//    		Log.v(LOGTAG, "Move get_index back to 0");
    	}
    	put.release(get_len);
    	return readBufferChunk;
    }
    
    public int GetBufferAvailable() {
    	return get.availablePermits();
    }
}
