/*************************************************************************
 * MinePsfPlayer is an Android App that plays psf and minipsf files.
 * Copyright (C) 2015  Lei YU
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

#include <dirent.h>
#include <stdio.h>
#include <unistd.h>

//extern void sexypsf_dbg_printf(char* fmt, ...);
#define sexypsf_dbg_printf(...)
extern char *GetFileWithBase(const char *f, const char *newfile);

static char* getPath(const char *file)
{
  char *ret;
  char *tp1;

  tp1 = ((char *)strrchr(file,'/'));
  if(!tp1) {
    ret = malloc(strlen(file));
    strcpy(ret, file);
  }
  else {
    ret=malloc(tp1 - file + 2);
    memcpy(ret, file, tp1 - file);
    ret[tp1-file]='/';
    ret[tp1-file+1]=0;
  }
  return ret;
}

/**
 Find the file in path ignoring case and return the full file path
 */
char* findFileIgnoreCase(const char* path, const char* file) {

  char* tmpfn = GetFileWithBase(path, file);

  if(access(tmpfn, F_OK) != -1 ) {
    return tmpfn;
  }
  // file does not exist, find it ignoring case
  sexypsf_dbg_printf("File %s does not exist!", tmpfn);

  DIR* d;
  struct dirent* dir;

  char* p = getPath(path);
  d = opendir(p);
  if (d) {
    sexypsf_dbg_printf("Opened dir %s", p);
    while ((dir = readdir(d)) != NULL) {
      sexypsf_dbg_printf("%s\n", dir->d_name);
      if (strcasecmp(dir->d_name, file) == 0) {
        // Find file!
        sexypsf_dbg_printf("Find similar file %s", dir->d_name);
        free(tmpfn);
        tmpfn = GetFileWithBase(path, dir->d_name);
        break;
      }
    }
    closedir(d);
  }
  else {
    sexypsf_dbg_printf("Failed to open dir %s", p);
  }
  free(p);
  return tmpfn;
}