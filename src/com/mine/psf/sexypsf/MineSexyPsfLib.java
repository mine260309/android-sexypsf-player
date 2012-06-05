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

public class MineSexyPsfLib {
	/** this is used to load the 'sexypsf' library on application
	 * startup.
	 */
	static {
		System.loadLibrary("sexypsf");
	}

	/** The native function implemented by sexypsf.
	 * It's used to open a psf file.
	 */
	public static native boolean sexypsfopen(String filename, int type);

	/** Native function to play a opened psf file */
	public static native void sexypsfplay();

	/** Native function to pause the playback */
	public static native void sexypsfpause(boolean pause);

	/** Native function to seek the playback */
	public static native void sexypsfseek(int seek, int mode);

	/** Native function to stop a the playback */
	public static native void sexypsfstop();

	/** Native function to get the audio data from sexypsf */
	public static native int sexypsfputaudiodata(byte[] arr, int size);

	/** Native function to get the audio data from sexypsf */
	public static native int sexypsfputaudiodataindex(byte[] arr, int index, int size);
	
	/** Native function to get the psf info from sexypsf */
	public static native PsfInfo sexypsfgetpsfinfo(String filename);
	
	/** Native function to get the current pos */
	public static native int sexypsfgetpos();
	
	/** Native function to release all the resources */
	public static native void sexypsfquit();
}

class PsfInfo {
	public int duration;
	public String title;
	public String artist;
	public String game;
	public String copyright;
	
	public PsfInfo(int duration, String title,
			String artist, String game, String copyright) {
		this.duration = duration;
		this.title = title;
		this.artist = artist;
		this.game = game;
		this.copyright = copyright;
		postInit();
	}
	// Verify and make sure no null String is returned
	private void postInit() {
		if (title == null) {
			title = "";
		}
		if (artist == null) {
			artist = "";
		}
		if (game == null) {
			game = "";
		}
		if (copyright == null) {
			copyright = "";
		}
	}
}