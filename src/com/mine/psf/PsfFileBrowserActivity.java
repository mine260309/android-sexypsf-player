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
import java.io.FilenameFilter;
import java.util.ArrayList;

import com.mine.psf.PsfUtils.ServiceToken;
import com.mine.psfplayer.R;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
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
	private static final String MEDIA_PATH = new String("/sdcard/psf/");
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
    	File home = new File(MEDIA_PATH);
    	File[] filteredFiles = home.listFiles( new Mp3Filter());
    	playList = new ArrayList<String>();
		if (filteredFiles!= null && filteredFiles.length > 0) {
    		for (File file : home.listFiles( new Mp3Filter())) {
    			MusicListAdapter.add(file.getPath());
    			playList.add(file.getPath());
    		}
		}

		MusicListView.setOnItemClickListener(new OnItemClickListener() {
			    public void onItemClick(AdapterView<?> parent, View view,
			        int position, long id) {
			        // When clicked, start playing
			    	String musicName = ((TextView) view).getText().toString();
			    	Log.d(LOGTAG, "pick a music: " + musicName);

			        PsfUtils.play(view.getContext(), musicName);
			        startActivity(new Intent(view.getContext(), PsfPlaybackActivity.class));
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
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        boolean result = super.onCreateOptionsMenu(menu);
        menu.add(0, ID_EXIT, 0, R.string.menu_exit);
        menu.add(0, ID_PLAY_ALL, 0, R.string.play_all).setIcon(R.drawable.ic_menu_play_clip);
        menu.add(0, ID_SHUFFLE_ALL, 0, R.string.shuffle_all).setIcon(R.drawable.ic_menu_shuffle);
        return result;
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
    	PsfUtils.stop(this);
    	finish();
    }

	@Override
	public void onServiceConnected(ComponentName name, IBinder service) {
		Log.d(LOGTAG, "onServiceConnected");
        PsfUtils.updateNowPlaying(this);
	}

	@Override
	public void onServiceDisconnected(ComponentName name) {
		Log.d(LOGTAG, "onServiceDisconnected");
		finish();
	}
}

class Mp3Filter implements FilenameFilter {
    public boolean accept(File dir, String name) {
        return (name.endsWith(".psf") || name.endsWith(".minipsf"));
    }
}