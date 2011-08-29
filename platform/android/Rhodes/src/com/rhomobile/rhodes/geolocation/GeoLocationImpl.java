/*------------------------------------------------------------------------
* (The MIT License)
* 
* Copyright (c) 2008-2011 Rhomobile, Inc.
* 
* Permission is hereby granted, free of charge, to any person obtaining a copy
* of this software and associated documentation files (the "Software"), to deal
* in the Software without restriction, including without limitation the rights
* to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
* copies of the Software, and to permit persons to whom the Software is
* furnished to do so, subject to the following conditions:
* 
* The above copyright notice and this permission notice shall be included in
* all copies or substantial portions of the Software.
* 
* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
* IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
* FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
* AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
* LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
* OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
* THE SOFTWARE.
* 
* http://rhomobile.com
*------------------------------------------------------------------------*/
package com.rhomobile.rhodes.geolocation;

import com.rhomobile.rhodes.Logger;
import com.rhomobile.rhodes.RhodesService;
import com.rhomobile.rhodes.util.PerformOnUiThread;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.os.Looper;

public class GeoLocationImpl {

	private static final String TAG = "GeoLocationImpl";
	
	private LocationManager locationManager = null;
	private boolean available = false;
	private double longitude = 0;
	private double latitude = 0;
	private float  accuracy = 0;
	private boolean determined = false;
	
	private RhoLocationListener mGpsListener = new RhoLocationListener();
	private RhoLocationListener mNetworkListener = new RhoLocationListener();
	
	class RhoLocationListener implements LocationListener {
		@Override
		public void onStatusChanged(String provider, int status, Bundle extras) {
			Logger.T(TAG, "onStatusChanged: provider=" + provider + ", status=" + status);
			setCurrentGpsLocation(null);
		}
		
		@Override
		public void onProviderEnabled(String provider) {
			Logger.T(TAG, "onProviderEnabled: provider=" + provider);
			setCurrentGpsLocation(null);
		}
		
		@Override
		public void onProviderDisabled(String provider) {
			Logger.T(TAG, "onProviderDisabled: provider=" + provider);
			setCurrentGpsLocation(null);
		}
		
		@Override
		public void onLocationChanged(Location location) {
			Logger.T(TAG, "onLocationChanged");
			setCurrentGpsLocation(location);
		}
	};
	
	private static int TIMEOUT_STOP = -1;
	
	private int timeout = 10*60*1000; // 10 minutes
	private Thread thCancel = new Thread(new Runnable() {
		public void run() {
			Logger.T(TAG, "\"cancel\" thread started");
			for (;;) {
				if (timeout < 0)
					break;
				try {
					Logger.T(TAG, "Waiting (" + timeout + "ms)...");
					Thread.sleep(timeout);
				}
				catch (InterruptedException e) {
					Logger.T(TAG, "\"cancel\" thread interrupted");
					continue;
				}
				if (!isKnownPosition()) {
					Logger.T(TAG, "Position is still unknown, inform about this");
					PerformOnUiThread.exec(new Runnable() {
						public void run() {
							geoCallbackError();
						}
					});
				}
				timeout = 2147483647;
			}
			Logger.T(TAG, "\"cancel\" thread stopped");
		}
	});
	
	private native void geoCallback();
	private native void geoCallbackError();
	
	public GeoLocationImpl() {
		Logger.T(TAG, "GeoLocationImpl instance created");
		setCurrentGpsLocation(null);
		thCancel.start();
	}

	private void setCurrentGpsLocation(Location location) {
		Logger.T(TAG, "setCurrentGpsLocation: location=" + location);
		try {
			// This array MUST be sorted in order "most-priority-first" meaning
			// that we'll don't ask next provider if previous returned non-null value
			final String[] providers = {LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER};
			final LocationListener[] listeners = {mGpsListener, mNetworkListener};
			
			if (locationManager == null) {
				Context ctx = RhodesService.getContext();
				locationManager = (LocationManager)ctx.getSystemService(Context.LOCATION_SERVICE);
				for (int i = 0; i < providers.length; ++i) {
					final String providerName = providers[i];
					LocationProvider provider = locationManager.getProvider(providerName);
					if (!available)
						available = provider != null && locationManager.isProviderEnabled(providerName);
					locationManager.requestLocationUpdates(providerName, 0, 0, listeners[i], Looper.getMainLooper());
				}
			}
			
			if (location != null) {
				// We've received location update
				Logger.T(TAG, "Received location update from \"" + location.getProvider() + "\" provider");
				
				// If we've received fix from GPS, stop updates from Network location manager
				// as we don't need it anymore
				if (LocationManager.GPS_PROVIDER.equals(location.getProvider())) {
					Logger.T(TAG, "Stop \"" + LocationManager.NETWORK_PROVIDER + "\" provider");
					locationManager.removeUpdates(mNetworkListener);
				}
			}
			else {
				// Ask providers for last known position and use first non-null value
				// That's why important place provider names in providers array in order
				// "most-priority-first"
				for (int i = 0; i < providers.length; ++i) {
					final String providerName = providers[i];
					location = locationManager.getLastKnownLocation(providerName);
					if (location != null) {
						Logger.T(TAG, "Use last known location from \"" + location.getProvider() + "\" provider");
						break;
					}
				}
			}
			
			boolean prevDetermined = determined;
			double prevLat = latitude;
			double prevLon = longitude;
			
			if (location != null) {
				longitude = location.getLongitude();
				latitude = location.getLatitude();
				accuracy = location.getAccuracy();
				determined = true;
			}
			else {
				determined = false;
			}

			dumpStatus(providers);
			
			if (determined != prevDetermined || latitude != prevLat || longitude != prevLon) {
				Logger.T(TAG, "Geo location information changed, notify about that");
				geoCallback();
			}
			
		} catch (Exception e) {
			determined = false;
			Logger.E(TAG, e.getMessage());
		}
	}
	
	private void dumpStatus(final String[] providers) {
		StringBuffer log = new StringBuffer();
		log.append("Status of location providers: ");
		for (int i = 0; i < providers.length; ++i) {
			if (i > 0)
				log.append(", ");
			log.append("\"");
			log.append(providers[i]);
			log.append("\" - ");
			if (locationManager.isProviderEnabled(providers[i]))
				log.append("enabled");
			else
				log.append("disabled");
		}
		Logger.T(TAG, log.toString());
		log = new StringBuffer();
		log.append("location is");
		if (!determined)
			log.append(" not");
		log.append(" determined");
		if (determined) {
			log.append(": longitude=").append(Double.toString(longitude));
			log.append(", latitude=").append(Double.toString(latitude));
			log.append(", accuracy=").append(Float.toString(accuracy));
		}
		Logger.T(TAG, log.toString());
	}
	
	public synchronized void stop() {
		// Stop thCancel thread
		timeout = TIMEOUT_STOP;
		thCancel.interrupt();
		
		if (locationManager == null)
			return;
		
		Logger.T(TAG, "Stop \"" + LocationManager.NETWORK_PROVIDER + "\" provider");
		locationManager.removeUpdates(mNetworkListener);
		Logger.T(TAG, "Stop \"" + LocationManager.GPS_PROVIDER + "\" provider");
		locationManager.removeUpdates(mGpsListener);
		
		locationManager = null;
	}

	public synchronized boolean isAvailable() {
		return available;
	}
	
	public synchronized double getLatitude() {
		return latitude;
	}

	public synchronized double getLongitude() {
		return longitude;
	}
	
	public synchronized float getAccuracy() {
		return accuracy;
	}

	public synchronized boolean isKnownPosition() {
		return determined;
	}

	public synchronized void setTimeout(int nsec) {
		timeout = nsec*1000;
		thCancel.interrupt();
	}
}
