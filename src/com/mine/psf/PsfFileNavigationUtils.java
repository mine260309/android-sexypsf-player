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

import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.mine.psfplayer.R;

public class PsfFileNavigationUtils {
  private static final String LOGTAG = "PsfFileNavigationUtils";

  public interface PsfFileType {
    public static final int TYPE_DIR = 0;
    public static final int TYPE_PSF = 1;
    public static final int TYPE_PSF2 = 2;
    public static final int TYPE_ZIP = 3;
  }

  public static final int BROWSE_MODE_ALL = 0;
  public static final int BROWSE_MODE_DIR_ONLY = 1;
  public static final int BROWSE_MODE_PSF_ONLY = 2;

  public static final String ROOT_PATH = "/";
  private static final String DIR_PREFIX = "";
  private static final String PSF_CACHE_FLAG = "$";
//	private static final String DIR_PREFIX = "<dir> ";

  // Return the type from the name
  // The name is either a psf/psf2 file, or a dir
  public static int GetFileType(String name) {
    name = name.toLowerCase();
    if (name.endsWith(".psf") || name.endsWith(".minipsf")) {
      return PsfFileType.TYPE_PSF;
    } else if (name.endsWith(".psf2") || name.endsWith(".minipsf2")) {
      return PsfFileType.TYPE_PSF2;
    } else if (name.endsWith(".zip")) {
      return PsfFileType.TYPE_ZIP;
    } else {
      return PsfFileType.TYPE_DIR;
    }
  }

  public static boolean isMiniPsf(String psf) {
    psf = psf.toLowerCase();
    return psf.endsWith(".minipsf") || psf.endsWith(".minipsf2");
  }

  public static boolean isPsfLib(String psf) {
    psf = psf.toLowerCase();
    return psf.endsWith(".psflib") || psf.endsWith(".psf2lib");
  }

  public static class PsfListAdapter extends ArrayAdapter<String> {

    private int NumOfDirectory;
    private int NumOfPsfFiles;
    private String curDirName;
    private ArrayList<String> playList;
    private LayoutInflater inflater = null;

    public PsfListAdapter(Context context, int textViewResourceId) {
      super(context, textViewResourceId);
      inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      View vi = convertView;
      if (convertView == null) {
        vi = inflater.inflate(R.layout.browse_list_item, null);
      }
      String name = getItem(position);
      TextView text = (TextView) vi.findViewById(R.id.item_title);
      ;
      ImageView image = (ImageView) vi.findViewById(R.id.item_img);
      text.setText(name);
      switch (GetFileType(name)) {
        case PsfFileType.TYPE_DIR:
        case PsfFileType.TYPE_ZIP:
          image.setImageResource(R.drawable.ic_psf_folder);
          break;
        case PsfFileType.TYPE_PSF:
        case PsfFileType.TYPE_PSF2:
          image.setImageResource(R.drawable.ic_psf_file);
      }
      return vi;
    }

    public int getNumberPsfFiles() {
      return NumOfPsfFiles;
    }

    public int getNumberDirs() {
      return NumOfDirectory;
    }

    public String getCurDir() {
      return curDirName;
    }

    public ArrayList<String> getPlayList() {
      return playList;
    }

    // Return the Canonical Path
    public String getFilePath(int pos) {
      String fileName = TryRemovingPrefix(getItem(pos));
      String fullPath = GetFullPath(curDirName, fileName);
      File testDir = new File(fullPath);
      try {
        fullPath = testDir.getCanonicalPath();
      } catch (IOException e) {
        e.printStackTrace();
      }
      return fullPath;
    }
  }

  // Browse to a dir and return the list adapter\
  // browseMode:
  //    BROWSE_MODE_ALL - browse all directories and psf files, MEDIA_PATH is the root
  //    BROWSE_MODE_PSF_ONLY - browse only psf files, MEDIA_PATH is the root
  //    BROWSE_MODE_DIR_ONLY - browse only dir, '/' is the root
  public static PsfListAdapter browseToDir(Context context, String dir, int browseMode) {
    // If it's zip, just browse into zip
    if (GetFileType(dir) == PsfFileType.TYPE_ZIP) {
      return browseToZip(context, dir);
    }

    // Make Canonical path
    File curDir = new File(dir);
    PsfListAdapter listAdapter = new PsfListAdapter(context, R.layout.textview);

    try {
      listAdapter.curDirName = curDir.getCanonicalPath();
    } catch (IOException e) {
      Log.e(LOGTAG, "Unable to get canonical path!");
      return listAdapter;
    }

    listAdapter.NumOfDirectory = 0;
    listAdapter.NumOfPsfFiles = 0;

    boolean isOnPsfRoot = true;
    if (browseMode == BROWSE_MODE_DIR_ONLY) {
      isOnPsfRoot = (listAdapter.curDirName.equals(ROOT_PATH));
    } else {
      isOnPsfRoot = (listAdapter.curDirName.equals(
          PsfDirectoryChoosePreference.getPsfRootDir(context)));
    }

    listAdapter.clear();
    if (!isOnPsfRoot) {
      // Add ".." on the first item if it's not on the root
      listAdapter.add(GetDirNameWithPrefix(".."));
      listAdapter.NumOfDirectory++;
    }

    // Get dirs
    if (browseMode == BROWSE_MODE_ALL
        || browseMode == BROWSE_MODE_DIR_ONLY) {
      File[] subDirs = curDir.listFiles(PsfFileNavigationUtils.DirFilter);
      if (subDirs != null && subDirs.length > 0) {
        ArrayList<String> dirFiles = new ArrayList<String>(subDirs.length);

        for (File file : subDirs) {
          dirFiles.add(file.getName());
        }
        // Sort dirs and add into list
        Collections.sort(dirFiles, String.CASE_INSENSITIVE_ORDER);
        for (String dirs : dirFiles) {
          listAdapter.add(GetDirNameWithPrefix(dirs));
          listAdapter.NumOfDirectory++;
        }
      }
    }

    // Get zips
    if (browseMode == BROWSE_MODE_ALL) {
      String[] zips = curDir.list(PsfFileNavigationUtils.ZipFilter);
      if (zips != null && zips.length > 0) {
        listAdapter.playList = new ArrayList<String>(zips.length);
        // Sort psf files and add into list
        Arrays.sort(zips, String.CASE_INSENSITIVE_ORDER);
        for (String file : zips) {
          listAdapter.add(file);
          listAdapter.NumOfDirectory++;
        }
      }
    }

    // Get Files
    if (browseMode == BROWSE_MODE_ALL
        || browseMode == BROWSE_MODE_PSF_ONLY) {
      String[] filteredFiles = curDir.list(PsfFileNavigationUtils.PsfFilter);
      if (filteredFiles != null && filteredFiles.length > 0) {
        listAdapter.playList = new ArrayList<String>(filteredFiles.length);
        // Sort psf files and add into list
        Arrays.sort(filteredFiles, String.CASE_INSENSITIVE_ORDER);
        for (String file : filteredFiles) {
          listAdapter.add(file);
          listAdapter.playList.add(GetFullPath(listAdapter.curDirName, file));
          listAdapter.NumOfPsfFiles++;
        }
      } else {
        // No files, create an empty play list
        listAdapter.playList = new ArrayList<String>();
      }
    }
    return listAdapter;
  }

  private static PsfListAdapter browseToZip(Context context, String zipFileName) {
    PsfListAdapter listAdapter = new PsfListAdapter(context, R.layout.textview);
    listAdapter.NumOfDirectory = 0;
    listAdapter.NumOfPsfFiles = 0;
    listAdapter.curDirName = zipFileName;

    // Up dir
    listAdapter.add(GetDirNameWithPrefix(".."));
    listAdapter.NumOfDirectory++;

    // Files
    try {
      ZipFile zip = new ZipFile(zipFileName);
      Enumeration files = zip.entries();
      ArrayList<String> playList = new ArrayList<String>();
      while (files.hasMoreElements()) {
        ZipEntry file = (ZipEntry) files.nextElement();
        int type = GetFileType(file.getName());
        if (type == PsfFileType.TYPE_PSF
            || type == PsfFileType.TYPE_PSF2) {
          playList.add(file.getName());
        }
      }
      Collections.sort(playList, String.CASE_INSENSITIVE_ORDER);
      listAdapter.playList = new ArrayList<String>(playList.size());
      for (String file : playList) {
        listAdapter.add(file);
        listAdapter.playList.add(GenerateZipPath(zipFileName, file));
        listAdapter.NumOfPsfFiles++;
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    return listAdapter;
  }

  // Get the psf file path
  // If it's a regular file, return the path
  // if it's in a zip file, extract to temp dir and return the temp path
  public static String getPsfPath(Context context, String p) {
    int zipIndex = p.indexOf(PSF_CACHE_FLAG);
    if (zipIndex == -1) {
      return p;
    }
    String zip = p.substring(0, zipIndex);
    String psfName = p.substring(zipIndex + 1);
    return extractPsfZip(context, zip, psfName);
  }

  public static void cleanupPsfPath(Context context, String p) {
    int zipIndex = p.indexOf(PSF_CACHE_FLAG);
    if (zipIndex == -1) {
      // Regular file, no need to clean
      return;
    }
    // Delete the file in cache but keep the psflib
    String psfName = p.substring(zipIndex + 1);
    File cacheDir = context.getCacheDir();
    File psfFile = new File(cacheDir, psfName);
    if (psfFile.exists()) {
      Log.d(LOGTAG, "To delete cached " + psfFile);
      psfFile.delete();
    }
    else {
      Log.w(LOGTAG, psfFile + " does not exist?");
    }
  }

  // TODO: make it as LRU cache, e.g. with 1MB size
  private static String extractPsfZip(Context context, String zipName, String psfName) {
    String ret = "";
    try {
      // Extract psf file
      ZipFile zip = new ZipFile(zipName);
      ZipEntry entry = zip.getEntry(psfName);
      File cacheDir = context.getCacheDir();
      File psfFile = new File(cacheDir, psfName);
      ret = psfFile.getCanonicalPath();
      if (!psfFile.exists()) {
        Log.d(LOGTAG, "To extract " + psfFile);
        extractSingleFile(zip, entry, psfFile);
      }
      else {
        Log.d(LOGTAG, "Use cached " + psfFile);
      }

      boolean isMiniPsf = isMiniPsf(psfName);
      if (isMiniPsf) {
        extractPsfLibs(zip, cacheDir.getPath());
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    return ret;
  }

  private static void extractSingleFile(ZipFile zip, ZipEntry entry, File psfFile) {
    try {
      InputStream is = zip.getInputStream(entry);
      try {
        psfFile.getParentFile().mkdirs();
        FileOutputStream fos = new FileOutputStream(psfFile);
        byte[] buf = new byte[1024];
        int bytesRead;
        while ((bytesRead = is.read(buf)) > 0) {
          fos.write(buf, 0, bytesRead);
        }
        fos.close();
      } catch (IOException e) {
        e.printStackTrace();
        psfFile.delete();
      }
      is.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private static void extractPsfLibs(ZipFile zip, String dir) {
    Enumeration files = zip.entries();
    while (files.hasMoreElements()) {
      ZipEntry file = (ZipEntry) files.nextElement();
      if (isPsfLib(file.getName())) {
        File libFile = new File(dir, file.getName());
        if (!libFile.exists()) {
          Log.d(LOGTAG, "To extract " + libFile);
          extractSingleFile(zip, file, libFile);
        }
        else {
          Log.d(LOGTAG, "Use cached " + libFile);
        }
      }
    }
  }

  private static String GenerateZipPath(String zip, String file) {
    StringBuffer sb = new StringBuffer(256);
    sb.append(zip);
    sb.append(PSF_CACHE_FLAG);
    sb.append(file);
    return sb.toString();
  }

  private static String GetFullPath(String dir, String file) {
    StringBuffer sb = new StringBuffer();
    sb.append(dir);
    if (dir.endsWith("/")) {
      sb.append(file);
    } else {
      sb.append('/');
      sb.append(file);
    }
    return sb.toString();
  }

  private static String GetDirNameWithPrefix(String dir) {
    return DIR_PREFIX + dir;
  }

  private static String TryRemovingPrefix(String name) {
    if (name.startsWith(DIR_PREFIX)) {
      return name.substring(DIR_PREFIX.length());
    } else {
      return name;
    }
  }

  public static FilenameFilter PsfFilter = new FilenameFilter() {
    public boolean accept(File dir, String name) {
      name = name.toLowerCase();
      return (name.endsWith(".psf") || name.endsWith(".minipsf")
          || name.endsWith(".psf2") || name.endsWith(".minipsf2"));
    }
  };

  public static FileFilter DirFilter = new FileFilter() {
    public boolean accept(File file) {
      return file.isDirectory();
    }
  };

  public static FilenameFilter ZipFilter = new FilenameFilter() {
    public boolean accept(File dir, String name) {
      name = name.toLowerCase();
      if (name.endsWith(".zip")) {
        // Check if the zip contains psf files
        try {
          ZipFile zip = new ZipFile(dir + "/" + name);
          Enumeration files = zip.entries();
          while (files.hasMoreElements()) {
            ZipEntry file = (ZipEntry) files.nextElement();
            int type = GetFileType(file.getName());
            if (type == PsfFileType.TYPE_PSF
                || type == PsfFileType.TYPE_PSF2) {
              return true;
            }
          }
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
      return false;
    }
  };

}
