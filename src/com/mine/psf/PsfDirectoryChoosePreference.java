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

import com.mine.psf.PsfFileNavigationUtils.PsfListAdapter;
import com.mine.psfplayer.R;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.preference.DialogPreference;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

public class PsfDirectoryChoosePreference extends DialogPreference {
  private static final String LOGTAG = "DirChoosePref";
  private static final String DEFAULT_MEDIA_PATH =
      Environment.getExternalStorageDirectory().toString();

  private Context context;
  private PsfListAdapter listAdapter;
  private ListView dirListView;
  private TextView curDirView;

  private static String psfRootDir = null;

  public PsfDirectoryChoosePreference(Context context, AttributeSet attrs) {
    super(context, attrs);
    this.context = context;
    initDialog();
  }

  public PsfDirectoryChoosePreference(Context context, AttributeSet attrs,
                                      int defStyle) {
    super(context, attrs, defStyle);
    this.context = context;
    initDialog();
  }

  private void initDialog() {
    setDialogTitle(R.string.pref_psf_root_dir_dialog_title);
    setSummary(getPsfRootDir(context));
  }

  @Override
  protected View onCreateDialogView() {
    LayoutInflater li = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    View v = li.inflate(R.layout.psffolder_choose_layout, null, false);
    dirListView = (ListView) v.findViewById(R.id.psffolderlist);
    curDirView = (TextView) v.findViewById(R.id.psffolder_text);
    listAdapter = PsfFileNavigationUtils.browseToDir(
        context,
        getPsfRootDir(context),
        PsfFileNavigationUtils.BROWSE_MODE_DIR_ONLY);
    dirListView.setAdapter(listAdapter);
    curDirView.setText(listAdapter.getCurDir());

    dirListView.setOnItemClickListener(new OnItemClickListener() {
      public void onItemClick(AdapterView<?> parent, View view,
                              int position, long id) {
        // When clicked, start playing
        String filePath = listAdapter.getFilePath(position);

        Log.d(LOGTAG, "pick " + filePath);
        listAdapter = PsfFileNavigationUtils.browseToDir(
            context,
            filePath,
            PsfFileNavigationUtils.BROWSE_MODE_DIR_ONLY);
        dirListView.setAdapter(listAdapter);
        curDirView.setText(listAdapter.getCurDir());
      }
    });
    return v;
  }

  @Override
  protected void onDialogClosed(boolean positiveResult) {
    if (positiveResult) {
      psfRootDir = listAdapter.getCurDir();
      Log.d(LOGTAG, "save psf root dir: " + psfRootDir);
      savePsfRootDir(context, psfRootDir);
      // Change the summary
      setSummary(getPsfRootDir(context));
    } else {
      // Save default value if not existing
      SharedPreferences settings = PreferenceManager
          .getDefaultSharedPreferences(context);
      if (!settings.contains(context.getString(R.string.key_psf_root_dir))) {
        savePsfRootDir(context, DEFAULT_MEDIA_PATH);
      }
    }
  }

  public static String getPsfRootDir(Context context) {
    if (psfRootDir == null) {
      SharedPreferences settings = PreferenceManager
          .getDefaultSharedPreferences(context);
      psfRootDir = settings.getString(
          context.getString(R.string.key_psf_root_dir),
          DEFAULT_MEDIA_PATH);
    }
    return psfRootDir;
  }

  private static void savePsfRootDir(Context context, String dir) {
    // Save psf root
    SharedPreferences settings = PreferenceManager
        .getDefaultSharedPreferences(context);
    SharedPreferences.Editor editor = settings.edit();
    editor.putString(
        context.getString(R.string.key_psf_root_dir),
        psfRootDir);
    editor.commit();
  }
}
