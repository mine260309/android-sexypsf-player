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
	public static native boolean sexypsfopen(String filename);

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
}
