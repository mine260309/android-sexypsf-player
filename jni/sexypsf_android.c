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

====================================================================================================
                                         INCLUDE FILES
==================================================================================================*/

#include <pthread.h>

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#define DEBUG_LEVEL 1

#ifdef USE_SDL
#include "sexypsf_psp.h"
#else
#include "sexypsf_android.h"
#endif

#ifdef USE_SDL
#include <SDL/SDL.h>
#include <SDL/SDL_thread.h>
#include <SDL/SDL_joystick.h>
#endif

/*==================================================================================================
                                     GLOBAL FUNCTIONS
==================================================================================================*/
int my_sexy_get_cur_time();
int my_sexy_seek(int32_t t);

/*==================================================================================================
                                     LOCAL CONSTANTS
==================================================================================================*/
#define AUDIO_BLOCK_BUFFER_SIZE (1024*512)

/*==================================================================================================
                                      LOCAL VARIABLES
==================================================================================================*/
static PSFINFO *PSFInfo=NULL;                                   //the psf info structure defined in driver.h
static const char* stored_filename = NULL;                            //the stored file name
static uint8_t playing_audio_buf[AUDIO_BLOCK_BUFFER_SIZE];      //the audio buffer
static uint8_t* audio_buf_pointer = playing_audio_buf;          //the pointer of audio buffer
static int put_index=0, get_index=0, free_size = 0;             //the index used for the audio buffer

volatile PSF_CMD    global_command;             //the global command
volatile PSF_STATUS global_psf_status;          //the global status
int                 global_seektime = 0;        //the global seek time

#ifdef USE_SDL_MUTEX
SDL_mutex* audio_buf_mutex;                     //the mutex to control the access of the audio buffer
#else
static char buffer_use_flag = 0;                //the mutex to control the access of the audio buffer
#endif

#ifndef USE_SDL_THREAD
static pthread_t dethread;                      //the play back thread
#else
static SDL_Thread* dethread;
#endif /* USE_SDL_THREAD */

typedef enum
{
  SEXY_BUFFER_EMPTY,
  SEXY_BUFFER_FULL
};

/*==================================================================================================
                                     LOCAL FUNCTION PROTOTYPES
==================================================================================================*/
static void sexypsf_clear_audio_buffer();
static int  put_audio_buf(void* buf, int len);
static int  get_audio_buf(void* buf_ptr, int wanted_len);
static void *playloop(void *arg);
void        sexyd_update(unsigned char *Buffer, long count);
static int  sexypsf_bufferstatus();
/*==================================================================================================
                                     LOCAL FUNCTIONS
==================================================================================================*/
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
    int put_len1, put_len2;
    int actual_put_len;
    debug_printf2("%s: buf %08X, len %d", __FUNCTION__, buf, len);
#ifdef USE_SDL_MUTEX
    SDL_LockMutex(audio_buf_mutex);

#else
    while(buffer_use_flag)
        SDL_Delay(1);
    buffer_use_flag = 1;
#endif
    if(free_size > len)
    {
        actual_put_len = len;
    }
    else
    {
        actual_put_len = free_size;
    }
    free_size -= actual_put_len;
#ifdef USE_SDL_MUTEX
    SDL_UnlockMutex(audio_buf_mutex);
#else
    buffer_use_flag = 0;
#endif

    if(put_index + actual_put_len < AUDIO_BLOCK_BUFFER_SIZE)
    {
        put_len1 = actual_put_len;
        memcpy(audio_buf_pointer+put_index, buf, put_len1);
        put_index+=put_len1;
    }
    else
    {   /* slipt to two memcpy */
        put_len1 = AUDIO_BLOCK_BUFFER_SIZE-put_index;
        memcpy(audio_buf_pointer+put_index, buf, put_len1);
        put_len2 = actual_put_len - put_len1;
        memcpy(audio_buf_pointer, buf+put_len1, put_len2);
        put_index = put_len2;
    }

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
    int get_len1, get_len2;
    int actual_get_len;
    int data_len_in_buffer;

    debug_printf2("%s: buf %08X, len %d", __FUNCTION__, buf_ptr, wanted_len);
#ifdef USE_SDL_MUTEX
    SDL_LockMutex(audio_buf_mutex);

#else
    while(buffer_use_flag)
        SDL_Delay(1);
    buffer_use_flag = 1;
#endif

    data_len_in_buffer = AUDIO_BLOCK_BUFFER_SIZE - free_size;
    if(data_len_in_buffer > wanted_len)
    {
        actual_get_len = wanted_len;
    }
    else
    {
        actual_get_len = data_len_in_buffer;
    }
    if(actual_get_len != 0)
        free_size += actual_get_len;

#ifdef USE_SDL_MUTEX
    SDL_UnlockMutex(audio_buf_mutex);
#else
    buffer_use_flag = 0;
#endif

    if(actual_get_len!=0)
    {
    if(get_index + actual_get_len < AUDIO_BLOCK_BUFFER_SIZE)
    {
        get_len1 = actual_get_len;
        memcpy(buf_ptr, audio_buf_pointer+get_index, get_len1);
        get_index+=get_len1;
    }
    else
    {   /* split to two memcpy */
        get_len1 = AUDIO_BLOCK_BUFFER_SIZE-get_index;
        memcpy(buf_ptr, audio_buf_pointer+get_index, get_len1);
        get_len2 = actual_get_len - get_len1;
        memcpy(buf_ptr+get_len1, audio_buf_pointer, get_len2);
        get_index = get_len2;
    }
    }

    return actual_get_len;
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
        SDL_Delay(2000);
    }

    debug_printf("playloop exit\n");

    global_psf_status = PSF_STATUS_STOPPED;
#ifndef USE_SDL_THREAD
    pthread_exit(0);
#else
    {
        SDL_Event event;
        event.type = SDL_QUIT;
        SDL_PushEvent(&event);
    }
#endif
    return NULL;
}

#ifdef USE_SDL_THREAD
/*==================================================================================================

FUNCTION: psf_audio_callback

DESCRIPTION: the callback function by SDL audio

ARGUMENTS PASSED:
   userdata - the pointer to userdata when SDL_OpenAudio is called, here it's always NULL
   stream   - the pointer to the audio buffer we need to feed
   len      - the length of the stream

RETURN VALUE:
   None

DEPENDENCIES:
   None

SIDE EFFECTS:
   None

Notes:
   This function is called by SDL Audio lib, it requests "len" size for the audio data,
   we need to feed the data in "stream" to play the sound.
   
==================================================================================================*/
static void psf_audio_callback(void *userdata, Uint8 *stream, int len)
{
#ifdef USE_DEBUG_PRINTF
    static int debug_index = 0;
#endif
    static uint8_t audio_static_data[AUDIO_BLOCK_BUFFER_SIZE];
    int get_len, audio_data_index;
//int get_audio_buf(void* buf_ptr, int wanted_len)

    debug_printf2("in psf_audio_callback: %d\n", debug_index++);

    if(len > AUDIO_BLOCK_BUFFER_SIZE)
        handle_error();

/*  if(global_clear_buf_flag)
    {
        printf("in callback clear\n");
        memset(audio_static_data, 0, AUDIO_BLOCK_BUFFER_SIZE);
        SDL_MixAudio(stream, audio_static_data, len, SDL_MIX_MAXVOLUME);
        return;
    }*/
    audio_data_index = 0;

    while(audio_data_index < len)
    {
        if(global_command == CMD_STOP)
        {
            sexypsf_clear_audio_buffer();
            memset(audio_static_data, 0, len);
            break;
        }
        get_len = get_audio_buf(audio_static_data+audio_data_index, len-audio_data_index);
        audio_data_index+=get_len;

        if(get_len == 0)
        {// if there's no audio now, let's mute
            memset(audio_static_data, 0, len);
            break;
        }
        SDL_Delay(1);
    }

    SDL_MixAudio(stream, audio_static_data, len, SDL_MIX_MAXVOLUME);

    //SDL_Delay(1);
    //while(get_len < len);

    return;
}
#endif

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
#ifdef USE_SDL_MUTEX
    if(SDL_Init(SDL_INIT_TIMER|SDL_INIT_AUDIO|SDL_INIT_VIDEO))
    {
        fprintf(stderr, "Could not initialize SDL - %s\n", SDL_GetError());
        exit(-1);
    }
    audio_buf_mutex = SDL_CreateMutex();
    if(audio_buf_mutex == NULL)
        handle_error();
#else
    buffer_use_flag = 0;
#endif
    free_size = AUDIO_BLOCK_BUFFER_SIZE;
    global_command = CMD_NONE;
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
    put_index=0, get_index=0, free_size = AUDIO_BLOCK_BUFFER_SIZE;
}


/*==================================================================================================
                                     GLOBAL FUNCTIONS
==================================================================================================*/

#ifdef USE_SDL_MUTEX
/*==================================================================================================

FUNCTION: sexypsf_SDL_quit

DESCRIPTION: quit the sexypsf_SDL lib

ARGUMENTS PASSED:
   None

RETURN VALUE:
   None

DEPENDENCIES:
   None

SIDE EFFECTS:
   None

==================================================================================================*/
void sexypsf_SDL_quit()
{
#ifdef USE_SDL_MUTEX
    SDL_DestroyMutex(audio_buf_mutex);
#endif
    SDL_Quit();
}

/*==================================================================================================

FUNCTION: psf_play_file

DESCRIPTION: play a psf file

ARGUMENTS PASSED:
   file_name - file name string, NULL terminated

RETURN VALUE:
   None

DEPENDENCIES:
   None

SIDE EFFECTS:
   This function will not return until the playback is done or psf_stop is called.

==================================================================================================*/
void psf_play_file(char* file_name)
{
    SDL_AudioSpec wanted_spec, spec;
    SDL_Event event;
#ifdef DEBUG_SHOW_TIME
    SDL_TimerID show_time_id;
#endif

    if(!(PSFInfo=sexy_load(file_name)))
    {
        handle_error();
    }
    stored_filename = file_name;

    wanted_spec.freq = 44100;
    wanted_spec.format = AUDIO_S16SYS;
    wanted_spec.channels = 2;
    wanted_spec.silence = 0;
    wanted_spec.samples = 1024;//SDL_AUDIO_BUFFER_SIZE;
    wanted_spec.callback = psf_audio_callback;
    wanted_spec.userdata = NULL;
    if(SDL_OpenAudio(&wanted_spec, &spec) < 0)
    {
        fprintf(stderr, "SDL_OpenAudio: %s\n", SDL_GetError());
        handle_error();
    }
    SDL_SetVideoMode(100, 100, 0, SDL_RESIZABLE |SDL_HWSURFACE|SDL_ASYNCBLIT|SDL_HWACCEL/*SDL_NOFRAME*/);

#ifdef DEBUG_SHOW_TIME
    show_time_id = SDL_AddTimer(1000, sexypsf_show_time, NULL);
#endif
//    pthread_create(&dethread,0,playloop,0);
	dethread = SDL_CreateThread(playloop, NULL);
    SDL_PauseAudio(0);
    while(1)
    {
        int incr;
        SDL_WaitEvent(&event);
        switch(event.type)
        {
        case SDL_KEYDOWN:
            switch(event.key.keysym.sym)
            {
                case SDLK_q:
                    goto quit_clause;
                    break;
                case SDLK_LEFT:
                    incr = -10*1000;
                    goto do_seek;
                case SDLK_RIGHT:
                    incr = 10*1000;
                    goto do_seek;
                case SDLK_UP:
                    incr = 60*1000;
                    goto do_seek;
                case SDLK_DOWN:
                    incr = -60*1000;
                    goto do_seek;
                do_seek:
                    psf_seek(incr, PSF_SEEK_CUR);
                    break;
                case SDLK_p:
                    psf_pause(TRUE);
                    break;
                case SDLK_c:
                    psf_pause(FALSE);
                    break;
                default:
                    break;
            }
            break;
        case SDL_QUIT:
quit_clause:
            debug_printf("quit clause\n");
            psf_stop();
            debug_printf("sdl_quit\n");
            goto psf_exit;
            break;
        default:
            SDL_Delay(1);
            break;
        }
        SDL_Delay(1);
    }
psf_exit:

#ifdef DEBUG_SHOW_TIME
    SDL_RemoveTimer(show_time_id);
#endif
    debug_printf("play end\n");
}

#else
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
BOOL psf_open(const char* file_name)
{
    if(!(PSFInfo=sexy_load((char*)file_name)))
    {
        debug_printf("%s: open file %s fail!!\n", __FUNCTION__, file_name);
        handle_error();
        return FALSE;
    }
    stored_filename = file_name;
    sexypsf_init();

    debug_printf("%s: open file %s success.\n", __FUNCTION__, file_name);
    return TRUE;
}


void psf_play()
{
    debug_printf("%s: playing file %s...\n", __FUNCTION__, stored_filename);
#ifndef USE_SDL_THREAD
    pthread_create(&dethread,0,playloop,0);
#else
    dethread = SDL_CreateThread(playloop, NULL);
#endif
}
#endif /* USE_SDL_THREAD */
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
    debug_printf("%s\n", __FUNCTION__);
    global_command = CMD_STOP;
#ifndef USE_SDL_THREAD
    pthread_join(dethread,0);
#else
    SDL_WaitThread(dethread, NULL);
#endif
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
#ifdef USE_SDL_MUTEX
        SDL_PauseAudio(1);
#endif
        global_psf_status = PSF_STATUS_PAUSE;
    }
    else if((global_psf_status == PSF_STATUS_PAUSE) && !pause)
    {
//printf("resume audio\n");
#ifdef USE_SDL_MUTEX
        SDL_PauseAudio(0);
#endif
        global_psf_status = PSF_STATUS_PLAYING;
    }
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
                    global_seektime = my_sexy_seek(global_seektime); //now global_seektime is the offset from the start time
                    global_command = CMD_NONE;
                }
                else    // Negative time, we must close & restart the playback
                {
                    global_seektime = my_sexy_seek(global_seektime);
                    sexy_stop();
                }
            }
            break;
            case PSF_SEEK_SET:
            {
                if(sexy_seek(global_seektime) == 0)
                {//Negative time!  Must make a C time machine.
                   sexy_stop();
                   return;
                }
            }
            break;
            default:
            break;
        }
        /*
        global_seektime = my_sexy_seek(seek);
        sexypsf_clear_audio_buffer();
        if(seek < 0)
        {
            sexy_stop();
            global_command = CMD_SEEK;
        }*/
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

    putindex = 0;

    while(putindex < count)
    {
        put_len = put_audio_buf(Buffer+putindex, count-putindex);
        putindex+=put_len;
        SDL_Delay(1);
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

#ifndef USE_SDL_MUTEX
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
#if 0   // just use following code to check the functionality
    static int flag = 1;
    int i;

    if(flag)
        srand(0);
    for(i = 0;i<len;++i)
    {
        stream[i] = (uint8_t)rand();
    }
    return;
#endif
#ifdef USE_DEBUG_PRINTF
    static int debug_index = 0;
#endif
    static uint8_t audio_static_data[AUDIO_BLOCK_BUFFER_SIZE];
    int get_len, audio_data_index;
//int get_audio_buf(void* buf_ptr, int wanted_len)

    debug_printf2("in psf_audio_callback: %d\n", debug_index++);

    if(len > AUDIO_BLOCK_BUFFER_SIZE)
        handle_error();

/*  if(global_clear_buf_flag)
    {
        printf("in callback clear\n");
        memset(audio_static_data, 0, AUDIO_BLOCK_BUFFER_SIZE);
        SDL_MixAudio(stream, audio_static_data, len, SDL_MIX_MAXVOLUME);
        return;
    }*/
    audio_data_index = 0;

    while(audio_data_index < len)
    {
        if(global_command == CMD_STOP)
        {
            sexypsf_clear_audio_buffer();
            memset(audio_static_data, 0, len);
            break;
        }
        get_len = get_audio_buf(audio_static_data+audio_data_index, len-audio_data_index);
        audio_data_index+=get_len;
        if( (get_len == 0) &&
        	(global_psf_status == PSF_STATUS_STOPPED) )
        {
        	memset(audio_static_data, 0, len);
        	len = 0;
        	break;
        }
#if 0 //This piece of code makes the thread not blocked, but we need to block the thread now, so comment it!
        if(get_len == 0)
        {// if there's no audio now, let's mute
            memset(audio_static_data, 0, len);
            break;
        }
#endif
        SDL_Delay(1);
    }

//    SDL_MixAudio(stream, audio_static_data, len, SDL_MIX_MAXVOLUME);
    memcpy(stream, audio_static_data, len);

    //SDL_Delay(1);
    //while(get_len < len);

    return len;
}
#endif

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
    if(free_size == AUDIO_BLOCK_BUFFER_SIZE)
        return SEXY_BUFFER_EMPTY;
    else
        return SEXY_BUFFER_FULL;
}


