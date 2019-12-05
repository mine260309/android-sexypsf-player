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

package com.mine.psf;

import android.Manifest;
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
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.mine.psf.PsfFileNavigationUtils.PsfListAdapter;
import com.mine.psf.PsfUtils.ServiceToken;
import com.mine.psfplayer.R;

import java.io.File;
import java.util.ArrayList;

public class PsfFileBrowserActivity extends Activity
        implements ServiceConnection,
        SharedPreferences.OnSharedPreferenceChangeListener,
        ActivityCompat.OnRequestPermissionsResultCallback {
  private static final String LOGTAG = "PsfFileBrowserActivity";

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
  private int focusListPosition = -1;
  private int mStepsBack; // handle BACK key press, up one level dir instead of exit the activity

  private final Handler handler = new Handler();

  /**
   * Called when the activity is first created.
   */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    c = this;

    checkStoragePermission();
    mToken = PsfUtils.bindToService(this, this);

    mStepsBack = 0;
    setContentView(R.layout.psffile_browser_activity);
    // Prepare the music list
    MusicListView = (ListView) findViewById(R.id.psffilelist);
    CurDirView = (TextView) findViewById(R.id.directory_text);

    Intent intent = getIntent();
    if (intent.getAction() == Intent.ACTION_VIEW) {
      String fullPath = intent.getData().getPath();
      if (fullPath.endsWith(".zip") || fullPath.endsWith(".ZIP")) {
        // Open a zip file, just browse into it
        browseToDir(fullPath);
      } else {
        String fileName = intent.getData().getLastPathSegment();
        String path = fullPath.substring(0, fullPath.lastIndexOf(fileName));

        browseToDir(path);
        String[] list = (String[]) playList.toArray(new String[playList.size()]);
        int i;
        for (i = 0; i < list.length; ++i) {
          if (fullPath.equals(list[i])) {
            final String[] threadList = list;
            final int threadIndex = i;
            focusListPosition = i;
            final Runnable playRunnable = new Runnable() {
              @Override
              public void run() {
                if (!PsfUtils.isServiceConnected()) {
                  // Wait until the service is connected
                  handler.postDelayed(this, 50);
                  return;
                }
                PsfUtils.playAll(threadList, threadIndex);
                startActivity(new Intent(getApplicationContext(),
                        PsfPlaybackActivity.class));
              }
            };
            handler.postDelayed(playRunnable, 50);
            break;
          }
        }
        if (i == list.length) {
          Log.e(LOGTAG, "Failed to get the file: " + fileName);
        }
      }
    }
    else {
      handleFirstTimeRun();
      Bundle extras = intent.getExtras();
      if (extras != null
              && extras.containsKey(getString(R.string.extra_current_list_position))) {
        // If extras contains current list pos,
        // we should focus on the item after connected to the service
        focusListPosition = extras.getInt(
                getString(R.string.extra_current_list_position));
      } else {
        showFiles();
      }
    }

    MusicListView.setOnItemClickListener(new OnItemClickListener() {
      public void onItemClick(AdapterView<?> parent, View view,
                              int position, long id) {
        // When clicked, start playing
        String filePath = MusicListAdapter.getFilePath(position);
        int fileType = PsfFileNavigationUtils.GetFileType(filePath);

        if (fileType == PsfFileNavigationUtils.PsfFileType.TYPE_DIR
                || fileType == PsfFileNavigationUtils.PsfFileType.TYPE_ZIP) {
          Log.d(LOGTAG, "pick a directory: " + filePath);
          // Check the if it's go up or down level of dir
          String curDir = MusicListAdapter.getCurDir();
          if (filePath.compareTo(curDir) < 0) {
            // up one level
            mStepsBack--;
            //Log.d(LOGTAG, "up one level: " + mStepsBack);
          } else {
            // down one level
            mStepsBack++;
            //Log.d(LOGTAG, "down one level: " + mStepsBack);
          }
          browseToDir(filePath);
        } else {
          Log.d(LOGTAG, "pick a music: " + filePath + " at pos " + position);
          String[] list = (String[]) playList.toArray(new String[playList.size()]);
          // Play the file in the list
          PsfUtils.playAll(list, position - MusicListAdapter.getNumberDirs());
          //PsfUtils.play(view.getContext(), musicName);
          startActivity(new Intent(view.getContext(), PsfPlaybackActivity.class));
        }
      }
    });

    // Register listener on psf root dir change
    SharedPreferences prefs =
            PreferenceManager.getDefaultSharedPreferences(this);
    prefs.registerOnSharedPreferenceChangeListener(this);
  }

  @Override
  public void onPause() {
    super.onPause();
  }

  @Override
  public void onStop() {
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
    menu.add(0, ID_PLAY_ALL, 0, R.string.play_all).setIcon(R.drawable.ic_menu_play_clip);
    menu.add(0, ID_SHUFFLE_ALL, 0, R.string.shuffle_all).setIcon(R.drawable.ic_menu_shuffle);
    menu.add(0, ID_SETTINGS, 0, R.string.settings).setIcon(R.drawable.ic_settings);
    menu.add(0, ID_ABOUT, 0, R.string.about).setIcon(R.drawable.ic_about);
    menu.add(0, ID_EXIT, 0, R.string.menu_exit).setIcon(R.drawable.ic_menu_exit);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case ID_EXIT:
        ExitApp();
        return true;
      case ID_PLAY_ALL: {
        String[] list = (String[]) playList.toArray(new String[playList.size()]);
        if (list.length == 0) {
          showListEmptyError();
          return true;
        }
        PsfUtils.playAll(list, 0);
        startActivity(new Intent(this, PsfPlaybackActivity.class));
        return true;
      }
      case ID_SHUFFLE_ALL: {
        String[] list = (String[]) playList.toArray(new String[playList.size()]);
        if (list.length == 0) {
          showListEmptyError();
          return true;
        }
        PsfUtils.shuffleAll(list);
        startActivity(new Intent(this, PsfPlaybackActivity.class));
        return true;
      }
      case ID_ABOUT: {
        showDialog(ID_ABOUT);
        break;
      }
      case ID_SETTINGS: {
        showSettings();
        break;
      }
    }
    return super.onOptionsItemSelected(item);
  }

  @Override
  protected Dialog onCreateDialog(int id) {
    // show dialog according to the id
    final String msg;
    if (id == ID_ABOUT) {
      // Get version code
      String versionString = "";
      PackageManager manager = getPackageManager();
      try {
        PackageInfo info = manager.getPackageInfo(getPackageName(), 0);
        versionString = info.versionName;
      } catch (NameNotFoundException e) {
        e.printStackTrace();
      }
      // Format dialog msg
      msg = String.format(getString(R.string.sexypsf_about), versionString);
    } else {
      Log.e(LOGTAG, "Unknown dialog id");
      msg = "";
    }

    final TextView msgView = new TextView(this);
    msgView.setText(msg);
    msgView.setAutoLinkMask(Linkify.ALL);
    msgView.setTextAppearance(this, android.R.style.TextAppearance_Medium);
    msgView.setMovementMethod(LinkMovementMethod.getInstance());

    return new AlertDialog.Builder(this).setView(msgView)
            .setCancelable(true)
            .setPositiveButton(android.R.string.ok,
                    new DialogInterface.OnClickListener() {
                      public void onClick(DialogInterface dialog,
                                          int whichButton) {
                        /* User clicked OK so do some stuff */
                      }
                    }).create();
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {

    if (keyCode == KeyEvent.KEYCODE_BACK) {
      if (mStepsBack > 0) {
        upOneLevel();
        return true;
      }
    }

    return super.onKeyDown(keyCode, event);
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
    Toast.makeText(this, R.string.playlist_empty_error,
            Toast.LENGTH_LONG).show();
  }

  private void showStoragePermissionReason() {
    Toast.makeText(this, R.string.permission_storage_reason,
            Toast.LENGTH_SHORT).show();
  }

  @Override
  public void onServiceConnected(ComponentName name, IBinder service) {
    Log.d(LOGTAG, "onServiceConnected");
    PsfUtils.updateNowPlaying(this);
    IntentFilter f = new IntentFilter();
    f.addAction(PsfPlaybackService.META_CHANGED);
    registerReceiver(mTrackListener, new IntentFilter(f));

    if (focusListPosition != -1) {
      PsfPlaybackService psfService =
              ((PsfPlaybackService.ServiceBinder) service).getService();

      String[] playList = psfService.getPlaylist();
      if (playList != null && focusListPosition >= 0 && focusListPosition < playList.length) {
        String mediaFile = playList[focusListPosition];
        String parentDir = PsfFileNavigationUtils.getParentDir(mediaFile);
        if (parentDir != null) {
          //Log.d(LOGTAG, "Going to focus on " + mediaFile);
          browseToDir(parentDir, focusListPosition);
        } else {
          // Should never occur
          Log.e(LOGTAG, "Media path incorrect: " + mediaFile);
        }
      } else {
        // Occurs if the playlist is changed out of sudden
        Log.e(LOGTAG, "To focused item incorrect!");
      }
      focusListPosition = -1;
    }
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
      mStepsBack = 0;
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

  private void browseToDir(String dir, int focusPosition) {
    browseToDir(dir);
    focusPosition += MusicListAdapter.getNumberDirs();
    MusicListView.setSelection(focusPosition);
    //Log.d(LOGTAG, "Verify if selection is correct: "
    //		+ MusicListAdapter.getItem(focusPosition));
  }

  private void upOneLevel() {
    File curPath = new File(MusicListAdapter.getCurDir());
    String upLevel = curPath.getParent();
    mStepsBack--;
    if (upLevel != null) {
      browseToDir(upLevel);
    } else {
      // Should never occur
      Log.e(LOGTAG, "upOneLevel, parent null");
    }
  }

  private final int MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 1;

  private void checkStoragePermission() {
    if (ContextCompat.checkSelfPermission(this,
            Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
      if (ActivityCompat.shouldShowRequestPermissionRationale(this,
              Manifest.permission.READ_EXTERNAL_STORAGE)) {
        showStoragePermissionReason();
      }

      // No explanation needed, we can request the permission.
      ActivityCompat.requestPermissions(this,
              new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
              MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE);
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode,
                                         String permissions[], int[] grantResults) {
    switch (requestCode) {
      case MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE: {
        // If request is cancelled, the result arrays are empty.
        if (grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
          Log.i(LOGTAG, "Granted storage permission");
          showFiles();
        } else {
          showStoragePermissionReason();
        }
        return;
      }
    }
  }

  private void showFiles() {
    String mediaPath = PsfDirectoryChoosePreference.getPsfRootDir(c);
    Log.d(LOGTAG, "Media Path is: " + mediaPath);
    browseToDir(mediaPath);
  }

  private void handleFirstTimeRun() {
    // Check if the psf root dir is set
    // If not, probably it's the first time run,
    // so ask user to set the psf root dir
    SharedPreferences settings = PreferenceManager
            .getDefaultSharedPreferences(c);
    if (!settings.contains(getString(R.string.key_psf_root_dir))) {
      Log.d(LOGTAG, "First time run");
      new AlertDialog.Builder(c).setMessage(R.string.first_time_run_dialog_msg)
              .setPositiveButton(android.R.string.ok,
                      new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog,
                                            int whichButton) {
                          // Direct to the psf root dir setting
                          Intent intent = new Intent(c, PsfSettingsActivity.class);
                          intent.putExtra(
                                  getString(R.string.extra_direct_show_dir_dialog), true);
                          startActivity(intent);
                        }
                      }).create().show();
    }
  }
}
