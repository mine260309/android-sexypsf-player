# Copyright (C) 2009 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

VERSION := 0.4.8
SEXY_DIR  := sexypsf-$(VERSION)

SEXY_INC_DIR := -I$(LOCAL_PATH)/$(SEXY_DIR)

SEXY_OBJS =	PsxBios.o PsxCounters.o PsxDma.o Spu.o PsxHw.o PsxMem.o Misc.o	\
	R3000A.o PsxInterpreter.o PsxHLE.o spu/spu.o

SEXY_SRC_FILES  := $(addprefix $(SEXY_DIR)/, $(SEXY_OBJS:.o=.c))
LOCAL_MODULE    := sexypsf
LOCAL_SRC_FILES := sexypsf_android.c sexypsf_wrapper.c $(SEXY_SRC_FILES)


SEXY_FLAGS = -DPSS_STYLE=1 -DSPSFVERSION="\"${VERSION}\"" -fPIC
LOCAL_CFLAGS += $(SEXY_INC_DIR) -Wall -O3 -finline-functions -ffast-math $(SEXY_FLAGS)

##This is NDK bug that we have to specify the libz dir
#LOCAL_LDLIBS := -lz
LOCAL_LDLIBS := -L$(SYSROOT)/usr/lib -lz -llog

include $(BUILD_SHARED_LIBRARY)
