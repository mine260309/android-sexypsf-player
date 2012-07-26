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

/*==================================================================================================

     Header Name: sexypsf_android.c

     General Description: This file contains the functions to play the psf file.

====================================================================================================
Revision History:
                            Modification
Author                          Date        Description of Changes
-------------------------   ------------    -------------------------------------------
Lei Yu                      08/30/2009	    Initial Creation, basic playback for psf file on Android platform
Lei Yu                      03/25/2012	    Code clean up, remove SDL related code
====================================================================================================
                                         INCLUDE FILES
==================================================================================================*/

#include <pthread.h>

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#define DEBUG_LEVEL 1
//#define DEBUG_DUMP_PCM

#include "sexypsf_android.h"
#include "ao.h"
#include "corlett.h"
#include "eng_protos.h"

#ifdef DEBUG_DUMP_PCM
  // dump the decoded audio data
  static FILE* dump_file;
  static FILE* dump_file2;
#endif
/*==================================================================================================
                                     GLOBAL FUNCTIONS
==================================================================================================*/
char *GetFileWithBase(char *f, char *newfile);

int my_sexy_get_cur_time();
int my_sexy_seek(int32_t t);

// psf2
int my_psf2_get_cur_time();

/*==================================================================================================
                                     LOCAL CONSTANTS
==================================================================================================*/
#define AUDIO_BLOCK_BUFFER_SIZE (1024*512)

/*==================================================================================================
                                      LOCAL VARIABLES
==================================================================================================*/
static PSFINFO *PSFInfo=NULL;                                   //the psf info structure defined in driver.h
static const char* stored_filename = NULL;                            //the stored file name

volatile PSF_CMD    global_command;             //the global command
volatile PSF_STATUS global_psf_status;          //the global status
int                 global_seektime = 0;        //the global seek time
PSF_TYPE			global_psf_type = -1;
volatile bool_t     psf2_stop_flag = FALSE;

static int mutex_initialized = FALSE;
static pthread_mutex_t audio_buf_mutex;
static pthread_t play_thread;                      //the play back thread
static int thread_running;

static PSF_INFO* PSF2Info = NULL;
static void* psf2_buffer = NULL;
static uint32 psf2_size = 0;

typedef enum
{
  SEXY_BUFFER_EMPTY,
  SEXY_BUFFER_FULL
} SEXY_BUFFER_STATE;

// The lock-free fifo buffer, which requires that only one reader and one writer
struct fifo_buf {
  unsigned int in;
  unsigned int out;
  unsigned int size;
  uint8_t _buf[AUDIO_BLOCK_BUFFER_SIZE]; // the size must be power of 2
};

struct fifo_buf playing_audio_buf;

/*==================================================================================================
                                     LOCAL FUNCTION PROTOTYPES
==================================================================================================*/
static void sexypsf_clear_audio_buffer();
static int  put_audio_buf(void* buf, int len);
static int  get_audio_buf(void* buf_ptr, int wanted_len);
static void *playloop(void *arg);
void        sexyd_update(unsigned char *Buffer, long count);
static int  sexypsf_bufferstatus();

// PSF2 related glue functions
PSF_INFO* psf2_load(const char *filename, void** buffer, uint32* size);
void psf2_update(unsigned char *buffer, long count, InputPlayback *playback);
static void *psf2_playloop(void *arg);
static void psf2_cleanup();

/*==================================================================================================
                                     LOCAL FUNCTIONS
==================================================================================================*/
inline static unsigned int min(a, b)
{
  return a <= b ? a : b;
}

void fifo_init(struct fifo_buf* buf)
{
  buf->in = 0;
  buf->out = 0;
  buf->size = sizeof(buf->_buf);
}

unsigned int fifo_freesize(struct fifo_buf* fifo)
{
  return fifo->size - (fifo->in - fifo->out);
}

unsigned int fifo_put(struct fifo_buf* fifo,
       const uint8_t *buffer, unsigned int len)
{
  unsigned int l;
  len = min(len, fifo->size - fifo->in + fifo->out);
  /* first put the data starting from fifo->in to buffer end */
  l = min(len, fifo->size - (fifo->in & (fifo->size - 1)));
  memcpy(fifo->_buf + (fifo->in & (fifo->size - 1)), buffer, l);
  /* then put the rest (if any) at the beginning of the buffer */
  memcpy(fifo->_buf, buffer + l, len - l);
  fifo->in += len;
  return len;
}

unsigned int fifo_get(struct fifo_buf* fifo,
     uint8_t *buffer, unsigned int len)
{
  unsigned int l;
  len = min(len, fifo->in - fifo->out);
  if (buffer != NULL) {
    /* first get the data from fifo->out until the end of the buffer */
    l = min(len, fifo->size - (fifo->out & (fifo->size - 1)));
    memcpy(buffer, fifo->_buf + (fifo->out & (fifo->size - 1)), l);
    /* then get the rest (if any) from the beginning of the buffer */
    memcpy(buffer + l, fifo->_buf, len - l);
  }
  fifo->out += len;
  return len;
}

#ifdef DEBUG_SHOW_TIME
/*==================================================================================================

FUNCTION: sexypsf_show_time

DESCRIPTION: show the playing time to stdout

ARGUMENTS PASSED:
   interval     - the current interval
   opaque       - pointer to some data, here it's not used

RETURN VALUE:
   the next interval

DEPENDENCIES:
   None

SIDE EFFECTS:
   None

Notes:
   This function is called by SDL Timer lib, the return interval is the next interval of this timer.

==================================================================================================*/
static uint32_t sexypsf_show_time(uint32_t interval, void *opaque)
{
    int cur_time; /* second */
    int hour, minute, second;
    static int first_show = 1;

    cur_time = my_sexy_get_cur_time();
    second = cur_time%60;
    minute = (cur_time/60) % 60;
    hour = (cur_time/3600);
#if 1
    if(first_show)
    {
//        fprintf(stdout, "\n%02d:%02d:%02d", hour, minute, second);
//        fflush(stdout);
        printf("\n%02d:%02d:%02d", hour, minute, second);
        first_show = 0;
    }
    else
    {
//        fprintf(stdout, "%c%c%c%c%c%c%c%c%02d:%02d:%02d",8,8,8,8,8,8,8,8,hour, minute, second);
//        fflush(stdout);
        printf("%c%c%c%c%c%c%c%c%02d:%02d:%02d",8,8,8,8,8,8,8,8,hour, minute, second);
    }
#endif
//    printf("\n%02d:%02d:%02d", hour, minute, second);
    return interval; /* remain the interval */
}
#endif

/*==================================================================================================

FUNCTION: put_audio_buf

DESCRIPTION: put audio data into the buffe

ARGUMENTS PASSED:
   buf          - the pointer of audio data to stored into the buffer
   len          - the length of the audio data

RETURN VALUE:
   the lenght of audio data actually stored into the buffer

DEPENDENCIES:
   None

SIDE EFFECTS:
   None

Notes:
   This function may be blocked if the buffer is used by another thread.

==================================================================================================*/
static int put_audio_buf(void* buf, int len)
{
    int actual_put_len;
    debug_printf2("%s: buf %08X, len %d", __FUNCTION__, buf, len);

    actual_put_len = fifo_put(&playing_audio_buf, buf, len);

    debug_printf2("%s return with %d\n", __FUNCTION__, actual_put_len);
    return actual_put_len;
}

/*==================================================================================================

FUNCTION: get_audio_buf

DESCRIPTION: get audio data from buffer

ARGUMENTS PASSED:
   buf_ptr      - the buffer to store the audio data
   wanted_len   - the max length of the buffer

RETURN VALUE:
   the lenght of buffer actually stored

DEPENDENCIES:
   None

SIDE EFFECTS:
   None

Notes:
   This function may be blocked if the buffer is used by another thread.

==================================================================================================*/
static int get_audio_buf(void* buf_ptr, int wanted_len)
{
    debug_printf2("%s: buf %08X, len %d", __FUNCTION__, buf_ptr, wanted_len);
    return fifo_get(&playing_audio_buf, buf_ptr, wanted_len);
}

/*==================================================================================================

FUNCTION: playloop

DESCRIPTION: the playback thread

ARGUMENTS PASSED:
   arg      - the pointer to parameters when the thread is created

RETURN VALUE:
   NULL

DEPENDENCIES:
   None

SIDE EFFECTS:
   None

Notes:
   This function is run as a thread, it calls sexy_execute to let the psx engine.

==================================================================================================*/
static void *playloop(void *arg)
{
    debug_printf("%s: in playloop\n", __FUNCTION__);
    global_psf_status = PSF_STATUS_PLAYING;
dofunky:
    sexy_execute();

    while(1)
    {
        if(CMD_STOP == global_command)
        {
            break;
        }
        else if(CMD_SEEK == global_command)
        {
            debug_printf("seek backward, re-open file, seektime: %d\n", global_seektime/44100);
        	if (PSFInfo!= NULL) {
        		sexy_freepsfinfo(PSFInfo);
        		PSFInfo = NULL;
        	}
            if(!(PSFInfo=sexy_load(stored_filename)))
            {
                handle_error();
            }
            sexy_seek(global_seektime);
            global_command = CMD_NONE;
            goto dofunky;
        }
        else
        {//the song is end, wait for audio buffer to be played and exit
            if(sexypsf_bufferstatus() == SEXY_BUFFER_EMPTY)
                break;
        }
        usleep(100000);
    }

    debug_printf("playloop exit\n");

    global_psf_status = PSF_STATUS_STOPPED;
    pthread_exit(0);
    return NULL;
}

/*==================================================================================================

FUNCTION: sexypsf_init

DESCRIPTION: initialize the variables for psf file playback
             This function shall be called when opening the file

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
void sexypsf_init()
{
	if (!mutex_initialized) {
		if (pthread_mutex_init(&audio_buf_mutex, NULL) != 0) {
			handle_error();
		}
		mutex_initialized = TRUE;
	}

	sexypsf_clear_audio_buffer();
	global_seektime = 0;
	global_command = CMD_NONE;
    global_psf_status = PSF_STATUS_IDLE;
	global_psf_type = -1;
    thread_running = 0;
}
/*==================================================================================================

FUNCTION: sexypsf_clear_audio_buffer

DESCRIPTION: clear the local audio buffer

ARGUMENTS PASSED:
   None

RETURN VALUE:
   None

DEPENDENCIES:
   None

SIDE EFFECTS:
   None

==================================================================================================*/
static void sexypsf_clear_audio_buffer()
{
//  global_clear_buf_flag = 1;
    fifo_init(&playing_audio_buf);
}


/*==================================================================================================
                                     GLOBAL FUNCTIONS
==================================================================================================*/

/*==================================================================================================

FUNCTION: psf_open

DESCRIPTION: open a psf file

ARGUMENTS PASSED:
   file_name - file name string, NULL terminated

RETURN VALUE:
   None

DEPENDENCIES:
   None

SIDE EFFECTS:
   None

==================================================================================================*/
BOOL psf_open(const char* file_name, PSF_TYPE type)
{
#ifdef DEBUG_DUMP_PCM
    char* dump_file_name = (char*)malloc(strlen(file_name)+10);
    if (dump_file_name != NULL) {
    	strcpy(dump_file_name, file_name);
    	strcat(dump_file_name, ".dmp2");
        dump_file = fopen(dump_file_name, "wb");
        strcat(dump_file_name, ".o");
        dump_file2 = fopen(dump_file_name, "wb");
        if (dump_file && dump_file2) {
            debug_printf("Opened dump file %s\n", dump_file_name);
        }
        else {
            debug_printf("Open dump file failure %s\n", dump_file_name);
        }
        free(dump_file_name);
    }
#endif
	if (type == TYPE_PSF) {
		if (PSFInfo!= NULL) {
			sexy_freepsfinfo(PSFInfo);
			PSFInfo = NULL;
		}
		if(!(PSFInfo=sexy_load((char*)file_name)))
		{
		    debug_printf("%s: open file %s fail!!\n", __FUNCTION__, file_name);
		    handle_error();
		    return FALSE;
		}
	}
	else if (type == TYPE_PSF2) {
		if(!(PSF2Info=psf2_load(file_name, &psf2_buffer, &psf2_size)))
		{
		    debug_printf("%s: open file %s fail!!\n", __FUNCTION__, file_name);
		    handle_error();
		    return FALSE;
		}
		if (psf2_start(psf2_buffer, psf2_size) != AO_SUCCESS)
		{
			handle_error();
			psf2_cleanup();
			return FALSE;
		}
        psf2_stop_flag = FALSE;
	}
	else {
		debug_printf("%s: unknown psf type\n", __FUNCTION__);
		return FALSE;
	}
	stored_filename = file_name;
	sexypsf_init();
	global_psf_type = type;
    debug_printf("%s: open file %s success.\n", __FUNCTION__, file_name);
    return TRUE;
}


void psf_play()
{
	int state = -1;
    debug_printf("%s: playing file %s...\n", __FUNCTION__, stored_filename);
	if (global_psf_type == TYPE_PSF) {
		state = pthread_create(&play_thread,0,playloop,0);
	}
	else if (global_psf_type == TYPE_PSF2) {
		// TODO: psf2
		state = pthread_create(&play_thread,0,psf2_playloop,0);
	}
    if (state == 0 ){
    	thread_running = 1;
    }
    else {
    	thread_running = 0;
    }
}

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
void psf_stop()
{
#ifdef DEBUG_DUMP_PCM
    if (dump_file) {
        debug_printf("Closing dump file\n");
    	fclose(dump_file);
    }
    if (dump_file2) {
    	fclose(dump_file2);
    }
    dump_file = NULL;
    dump_file2 = NULL;
#endif
    debug_printf("%s\n", __FUNCTION__);
    global_command = CMD_STOP;
    if (thread_running) {
    	// consume audio data so that the thread gets a chance to exit
    	get_audio_buf(NULL, AUDIO_BLOCK_BUFFER_SIZE);
    	debug_printf("joining player thread...");
    	pthread_join(play_thread,0);
    	thread_running = 0;
    }
	// TODO: check how to stop psf2
}

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
void psf_pause(BOOL pause)
{
    debug_printf("%s: pause %d\n", __FUNCTION__, pause);
//printf("current status: playing?:%d pause?:%d", global_psf_status, pause);
    if((global_psf_status == PSF_STATUS_PLAYING) && pause)
    {
//printf("pause audio\n");
        global_psf_status = PSF_STATUS_PAUSE;
    }
    else if((global_psf_status == PSF_STATUS_PAUSE) && !pause)
    {
//printf("resume audio\n");
        global_psf_status = PSF_STATUS_PLAYING;
    }
	// TODO: check how to pause psf2
}

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
void psf_seek(int seek, PSF_SEEK_MODE mode)
{
    debug_printf("%s: seek %d, mode %d\n", __FUNCTION__, seek, mode);

    if(global_psf_status == PSF_STATUS_PLAYING)
    {
        global_seektime = seek;
        global_command = CMD_SEEK;
        switch(mode)
        {
            case PSF_SEEK_CUR:
            {
                //now global_seektime is a offset from current time
                if(global_seektime>=0)
                {
					if (global_psf_type == TYPE_PSF) {
                    	global_seektime = my_sexy_seek(global_seektime); //now global_seektime is the offset from the start time
					}
					else if (global_psf_type == TYPE_PSF2) {
						// TODO: psf2
					}
                    global_command = CMD_NONE;
                }
                else    // Negative time, we must close & restart the playback
                {
					if (global_psf_type == TYPE_PSF) {
		                global_seektime = my_sexy_seek(global_seektime);
		                sexy_stop();
					}
					else if (global_psf_type == TYPE_PSF2) {
						// TODO: psf2
					}
                }
            }
            break;
            case PSF_SEEK_SET:
            {
				if (global_psf_type == TYPE_PSF) {
		            if(sexy_seek(global_seektime) == 0)
		            {//Negative time!  Must make a C time machine.
		               sexy_stop();
		               return;
		            }
				}
				else if (global_psf_type == TYPE_PSF2) {
					// TODO: psf2
				}
            }
            break;
            default:
            break;
        }
    }
}

/*==================================================================================================

FUNCTION: sexyd_update

DESCRIPTION: This function is called by sexy_psf's psx engine, it produce the sound data

ARGUMENTS PASSED:
   Buffer  -  pointer to the sound data psx produced

   count  -  the data size

RETURN VALUE:
   None

DEPENDENCIES:
   None

SIDE EFFECTS:
   None

Notes:
   When psx engine is running, it will produce the sound data and call this function;
   we implement this function and put the data into the audio buffer.
==================================================================================================*/
void sexyd_update(unsigned char *Buffer, long count)
{
#ifdef USE_DEBUG_PRINTF
    static int debug_index = 0;
#endif
    int put_len, putindex;

    debug_printf2("in %s: %d, buf: %08X, len: %d\n", __FUNCTION__, debug_index++, Buffer, count);

#ifdef DEBUG_DUMP_PCM
    if (dump_file2 && count != 0) {
        debug_printf("Dump pcm2.o data %d\n", count);
		fwrite(Buffer, count, 1, dump_file2);
    }
#endif
    putindex = 0;

    while(putindex < count)
    {
        put_len = put_audio_buf(Buffer+putindex, count-putindex);
        putindex+=put_len;
        usleep(1000);
    }
#if 0
    if(global_command == CMD_SEEK)
    {
        //now global_seektime is a offset from current time
        if(global_seektime>=0)
        {
            global_seektime = my_sexy_seek(global_seektime); //now global_seektime is the offset from the start time
            global_command = CMD_NONE;
        }
        else    // Negative time, we must close & restart the playback
        {
            global_seektime = my_sexy_seek(global_seektime);
            sexy_stop();
            return;
        }
    }
#endif
    if(global_command == CMD_STOP)
    {
        debug_printf("in sexyd_update, call sexy_stop\n");
        sexy_stop();
    }
    debug_printf2("%s returned\n", __FUNCTION__);
}

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
int psf_audio_putdata(uint8_t *stream, int len)
{
#ifdef USE_DEBUG_PRINTF
    static int debug_index = 0;
#endif
    int get_len, audio_data_index;

    debug_printf2("in psf_audio_callback: %d\n", debug_index++);

    if(len > AUDIO_BLOCK_BUFFER_SIZE)
        handle_error();

    audio_data_index = 0;

    while(audio_data_index < len)
    {
        if(global_command == CMD_STOP)
        {
            sexypsf_clear_audio_buffer();
            break;
        }
        get_len = get_audio_buf(stream+audio_data_index, len-audio_data_index);
#ifdef DEBUG_DUMP_PCM
    if (dump_file && get_len != 0) {
        debug_printf("Dump pcm2 data %d\n", get_len);
		fwrite(stream+audio_data_index, get_len, 1, dump_file);
    }
#endif
        audio_data_index+=get_len;
        if( (get_len == 0) &&
        	(global_psf_status == PSF_STATUS_STOPPED) )
        {
        	break;
        }
        usleep(1000);
    }

    if (audio_data_index < len) {
    	debug_printf("audio_data_index %d < len %d\n", audio_data_index, len);
    }
    return audio_data_index;
}

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
int psf_get_pos()
{
	int ret = 0;
	if (global_psf_type == TYPE_PSF) {
		ret = my_sexy_get_cur_time();
	}
	else if (global_psf_type == TYPE_PSF2) {
		// TODO: psf2
		ret = my_psf2_get_cur_time();
	}
	return ret;
}

/*==================================================================================================

FUNCTION: sexypsf_bufferstatus

DESCRIPTION: get the buffer status of sexy audio

ARGUMENTS PASSED:
   None

RETURN VALUE:
   SEXY_BUFFER_EMPTY when the audio buffer is empty
   SEXY_BUFFER_FULL otherwise

DEPENDENCIES:
   None

SIDE EFFECTS:
   None

Notes:
   None
==================================================================================================*/
static int sexypsf_bufferstatus()
{
    if(fifo_freesize(&playing_audio_buf) == AUDIO_BLOCK_BUFFER_SIZE)
        return SEXY_BUFFER_EMPTY;
    else
        return SEXY_BUFFER_FULL;
}


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
void sexypsf_quit()
{
	if (global_psf_type == TYPE_PSF) {
		psf_stop();
		if (PSFInfo!= NULL) {
			sexy_freepsfinfo(PSFInfo);
			PSFInfo = NULL;
		}
	}
	else if (global_psf_type == TYPE_PSF2) {
		// TODO: psf2
		psf2_stop();
		psf2_cleanup();
	}

	if (mutex_initialized) {
		pthread_mutex_destroy(&audio_buf_mutex);
		mutex_initialized = FALSE;
	}
}

PSF_INFO* psf_getinfo(const char* filename)
{
	PSF_INFO *ret = NULL;
	char* ext = strrchr(filename, '.');
	if (ext == NULL) {
		return NULL;
	}
	ext++;
	if (strcasecmp(ext, "psf") == 0
		|| strcasecmp(ext, "minipsf") == 0) {
		// psf file
		PSFINFO* info = sexy_getpsfinfo(filename);
		if (info != NULL) {
			ret=malloc(sizeof(PSF_INFO));
			memset(ret, 0, sizeof(PSF_INFO));
			ret->length = info->length;
			if (info->artist) {
				ret->artist = strdup(info->artist);
			}
			if (info->copyright) {
				ret->copyright = strdup(info->copyright);
			}
			if (info->game) {
				ret->game = strdup(info->game);
			}
			if (info->title) {
				ret->title = strdup(info->title);
			}
		}
	}
	else if (strcasecmp(ext, "psf2") == 0
		|| strcasecmp(ext, "minipsf2") == 0) {
		void* buffer;
		uint32 size;
		ret = psf2_load(filename, &buffer, &size);
		if (ret != NULL) {
			free(buffer);
		}
	}
	return ret;
}

void psf_freeinfo(PSF_INFO* info)
{
	if (info == NULL) {
		return;
	}
	if (info->artist) {
		free(info->artist);
	}
	if (info->copyright) {
		free(info->copyright);
	}
	if (info->game) {
		free(info->game);
	}
	if (info->title) {
		free(info->title);
	}
	free(info);
}

/** PSF2 support functions */

// Get file's contents
static void file_get_contents(const char* filename, void** buffer, uint32* size) {
	FILE *fd;
	*buffer = NULL;
	*size = 0;

	fd = fopen(filename, "rb");

	if (fd == NULL) {
		handle_error();
		return;
	}

	fseek(fd, 0, SEEK_END);
	*size = ftell(fd);
	*buffer = malloc(*size);

	if (*buffer == NULL) {
		handle_error();
		return;
	}

	fseek(fd, 0, SEEK_SET);
	fread(*buffer, 1, *size, fd);

	fclose(fd);
}

int ao_get_lib(char *filename, uint8 **buffer, uint64 *length)
{
	void *filebuf;
	uint32 size;

	char* libfile = GetFileWithBase((char*)stored_filename, filename);
	debug_printf("load psf2 lib %s\n", libfile);

	file_get_contents (libfile, &filebuf, &size);
	free (libfile);

	*buffer = filebuf;
	*length = (uint64)size;

	return AO_SUCCESS;
}

void psf2_update(unsigned char *buffer, long count, InputPlayback *playback)
{
#ifdef USE_DEBUG_PRINTF
    static int debug_index = 0;
#endif
    int put_len, putindex;

    debug_printf2("in %s: %d, buf: %08X, len: %d\n", __FUNCTION__, debug_index++, buffer, count);

    if (buffer == NULL) {
        // play to the end, set stop command to simulate psf2 stop
        psf2_stop_flag = TRUE;
        return;
    }
#ifdef DEBUG_DUMP_PCM
    if (dump_file2 && count != 0) {
        debug_printf("Dump pcm2.o data %d\n", count);
		fwrite(buffer, count, 1, dump_file2);
    }
#endif
    putindex = 0;

    while(putindex < count)
    {
        put_len = put_audio_buf(buffer+putindex, count-putindex);
        putindex+=put_len;
        usleep(1000);
    }

    if(global_command == CMD_STOP)
    {
        debug_printf("%s: set psf2_stop_flag\n", __FUNCTION__);
        psf2_stop_flag = TRUE;
    }
}

PSF_INFO* psf2_load(const char *filename, void** buffer, uint32* size) {
	// Open and read the content of the file
	corlett_t *c;

	file_get_contents(filename, buffer, size);

	// decode and get the file infor
	if (corlett_decode(*buffer, *size, NULL, NULL, &c) != AO_SUCCESS) {
		free(*buffer);
		return NULL;
	}

	PSF_INFO *psfi=malloc(sizeof(PSF_INFO));
	memset(psfi,0,sizeof(PSF_INFO));

	psfi->length = psfTimeToMS(c->inf_length) + psfTimeToMS(c->inf_fade);
	psfi->artist = strdup(c->inf_artist);
	psfi->copyright = strdup(c->inf_copy);
	psfi->game = strdup(c->inf_game);
	psfi->title = strdup(c->inf_title);

	free(c);
	return psfi;
}

void psf2_cleanup() {
	if (PSF2Info) {
		psf_freeinfo(PSF2Info);
	}
	if (psf2_buffer) {
		free(psf2_buffer);
		psf2_buffer = NULL;
	}
	psf2_size = 0;
}

void *psf2_playloop(void *arg)
{
    global_psf_status = PSF_STATUS_PLAYING;

	while(TRUE)
	{
	    debug_printf("%s: do psf2_execute...\n", __FUNCTION__);
		psf2_execute(NULL);

		if (CMD_SEEK == global_command)
		{
			//data->output->flush(seek);
			// TODO: flush the audio

			psf2_stop();

			if (psf2_start(psf2_buffer, psf2_size) == AO_SUCCESS)
			{
				psf2_seek(global_seektime);
				global_command = CMD_NONE;
				continue;
			}
			else {
				handle_error();
				break;
			}
		}

	    debug_printf("%s: call psf2_stop...\n", __FUNCTION__);

		psf2_stop();
/*
		while(psf2_stop_flag == TRUE \
			&& sexypsf_bufferstatus() != SEXY_BUFFER_EMPTY) {
            debug_printf("%s: in psf2 playloop, sleeping...\n", __FUNCTION__);
			usleep(10000);
		}
*/
		break;
	}

    debug_printf("psf2 playloop exit\n");
    psf2_stop_flag = TRUE;
    global_psf_status = PSF_STATUS_STOPPED;
    pthread_exit(0);
    return NULL;
}
