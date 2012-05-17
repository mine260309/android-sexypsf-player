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

package com.mine.psf;

import java.io.File;
import java.util.ArrayList;

import com.mine.psf.PsfFileNavigationUtils.PsfListAdapter;
import com.mine.psf.PsfUtils.ServiceToken;
import com.mine.psfplayer.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Toast;

public class PsfFileBrowserActivity extends Activity
									implements ServiceConnection,
									SharedPreferences.OnSharedPreferenceChangeListener
{
	private static final String LOGTAG = "PsfFileBrowserActivity";

//	private static final String DIR_PREFIX = "<dir> ";

	private static final int ID_EXIT = 1;
	private static final int ID_PLAY_ALL = 2;
	private static final int ID_SHUFFLE_ALL = 3;
	private static final int ID_SETTINGS = 4;
	private static final int ID_ABOUT = 5;

	private Context c;
	private ListView MusicListView;
	private PsfListAdapter MusicListAdapter;
    private ServiceToken mToken;
    private ArrayList<String> playList;
    private TextView CurDirView;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        c = this;
        setContentView(R.layout.psffile_browser_activity);

        // Prepare the music list
        MusicListView = (ListView) findViewById(R.id.psffilelist);
        CurDirView = (TextView) findViewById(R.id.directory_text);
        String mediaPath = PsfDirectoryChoosePreference.getPsfRootDir(c);
        Log.d(LOGTAG, "Media Path is: " + mediaPath);
        
        browseToDir(mediaPath);
        
		MusicListView.setOnItemClickListener(new OnItemClickListener() {
			    public void onItemClick(AdapterView<?> parent, View view,
			        int position, long id) {
			        // When clicked, start playing
			    	String filePath = MusicListAdapter.getFilePath(position);
			    	File testDir = new File(filePath);
			    	if (testDir.isDirectory()) {
			    		Log.d(LOGTAG, "pick a directory: " + filePath);
			    		browseToDir(filePath);
			    	}
			    	else {
				    	Log.d(LOGTAG, "pick a music: " + filePath +" at pos " + position);
				    	String[] list = (String[])playList.toArray(new String[playList.size()]);
				    	// Play the file in the list
			    		PsfUtils.playAll(list, position-MusicListAdapter.getNumberDirs());
				        //PsfUtils.play(view.getContext(), musicName);
				        startActivity(new Intent(view.getContext(), PsfPlaybackActivity.class));
				    }
			    }
			  });
		mToken = PsfUtils.bindToService(this, this);
		
		// Register listener on psf root dir change
		SharedPreferences prefs = 
			    PreferenceManager.getDefaultSharedPreferences(this);
		prefs.registerOnSharedPreferenceChangeListener(this);

    }

    @Override
    public void onPause()
    {
    	super.onPause();
    }
    @Override
    public void onStop()
    {
    	super.onStop();
    }

    @Override
    public void onDestroy() {
        if (mToken != null) {
            PsfUtils.unbindFromService(mToken);
        }
        unregisterReceiverSafe(mTrackListener);
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, ID_EXIT, 0, R.string.menu_exit).setIcon(R.drawable.ic_menu_exit);
        menu.add(0, ID_PLAY_ALL, 0, R.string.play_all).setIcon(R.drawable.ic_menu_play_clip);
        menu.add(0, ID_SHUFFLE_ALL, 0, R.string.shuffle_all).setIcon(R.drawable.ic_menu_shuffle);
        menu.add(1, ID_SETTINGS, 0, R.string.settings).setIcon(R.drawable.ic_settings);
        menu.add(1, ID_ABOUT, 0, R.string.about).setIcon(R.drawable.ic_about);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	switch(item.getItemId())
    	{
    	case ID_EXIT:
    		ExitApp();
    		return true;
    	case ID_PLAY_ALL:
    	{
    		String[] list = (String[])playList.toArray(new String[playList.size()]);
    		if (list.length == 0) {
    			showListEmptyError();
    			return true;
    		}
    		PsfUtils.playAll(list, 0);
	        startActivity(new Intent(this, PsfPlaybackActivity.class));
	        return true;
    	}
    	case ID_SHUFFLE_ALL:
    	{
    		String[] list = (String[])playList.toArray(new String[playList.size()]);
    		if (list.length == 0) {
    			showListEmptyError();
    			return true;
    		}
    		PsfUtils.shuffleAll(list);
	        startActivity(new Intent(this, PsfPlaybackActivity.class));
	        return true;
    	}
    	case ID_ABOUT:
    	{
    		showDialog(ID_ABOUT);
    		break;
    	}
    	case ID_SETTINGS:
    	{
    		showSettings();
    		break;
    	}
    	}
        return super.onOptionsItemSelected(item);
    }

	@Override
	protected Dialog onCreateDialog(int id) {
		// show dialog according to the id
		String msg;
		if (id == ID_ABOUT) {
    		// Get version code
    		String versionString="";
    		PackageManager manager = getPackageManager();
    		try {
    			PackageInfo info = manager.getPackageInfo(getPackageName(),0);
    			versionString = info.versionName;
    		} catch (NameNotFoundException e) {
    			e.printStackTrace();
    		}
    		// Format dialog msg
    		msg = String.format(getString(R.string.sexypsf_about),versionString);
		}
		else {
			Log.e(LOGTAG, "Unknown dialog id");
			msg = "";
		}
		return new AlertDialog.Builder(this).setMessage(msg)
				.setPositiveButton(android.R.string.ok,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog,
									int whichButton) {
								/* User clicked OK so do some stuff */
							}
						}).create();
	}

    private void ExitApp() {
    	PsfUtils.quit();
    	finish();
    }

    private void showSettings() {
		Intent intent = new Intent(this, PsfSettingsActivity.class);
		startActivity(intent);
    }

    private void showListEmptyError() {
    	// Show a toast indicating play list empty
    	Toast.makeText(c, R.string.playlist_empty_error,
    			Toast.LENGTH_LONG).show();
    }

	@Override
	public void onServiceConnected(ComponentName name, IBinder service) {
		Log.d(LOGTAG, "onServiceConnected");
        PsfUtils.updateNowPlaying(this);
        IntentFilter f = new IntentFilter();
        f.addAction(PsfPlaybackService.META_CHANGED);
        registerReceiver(mTrackListener, new IntentFilter(f));
	}

	@Override
	public void onServiceDisconnected(ComponentName name) {
		Log.d(LOGTAG, "onServiceDisconnected");
		finish();
	}

    private BroadcastReceiver mTrackListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(PsfPlaybackService.META_CHANGED)) {
            	PsfUtils.updateNowPlaying(PsfFileBrowserActivity.this);
            }
        }
    };
    
    /**
     * Unregister a receiver, but eat the exception that is thrown if the
     * receiver was never registered to begin with. This is a little easier
     * than keeping track of whether the receivers have actually been
     * registered by the time onDestroy() is called.
     */
    private void unregisterReceiverSafe(BroadcastReceiver receiver) {
        try {
            unregisterReceiver(receiver);
        } catch (IllegalArgumentException e) {
            // ignore
        }
    }

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		if (key == getString(R.string.key_psf_root_dir)) {
			Log.d(LOGTAG, "psf root changed, refresh list...");
			browseToDir(PsfDirectoryChoosePreference.getPsfRootDir(c));
		}
	}

	private void browseToDir(String dir) {
        MusicListAdapter = PsfFileNavigationUtils.browseToDir(
        		c, dir, PsfFileNavigationUtils.BROWSE_MODE_ALL);
        MusicListView.setAdapter(MusicListAdapter);
        playList = MusicListAdapter.getPlayList();
        CurDirView.setText(MusicListAdapter.getCurDir());
	}
}
