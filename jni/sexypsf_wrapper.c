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

#define DEBUG_LEVEL 1
//#define DEBUG_DUMP_PCM

#include <string.h>
#include <jni.h>
#include "sexypsf_android.h"

#ifdef DEBUG_DUMP_PCM
  #include <stdio.h>
  // dump the decoded audio data
  static FILE* dump_file;
#endif

static char sexypsf_filename[1024];
//jstring to char*
//!!!It uses a 1K buffer to store the string, so use it only once!!!
static char* jstringTostring(JNIEnv* env, jstring jstr)
{
       char* rtn = sexypsf_filename;
       jclass clsstring = (*env)->FindClass(env, "java/lang/String");
       jstring strencode = (*env)->NewStringUTF(env, "utf-8");
       jmethodID mid = (*env)->GetMethodID(env, clsstring, "getBytes", "(Ljava/lang/String;)[B");
       jbyteArray barr= (jbyteArray)(*env)->CallObjectMethod(env, jstr, mid, strencode);
       jsize alen = (*env)->GetArrayLength(env, barr);
       jbyte* ba = (*env)->GetByteArrayElements(env, barr, JNI_FALSE);
       if (alen > 0 && alen <1024)
       {
           memcpy(rtn, ba, alen);
           rtn[alen] = 0;
       }
       (*env)->ReleaseByteArrayElements(env, barr, ba, 0);
       return rtn;
}

/*==================================================================================================

FUNCTION: Java_com_sexypsf_SexypsfWrapper_sexypsfopen

DESCRIPTION: Sexypsf wrapper to call function psf_open

ARGUMENTS PASSED:
   env      - JNI env
   jobject  - JNI object
   filename - file name string, NULL terminated

RETURN VALUE:
   None

DEPENDENCIES:
   None

SIDE EFFECTS:
   None

==================================================================================================*/
jboolean Java_com_sexypsf_SexypsfWrapper_sexypsfopen( JNIEnv* env,
                                             jobject thiz,
                                             jstring filename)
{
    const char* name = jstringTostring(env, filename);
#ifdef DEBUG_DUMP_PCM
    char* dump_file_name = (char*)malloc(strlen(name)+5);
    if (dump_file_name != NULL) {
    	strcpy(dump_file_name, name);
    	strcat(dump_file_name, ".dmp");
        dump_file = fopen(dump_file_name, "wb");
        if (dump_file) {
            sexypsf_dbg_printf("Opened dump file %s\n", dump_file_name);
        }
        else {
            sexypsf_dbg_printf("Open dump file failure %s\n", dump_file_name);
        }
        free(dump_file_name);
    }
#endif
    return psf_open(name);
}


/*==================================================================================================

FUNCTION: Java_com_sexypsf_SexypsfWrapper_sexypsfplay

DESCRIPTION: Sexypsf wrapper to call function psf_play

ARGUMENTS PASSED:
   env      - JNI env
   jobject  - JNI object

RETURN VALUE:
   None

DEPENDENCIES:
   None

SIDE EFFECTS:
   This function will not return until the playback is done or psf_stop is called.

==================================================================================================*/
void Java_com_sexypsf_SexypsfWrapper_sexypsfplay( JNIEnv* env,
                                             jobject thiz)
{
    psf_play();
}


/*==================================================================================================

FUNCTION: Java_com_sexypsf_SexypsfWrapper_sexypsfpause

DESCRIPTION: Sexypsf wrapper to call function psf_pause

ARGUMENTS PASSED:
   env      - JNI env
   jobject  - JNI object
   pause    - Indicate if it's pause or resume

RETURN VALUE:
   None

DEPENDENCIES:
   None

SIDE EFFECTS:
   None

==================================================================================================*/
void Java_com_sexypsf_SexypsfWrapper_sexypsfpause( JNIEnv* env,
                                             jobject thiz,
                                             jboolean   pause)
{
    psf_pause(pause);
}

/*==================================================================================================

FUNCTION: Java_com_sexypsf_SexypsfWrapper_sexypsfseek

DESCRIPTION: Sexypsf wrapper to call function psf_seek

ARGUMENTS PASSED:
   env      - JNI env
   jobject  - JNI object
   seek     - the offset of seek
   mode     - the seek mode

RETURN VALUE:
   None

DEPENDENCIES:
   None

SIDE EFFECTS:
   None

==================================================================================================*/
void Java_com_sexypsf_SexypsfWrapper_sexypsfseek( JNIEnv* env,
                                             jobject thiz,
                                             jint    seek,
                                             jint    mode)
{
    psf_seek(seek, mode);
}

/*==================================================================================================

FUNCTION: Java_com_sexypsf_SexypsfWrapper_sexypsfstop

DESCRIPTION: Sexypsf wrapper to call function psf_stop

ARGUMENTS PASSED:
   env      - JNI env
   jobject  - JNI object

RETURN VALUE:
   None

DEPENDENCIES:
   None

SIDE EFFECTS:
   None

==================================================================================================*/
void Java_com_sexypsf_SexypsfWrapper_sexypsfstop( JNIEnv* env,
                                             jobject thiz)
{
    psf_stop();
#ifdef DEBUG_DUMP_PCM
    if (dump_file) {
        sexypsf_dbg_printf("Closing dump file\n");
    	fclose(dump_file);
    }
    dump_file = NULL;
#endif
}


/*==================================================================================================

FUNCTION: Java_com_sexypsf_SexypsfWrapper_sexypsfputaudiodata

DESCRIPTION: Sexypsf wrapper to put audio data to java buffer

ARGUMENTS PASSED:
   env      - JNI env
   jobject  - JNI object
   arr      - Java byte array
   size     - size of the audio data to put

RETURN VALUE:
   None

DEPENDENCIES:
   None

SIDE EFFECTS:
   None

==================================================================================================*/
jint Java_com_sexypsf_SexypsfWrapper_sexypsfputaudiodata( JNIEnv* env,
                                             jobject thiz, jbyteArray arr, jint size)
{
    jbyte *carr;
    int ret;
    carr = (*env)->GetPrimitiveArrayCritical(env, arr, NULL);
    if (carr == NULL) {
        sexypsf_dbg_printf("Get NULL array in JNI call, nothing to put!!!\n");
        return 0; /* exception occurred */
    }
    ret = psf_audio_putdata((uint8_t*)carr, size);
    sexypsf_dbg_printf("sexypsfputaudiodata: req %d, got %d", size, ret);
#ifdef DEBUG_DUMP_PCM
    if (dump_file) {
        sexypsf_dbg_printf("Dump pcm data %d\n", ret);
		fwrite(carr, ret, 1, dump_file);
    }
#endif
    (*env)->ReleasePrimitiveArrayCritical(env, arr, carr, 0);
    return ret;
}

/*==================================================================================================

FUNCTION: Java_com_sexypsf_SexypsfWrapper_sexypsfputaudiodataindex

DESCRIPTION: Sexypsf wrapper to put audio data to java buffer

ARGUMENTS PASSED:
   env      - JNI env
   jobject  - JNI object
   arr      - Java byte array
   size     - size of the audio data to put

RETURN VALUE:
   None

DEPENDENCIES:
   None

SIDE EFFECTS:
   None

==================================================================================================*/
jint Java_com_sexypsf_SexypsfWrapper_sexypsfputaudiodataindex( JNIEnv* env,
                                      jobject thiz, jbyteArray arr, jint index, jint size)
{
    jbyte *carr;
    int ret;
    carr = (*env)->GetPrimitiveArrayCritical(env, arr, NULL);
    if (carr == NULL) {
        sexypsf_dbg_printf("Get NULL array in JNI call, nothing to put!!!\n");
        return 0; /* exception occurred */
    }
    ret = psf_audio_putdata((uint8_t*)carr+index, size);
    sexypsf_dbg_printf("sexypsfputaudiodataindex: req %d, got %d", size, ret);
#ifdef DEBUG_DUMP_PCM
    if (dump_file) {
        sexypsf_dbg_printf("Dump pcm data %d\n", ret);
		fwrite(carr+index, ret, 1, dump_file);
    }
#endif
    (*env)->ReleasePrimitiveArrayCritical(env, arr, carr, 0);
    return ret;
}
