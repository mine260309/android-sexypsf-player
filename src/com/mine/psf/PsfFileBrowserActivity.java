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

import com.mine.psfplayer.R;

import android.app.Activity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

public class PsfFileBrowserActivity extends Activity
{
	private static final String MEDIA_PATH = new String("/sdcard/psf/");
	private static final int ID_EXIT = 1;
	
	private TextView PlayerStatusView;
	private ListView MusicListView;
	private ArrayAdapter<String> MusicListAdapter;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        /** Create a TextView and set its content.
         * the text is retrieved by calling a native
         * function.
         */
        PlayerStatusView = new TextView(this);
        MusicListView = new ListView(this);
        MusicListAdapter = new ArrayAdapter<String>(this, R.layout.textview);
        MusicListView.setAdapter(MusicListAdapter);
    	File home = new File(MEDIA_PATH);
    	File[] filteredFiles = home.listFiles( new Mp3Filter());
		if (filteredFiles!= null && filteredFiles.length > 0) {
    		for (File file : home.listFiles( new Mp3Filter())) {
    			MusicListAdapter.add(file.getPath());
    		}
		}
		MusicListView.setOnItemClickListener(new OnItemClickListener() {
			    public void onItemClick(AdapterView<?> parent, View view,
			        int position, long id) {
			      // When clicked, start playing
			    	String musicName = ((TextView) view).getText().toString();
			    	System.out.println("pick a music: " + musicName);
			    	PlayerStatusView.append("pick a music: " + musicName);
			        
			        //setContentView(playerStatusView);
			    }
			  });
		setContentView(MusicListView);
    }

    @Override
    public void onPause()
    {
    	PlayerStatusView.append("\n pause application");
    	super.onPause();
    }
    @Override
    public void onStop()
    {
    	PlayerStatusView.append("\n stop application");
    	super.onStop();
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        boolean result = super.onCreateOptionsMenu(menu);
        menu.addSubMenu(0, ID_EXIT, 0, R.string.menu_exit);
        return result;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	switch(item.getItemId())
    	{
    	case ID_EXIT:
    		ExitApp();
    		return true;
    	}
        return super.onOptionsItemSelected(item);
    }
    
    public void ExitApp() {
    	PlayerStatusView.append("\n exit application");
    	PlayerStatusView.append("\nExit...");

    	finish();
    }
}

class Mp3Filter implements FilenameFilter {
    public boolean accept(File dir, String name) {
        return (name.endsWith(".psf") || name.endsWith(".minipsf"));
    }
}