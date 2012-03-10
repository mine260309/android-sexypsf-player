/*  sexyPSF - PSF1 player
 *  Copyright (C) 2002-2004 xodnizel
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

/*
 * This file is copied from xmms.c, it will be the interface with Android
 * App. Currently the whole file is a dummy code to pass build */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include "driver.h"

#define CMD_SEEK	0x80000000
#define CMD_STOP	0x40000000

#define uint32 u32
#define int16 short

static volatile uint32 command;
static volatile int playing=0;

static PSFINFO *PSFInfo=NULL;

void init(void)
{
}
void about(void)
{

}
void config(void)
{

}

int testfile(char *fn)
{
 char buf[4];
 char *tmps;
 static const char *teststr="psflib";
 FILE *fp;
 if(!strncasecmp(fn,"http://",7)) return(0); // Don't handle http://blahblah

 /* Filter out psflib files */
 if(strlen(teststr) < strlen(fn))
 {
  tmps=fn+strlen(fn);
  tmps-=strlen(teststr);
  if(!strcasecmp(tmps,teststr))
   return(0);
 }

 if(!(fp=fopen(fn,"rb"))) return(0);
 if(fread(buf,1,4,fp)!=4) {fclose(fp);return(0);}
 fclose(fp);
 if(memcmp(buf,"PSF\x01",4))	// Only allow for PSF1 files for now.
   return 0;
 return(1);
}

static char *MakeTitle(PSFINFO *info)
{
 char *ret;

 ret=malloc( (info->game?strlen(info->game):0) + (info->title?strlen(info->title):0) + 3 + 1);
 sprintf(ret,"%s - %s",info->game?info->game:"",info->title?info->title:"");

 return(ret);
}

//static pthread_t dethread;
void sexyd_update(unsigned char *Buffer, long count)
{

}
static volatile int nextsong=0;

static void *playloop(void *arg)
{

}

static int paused;
static void play(char *fn)
{

}

static void stop(void)
{

}

static void sexy_pause(short p)
{

}

static void seek(int time)
{

}

static int gettime(void)
{

}

static void getsonginfo(char *fn, char **title, int *length)
{
  PSFINFO *tmp;

  if((tmp=sexy_getpsfinfo(fn)))
  {
   *length=tmp->length;
   *title=MakeTitle(tmp);
   sexy_freepsfinfo(tmp);
  }
}

