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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

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
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

public class PsfFileBrowserActivity extends Activity
									implements ServiceConnection
{
	private static final String LOGTAG = "PsfFileBrowserActivity";
	private static final String MEDIA_PATH = new String(
			Environment.getExternalStorageDirectory()+"/psf");
//	private static final String DIR_PREFIX = "<dir> ";
	private static final String DIR_PREFIX = "";

	private static final int ID_EXIT = 1;
	private static final int ID_PLAY_ALL = 2;
	private static final int ID_SHUFFLE_ALL = 3;
	private static final int ID_SETTINGS = 4;
	private static final int ID_ABOUT = 5;
	
	private static final int TYPE_DIR = 0;
	private static final int TYPE_PSF = 1;
	private static final int TYPE_PSF2 = 2;  // psf2 is in future support
	
	private ListView MusicListView;
	private PsfListAdapter MusicListAdapter;
    private ServiceToken mToken;
    private ArrayList<String> playList;
    private int NumOfDirectory;
    private int NumOfPsfFiles;
    private TextView CurDirView;
    private String curDirName;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.psffile_browser_activity);
        //PlayerStatusView = new TextView(this);
        MusicListView = (ListView) findViewById(R.id.psffilelist);
        MusicListAdapter = new PsfListAdapter(this, R.layout.textview);
        MusicListView.setAdapter(MusicListAdapter);
        CurDirView = (TextView) findViewById(R.id.directory_text);
        Log.d(LOGTAG, "Media Path is: " + MEDIA_PATH);
        
        browseToDir(MEDIA_PATH);

		MusicListView.setOnItemClickListener(new OnItemClickListener() {
			    public void onItemClick(AdapterView<?> parent, View view,
			        int position, long id) {
			        // When clicked, start playing
			    	String fileName = TryRemovingPrefix( 
			    			((TextView) view.findViewById(R.id.item_title)).getText().toString());
			    	String musicName = GetFullPath(curDirName, fileName);
			    	File testDir = new File(musicName);
			    	if (testDir.isDirectory()) {
			    		Log.d(LOGTAG, "pick a directory: " + musicName);
			    		browseToDir(musicName);
			    	}
			    	else {
				    	Log.d(LOGTAG, "pick a music: " + musicName +" at pos " + position);
				    	String[] list = (String[])playList.toArray(new String[playList.size()]);
				    	// Play the file in the list
			    		PsfUtils.playAll(list, position-NumOfDirectory);
				        //PsfUtils.play(view.getContext(), musicName);
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
    	case ID_ABOUT:
    	{
    		showDialog(ID_ABOUT);
    		break;
    	}
    	case ID_SETTINGS:
    	{
    		// TODO
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
    	// Make Canonical path
    	File curDir = new File(dir);

    	try {
    		curDirName = curDir.getCanonicalPath();
		} catch (IOException e) {
			Log.e(LOGTAG, "Unable to get canonical path!");
			return;
		}

    	NumOfDirectory = 0;
    	NumOfPsfFiles = 0;
    	CurDirView.setText(curDirName);
    	boolean isOnPsfRoot = (curDirName.equals(MEDIA_PATH));

    	MusicListAdapter.clear();
    	if (!isOnPsfRoot) {
    		// Add ".." on the first item if it's not on the root
    		MusicListAdapter.add(GetDirNameWithPrefix(".."));
    		NumOfDirectory++;
    	}

    	File[] subDirs = curDir.listFiles(DirFilter);
    	String[] filteredFiles = curDir.list(PsfFilter);
    	
		if (subDirs!= null && subDirs.length > 0) {
	    	ArrayList<String> dirFiles = new ArrayList<String>(subDirs.length);

    		for (File file : subDirs) {
    			dirFiles.add(file.getName());
    		}
    		// Sort dirs and add into list
    		Collections.sort(dirFiles, String.CASE_INSENSITIVE_ORDER);
    		for (String dirs : dirFiles) {
    			MusicListAdapter.add(GetDirNameWithPrefix(dirs));
    			NumOfDirectory++;
    		}
		}

		if (filteredFiles != null && filteredFiles.length > 0) {
	    	playList = new ArrayList<String>(filteredFiles.length);
			// Sort psf files and add into list
			Arrays.sort(filteredFiles, String.CASE_INSENSITIVE_ORDER);
	    	for (String file : filteredFiles) {
	    		MusicListAdapter.add(file);
	    		playList.add(GetFullPath(curDirName, file));
	    		NumOfPsfFiles++;
	    	}
		}
		else {
			// No files, create an empty play list
			playList = new ArrayList<String>();
		}
    }

    private String GetFullPath(String dir, String file) {
    	StringBuffer sb = new StringBuffer();
		sb.append(dir);
    	if (dir.endsWith("/")) {
    		sb.append(file);
    	}
    	else {
    		sb.append('/');
    		sb.append(file);
    	}
    	return sb.toString();
    }
    
    private String GetDirNameWithPrefix(String dir) {
    	return DIR_PREFIX + dir;
    }
    private String TryRemovingPrefix(String name) {
    	if (name.startsWith(DIR_PREFIX)) {
    		return name.substring(DIR_PREFIX.length());
    	}
    	else {
    		return name;
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
    
    // Return the type from the name
    // The name is either a psf/psf2 file, or a dir
    private int GetFileType(String name) {
    	if (name.endsWith(".psf") || name.endsWith(".minipsf")) {
    		return TYPE_PSF;
    	} else if (name.endsWith(".psf2")) {
    		return TYPE_PSF2;
    	} else {
    		return TYPE_DIR;
    	}
    }

    private class PsfListAdapter extends ArrayAdapter<String> {
    	
        private LayoutInflater inflater=null;

		public PsfListAdapter(Context context, int textViewResourceId) {
			super(context, textViewResourceId);
			inflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		}
	
		@Override
		public View getView(int position, View convertView, ViewGroup parent){
	        View vi=convertView;
	        if(convertView==null) {
	            vi = inflater.inflate(R.layout.browse_list_item, null);
	        }
	        String name = getItem(position);
	        TextView text=(TextView)vi.findViewById(R.id.item_title);;
	        ImageView image=(ImageView)vi.findViewById(R.id.item_img);
	        text.setText(name);
	        switch (GetFileType(name)) {
	        case TYPE_DIR:
	        	image.setImageResource(R.drawable.ic_psf_folder);
	        	break;
	        case TYPE_PSF:
	        case TYPE_PSF2:
		        image.setImageResource(R.drawable.ic_psf_file);
	        }
	        return vi;
		}
    }
}
