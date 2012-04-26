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
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import com.mine.psf.PsfUtils.ServiceToken;
import com.mine.psfplayer.R;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

public class PsfFileBrowserActivity extends Activity
									implements ServiceConnection
{
	private static final String LOGTAG = "PsfFileBrowserActivity";
	private static final String MEDIA_PATH = new String(
			Environment.getExternalStorageDirectory()+"/psf/");
	private static final int ID_EXIT = 1;
	private static final int ID_PLAY_ALL = 2;
	private static final int ID_SHUFFLE_ALL = 3;
	
	private ListView MusicListView;
	private ArrayAdapter<String> MusicListAdapter;
    private ServiceToken mToken;
    private ArrayList<String> playList;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.psffile_browser_activity);
        //PlayerStatusView = new TextView(this);
        MusicListView = (ListView) findViewById(R.id.psffilelist);
        MusicListAdapter = new ArrayAdapter<String>(this, R.layout.textview);
        MusicListView.setAdapter(MusicListAdapter);
        Log.d(LOGTAG, "Media Path is: " + MEDIA_PATH);
        
        browseToDir(MEDIA_PATH);

		MusicListView.setOnItemClickListener(new OnItemClickListener() {
			    public void onItemClick(AdapterView<?> parent, View view,
			        int position, long id) {
			        // When clicked, start playing
			    	String musicName = ((TextView) view).getText().toString();
			    	File testDir = new File(musicName);
			    	if (testDir.isDirectory()) {
			    		Log.d(LOGTAG, "pick a directory: " + musicName);
			    		browseToDir(musicName);
			    	}
			    	else {
				    	Log.d(LOGTAG, "pick a music: " + musicName);
				        PsfUtils.play(view.getContext(), musicName);
				        startActivity(new Intent(view.getContext(), PsfPlaybackActivity.class));
				    }
			    }
			  });
		mToken = PsfUtils.bindToService(this, this);
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
    		PsfUtils.playAll(list, 0);
	        startActivity(new Intent(this, PsfPlaybackActivity.class));
	        return true;
    	}
    	case ID_SHUFFLE_ALL:
    	{
    		String[] list = (String[])playList.toArray(new String[playList.size()]);
    		PsfUtils.shuffleAll(list);
	        startActivity(new Intent(this, PsfPlaybackActivity.class));
	        return true;
    	}
    	}
        return super.onOptionsItemSelected(item);
    }
    
    public void ExitApp() {
    	PsfUtils.quit();
    	finish();
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

    private void browseToDir(String dir) {
    	MusicListAdapter.clear();
    	File curDir = new File(dir);
    	File[] subDirs = curDir.listFiles(DirFilter);
    	String[] filteredFiles = curDir.list(PsfFilter);
    	ArrayList<String> dirFiles = new ArrayList<String>(subDirs.length);
    	
    	playList = new ArrayList<String>(filteredFiles.length);
		if (subDirs!= null && subDirs.length > 0) {
    		for (File file : subDirs) {
    			//Log.d(LOGTAG, "Add dir to list: " + file);
    			dirFiles.add(file.getPath());
    		}
    		
		}

		// Sort dirFiles and psf files
		Collections.sort(dirFiles, String.CASE_INSENSITIVE_ORDER);
		Arrays.sort(filteredFiles, String.CASE_INSENSITIVE_ORDER);
		
		// Add dirs into list
		for (String dirs : dirFiles) {
			MusicListAdapter.add(dirs);
		}

		// Add psf files into list
    	for (String file : filteredFiles) {
    		//Log.d(LOGTAG, "Add file to list: " + file);
    		String fullPath;
    		if (dir.endsWith("/")) {
    			fullPath = dir+file;
    		}
    		else {
    			fullPath = dir+'/'+file;
    		}
    		MusicListAdapter.add(fullPath);
    		playList.add(fullPath);
    	}
    }

    FilenameFilter PsfFilter = new FilenameFilter() {
        public boolean accept(File dir, String name) {
            return (name.endsWith(".psf") || name.endsWith(".minipsf"));
        }
    };

    FileFilter DirFilter = new FileFilter() {
        public boolean accept(File file) {
            return file.isDirectory();
        }
    };
}
