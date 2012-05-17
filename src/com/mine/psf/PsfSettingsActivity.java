/*************************************************************************
 * MineMessageVibrator is an Android App that provides vibrate and 
 * reminder functions for SMS, MMS, Gmail, etc. 
 * Copyright (C) 2010  Lei YU
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

import com.mine.psfplayer.R;

import android.os.Bundle;
import android.preference.PreferenceActivity;

public class PsfSettingsActivity extends PreferenceActivity {
	
	@Override
	protected void onCreate(Bundle icicle) {
		
		super.onCreate(icicle);

		addPreferencesFromResource(R.xml.preferences);
	}
	
	// TODO: Implement setting psf root dir function
}
