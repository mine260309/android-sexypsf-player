/*  Copyright (C) 2009 The android-sexypsf Open Source Project
 *
 *  This file is part of android-sexypsf.
 *  android-sexypsf is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  android-sexypsf is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with android-sexypsf.  If not, see <http://www.gnu.org/licenses/>.
 */

#ifndef _SEXYPSF_ANDROID_H_
#define _SEXYPSF_ANDROID_H_
/*==================================================================================================

     Header Name: sexypsf_android.h

     General Description: This file contains the related defines & global function declarations.

====================================================================================================

Revision History:
                            Modification
Author                          Date        Description of Changes
-------------------------   ------------    -------------------------------------------
Lei Yu                      08/30/2009	    Initial Creation, basic playback for psf file on Android platform

====================================================================================================
                                         INCLUDE FILES
==================================================================================================*/
#include "android/log.h"
#include "driver.h"
/*================================================================================================
                                         ENUMS
==================================================================================================*/
typedef enum {
	CMD_NONE = 0x00,
	CMD_SEEK = 0x01,
	CMD_STOP = 0x02
} PSF_CMD;

typedef enum {
	PSF_STATUS_IDLE,
	PSF_STATUS_STOPPED,
	PSF_STATUS_PLAYING,
	PSF_STATUS_PAUSE
} PSF_STATUS;

typedef enum {
    PSF_SEEK_SET,
    PSF_SEEK_CUR
} PSF_SEEK_MODE;

typedef enum {
	TYPE_PSF,
	TYPE_PSF2
} PSF_TYPE;

typedef struct {
	u32 length;
	char *title;
	char *artist;
	char *game;
	char *copyright;
} PSF_INFO;
/*================================================================================================
                                         DEFINES
==================================================================================================*/
#ifndef BOOL
typedef unsigned char BOOL;
#endif
#ifndef TRUE
#define TRUE  1
#endif
#ifndef FALSE
#define FALSE 0
#endif


//#define DEBUG_SHOW_TIME

#if (DEBUG_LEVEL!=0)
#define USE_DEBUG_PRINTF
#endif

#ifdef USE_DEBUG_PRINTF
	#if (DEBUG_LEVEL==2)
		#define debug_printf2 sexypsf_dbg_printf
	#else
		#define debug_printf2(...)
	#endif
	#define debug_printf sexypsf_dbg_printf
#else
	#define debug_printf(...)
	#define debug_printf2(...)
#endif

static inline void sexypsf_dbg_printf(char* fmt, ...)
{
    va_list arg;
    int done;

    va_start(arg,fmt);
    done = __android_log_vprint(ANDROID_LOG_INFO, "SEXYPSF",  fmt,   arg);
    va_end(arg);
}

#define handle_error() do{\
	__android_log_print(ANDROID_LOG_ERROR, "SEXYPSF", "Error occurs at line %d\n", __LINE__);\
}while(0)

/*==================================================================================================

FUNCTION: psf_open

DESCRIPTION: open a psf file

ARGUMENTS PASSED:
   file_name - file name string, NULL terminated
   type - the psf type, either PSF or PSF2

RETURN VALUE:
   TRUE if open successful, FALSE otherwise

DEPENDENCIES:
   The PSF_TYPE should be correct.

SIDE EFFECTS:
   This function will not return until the playback is done or psf_stop is called.

==================================================================================================*/
BOOL psf_open(const char* file_name, PSF_TYPE type);

/*==================================================================================================

FUNCTION: psf_play

DESCRIPTION: play the opened psf file

ARGUMENTS PASSED:
   file_name - file name string, NULL terminated

RETURN VALUE:
   None

DEPENDENCIES:
   None

SIDE EFFECTS:
   None

==================================================================================================*/
void psf_play();

/*==================================================================================================

FUNCTION: psf_stop

DESCRIPTION: stop the current playback

ARGUMENTS PASSED:
   None

RETURN VALUE:
   None

DEPENDENCIES:
   None

SIDE EFFECTS:
   None

==================================================================================================*/
void psf_stop();

/*==================================================================================================

FUNCTION: psf_pause

DESCRIPTION: pause or resume the current playback

ARGUMENTS PASSED:
   pause - TRUE,  pause the playback
         - FALSE, resume the playback

RETURN VALUE:
   None

DEPENDENCIES:
   None

SIDE EFFECTS:
   None

==================================================================================================*/
void psf_pause(BOOL pause);

/*==================================================================================================

FUNCTION: psf_seek

DESCRIPTION: seek the playback

ARGUMENTS PASSED:
   seek  -  seek position
            if it's positive, then seek forward;
            if it's negative, then seek backward

   mode  -  SEEK_SET, seek from the file's beginning
            SEEK_CUR, seek from the current playing position

RETURN VALUE:
   None

DEPENDENCIES:
   None

SIDE EFFECTS:
   None

Notes:
   PSF file playback only support seek forard,
   so if seek backward, it will seek from the beginning
==================================================================================================*/
void psf_seek(int seek, PSF_SEEK_MODE mode);

/*==================================================================================================

FUNCTION: psf_audio_putdata

DESCRIPTION: put the audio data to buffer

ARGUMENTS PASSED:
   stream - the pointer to the audio data buffer

   len    - the length of audio buffer

RETURN VALUE:
   the size actually put into the buffer

DEPENDENCIES:
   None

SIDE EFFECTS:
   None

Notes:
   If the return value does NOT equal to len, it indicates the current playback is end
==================================================================================================*/
int psf_audio_putdata(unsigned char *stream, int len);

/*==================================================================================================

FUNCTION: psf_get_pos

DESCRIPTION: get the current position, in seconds

ARGUMENTS PASSED:
   None

RETURN VALUE:
   the current position in seconds

DEPENDENCIES:
   None

SIDE EFFECTS:
   None

Notes:
   None
==================================================================================================*/
int psf_get_pos();

/*==================================================================================================

FUNCTION: sexypsf_quit

DESCRIPTION: quit sexypsf, release all resources

ARGUMENTS PASSED:
   None

RETURN VALUE:
   None

DEPENDENCIES:
   None

SIDE EFFECTS:
   None

Notes:
   None
==================================================================================================*/
void sexypsf_quit();


PSF_INFO* psf_getinfo(const char* filename);
void psf_freeinfo(PSF_INFO* info);
#endif /* _SEXYPSF_ANDROID_H_ */

