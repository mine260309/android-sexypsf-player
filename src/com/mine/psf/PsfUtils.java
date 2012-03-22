/*************************************************************************
 * MinePsfPlayer is an Android App that plays psf and minipsf files.
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

import java.util.HashMap;

import com.mine.psfplayer.R;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.ServiceConnection;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

public class PsfUtils {
    private static final String LOGTAG = "PsfUtils";
    
    public static class ServiceToken {
        ContextWrapper mWrappedContext;
        ServiceToken(ContextWrapper context) {
            mWrappedContext = context;
        }
    }
    
    public static PsfPlaybackService sService = null;
    private static HashMap<Context, ServiceBinder> sConnectionMap =
    	new HashMap<Context, ServiceBinder>();

    public static ServiceToken bindToService(Activity context) {
        return bindToService(context, null);
    }
    
    public static ServiceToken bindToService(Activity context,
    		ServiceConnection callback) {
        Activity realActivity = context.getParent();
        if (realActivity == null) {
            realActivity = context;
        }
        ContextWrapper cw = new ContextWrapper(realActivity);
        cw.startService(new Intent(cw, PsfPlaybackService.class));
        ServiceBinder sb = new ServiceBinder(callback);
        if (cw.bindService((new Intent()).setClass(cw, PsfPlaybackService.class), sb, 0)) {
            sConnectionMap.put(cw, sb);
            return new ServiceToken(cw);
        }
        Log.e(LOGTAG, "Failed to bind to service");
        return null;
    }

    public static void unbindFromService(ServiceToken token) {
        if (token == null) {
            Log.e(LOGTAG, "Trying to unbind with null token");
            return;
        }
        ContextWrapper cw = token.mWrappedContext;
        ServiceBinder sb = sConnectionMap.remove(cw);
        if (sb == null) {
            Log.e(LOGTAG, "Trying to unbind for unknown Context");
            return;
        }
        cw.unbindService(sb);
        if (sConnectionMap.isEmpty()) {
            // presumably there is nobody interested in the service at this point,
            // so don't hang on to the ServiceConnection
        	sService = null;
        }
    }
    
    private static class ServiceBinder implements ServiceConnection {
        ServiceConnection mCallback;
        ServiceBinder(ServiceConnection callback) {
            mCallback = callback;
        }
        
        public void onServiceConnected(ComponentName className, android.os.IBinder service) {
        	sService = ((PsfPlaybackService.ServiceBinder)service).getService();
            if (mCallback != null) {
                mCallback.onServiceConnected(className, service);
            }
        }
        
        public void onServiceDisconnected(ComponentName className) {
            if (mCallback != null) {
                mCallback.onServiceDisconnected(className);
            }
            sService = null;
        }
    }
    
    public static void play(Context context, String psfFile) {
    	if (sService != null) {
	    	sService.openFile(psfFile);
	    	sService.play();
    	}
    }
    
    public static void stop(Context context) {
    	if (sService != null) {
    		sService.stop();
    	}
    }
    
    public static void playAll(String[] playList, int pos) {
    	if (sService != null) {
    		sService.setPlaylist(playList, false);
    		sService.play(pos);
    	}
    }
    
    public static void shuffleAll(String[] playList) {
    	if (sService != null) {
    		sService.setPlaylist(playList, true);
    		sService.play(0);
    	}
    }
    static void updateNowPlaying(Activity a) {
        View nowPlayingView = a.findViewById(R.id.nowplaying);
        if (nowPlayingView == null) {
            return;
        }
        if (PsfUtils.sService != null /* TODO && PsfUtils.sService.getAudioId() != -1*/) {
        	TextView title = (TextView) nowPlayingView.findViewById(R.id.title);
        	TextView artist = (TextView) nowPlayingView.findViewById(R.id.artist);
        	title.setText(PsfUtils.sService.getTrackName());
        	String artistName = PsfUtils.sService.getArtistName();
        	if (artistName.equals("")) {
        		artistName = a.getString(R.string.unknown_artist_name);
        	}
        	artist.setText(artistName);

        	nowPlayingView.setVisibility(View.VISIBLE);
        	nowPlayingView.setOnClickListener(new View.OnClickListener() {
        		public void onClick(View v) {
        			Context c = v.getContext();
        			c.startActivity(new Intent(c, PsfPlaybackActivity.class));
        		}});
        	return;
        }
        nowPlayingView.setVisibility(View.GONE);
    }
}
