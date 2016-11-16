// Copyright 2008 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.android.stardroid;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.android.stardroid.control.AstronomerModel;
import com.google.android.stardroid.control.AstronomerModelImpl;
import com.google.android.stardroid.control.ZeroMagneticDeclinationCalculator;
import com.google.android.stardroid.layers.EclipticLayer;
import com.google.android.stardroid.layers.GridLayer;
import com.google.android.stardroid.layers.HorizonLayer;
import com.google.android.stardroid.layers.LayerManager;
import com.google.android.stardroid.layers.MeteorShowerLayer;
import com.google.android.stardroid.layers.ModConstellationsLayer;
import com.google.android.stardroid.layers.ModMessierLayer;
import com.google.android.stardroid.layers.ModPlanetsLayer;
import com.google.android.stardroid.layers.ModStarsLayer;
import com.google.android.stardroid.layers.NewConstellationsLayer;
import com.google.android.stardroid.layers.NewMessierLayer;
import com.google.android.stardroid.layers.NewStarsLayer;
import com.google.android.stardroid.layers.PlanetsLayer;
import com.google.android.stardroid.layers.SkyGradientLayer;
import com.google.android.stardroid.util.Analytics;
import com.google.android.stardroid.util.Analytics.Slice;
import com.google.android.stardroid.util.MiscUtil;
import com.google.android.stardroid.util.PreferenceChangeAnalyticsTracker;

import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * The main Stardroid Application class.
 *
 * @author John Taylor
 */
public class StardroidApplication extends Application {

  public static boolean loadModified = false;
  private static final String TAG = MiscUtil.getTag(StardroidApplication.class);
  private static final String PREVIOUS_APP_VERSION_PREF = "previous_app_version";
  private static final String NONE = "Clean install";
  private static final String UNKNOWN = "Unknown previous version";
  // The Application class is a singleton, so treat it as such, with static
  // fields.  This is necessary so that the content provider can access the
  // things it needs; there seems to be no easy way for a ContentProvider
  // to access its Application object.
  private static AstronomerModel model;
  private static LayerManager layerManager;
  private static ExecutorService backgroundExecutor;


  private static Context context;

  // We need to maintain references to this object to keep it from
  // getting gc'd.
  private final PreferenceChangeAnalyticsTracker preferenceChangeAnalyticsTracker =
      new PreferenceChangeAnalyticsTracker(Analytics.getInstance(this));


  @Override
  public void onCreate() {
    Log.d(TAG, "StardroidApplication: onCreate");
    super.onCreate();
    Log.d("debug","oncreate ran");

    Log.i(TAG, "OS Version: " + android.os.Build.VERSION.RELEASE
            + "(" + android.os.Build.VERSION.SDK_INT + ")");
    String versionName = getVersionName();
    Log.i(TAG, "Sky Map version " + versionName + " build " + getVersion());
    backgroundExecutor = new ScheduledThreadPoolExecutor(1);
    // This populates the default values from the preferences XML file. See
    // {@link DefaultValues} for more details.
    PreferenceManager.setDefaultValues(this, R.xml.preference_screen, false);

    AssetManager assetManager = this.getAssets();
    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
    Resources resources = this.getResources();
    // Start the LayerManager initializing
    getLayerManager(assetManager, preferences, resources, this);

    setUpAnalytics(versionName, preferences);

    performFeatureCheck();

    Log.d(TAG, "StardroidApplication: -onCreate");
  }

  public static Context getAppContext() {
    return StardroidApplication.context;
  }

  private void setUpAnalytics(String versionName, SharedPreferences preferences) {
    Analytics analytics = Analytics.getInstance(this);
    analytics.setProductVersion(versionName);
    analytics.setCustomVar(Slice.ANDROID_OS, Integer.toString(Build.VERSION.SDK_INT));
    analytics.setCustomVar(Slice.SKYMAP_VERSION, versionName);
    analytics.setCustomVar(Slice.DEVICE_NAME, android.os.Build.MODEL);
    analytics.setEnabled(preferences.getBoolean(Analytics.PREF_KEY, true));
    analytics.trackPageView(Analytics.APPLICATION_CREATE);

    String previousVersion = preferences.getString(PREVIOUS_APP_VERSION_PREF, NONE);
    if (previousVersion.equals(NONE)) {
      // It's possible a previous version exists, it's just that it wasn't a recent enough
      // version to have set PREVIOUS_APP_VERSION_PREF.  If so, we should see that the TOS
      // have been accepted.
      String oldPreviousVersionKey = "read_tos";
      if (preferences.contains(oldPreviousVersionKey)) {
        previousVersion = UNKNOWN;
      }
    }
    preferences.edit().putString(PREVIOUS_APP_VERSION_PREF, versionName).commit();
    if (!previousVersion.equals(versionName)) {
      // It's either an upgrade or a new installation
      Log.d(TAG, "New installation: version " + versionName);
      analytics.trackEvent(Analytics.INSTALL_CATEGORY, Analytics.INSTALL_EVENT + versionName,
          Analytics.PREVIOUS_VERSION + previousVersion, 1);
    }

    // It will be interesting to see *when* people use Sky Map.
    analytics.trackEvent(
        Analytics.GENERAL_CATEGORY, Analytics.START_HOUR,
        Integer.toString(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) + 'h', 0);

    preferences.registerOnSharedPreferenceChangeListener(preferenceChangeAnalyticsTracker);
  }

  @Override
  public void onTerminate() {
    super.onTerminate();
    Analytics.getInstance(this).setEnabled(false);
  }

  /**
   * Returns the version string for Sky Map.
   */
  public String getVersionName() {
    PackageManager packageManager = getPackageManager();
    try {
      PackageInfo info = packageManager.getPackageInfo(this.getPackageName(), 0);
      return info.versionName;
    } catch (NameNotFoundException e) {
      Log.e(TAG, "Unable to obtain package info");
      return "Unknown";
    }
  }

  /**
   * Returns the build number for Sky Map.
   */
  public int getVersion() {
    PackageManager packageManager = getPackageManager();
    try {
      PackageInfo info = packageManager.getPackageInfo(this.getPackageName(), 0);
      return info.versionCode;
    } catch (NameNotFoundException e) {
      Log.e(TAG, "Unable to obtain package info");
      return -1;
    }
  }

  /**
   * Get the catalog.
   * This should return relatively quickly, with the catalogs initializing
   * themselves on background threads.
   */
  public static synchronized LayerManager getLayerManager(AssetManager assetManager,
                                                          SharedPreferences preferences,
                                                          Resources resources,
                                                          Context context) {
    StardroidApplication.context = context;
    if (layerManager == null) {
      Log.i(TAG, "Initializing LayerManager");
      layerManager = new LayerManager(preferences, getModel());

      layerManager.addLayer(new NewStarsLayer(assetManager, resources));

      layerManager.addLayer(new ModStarsLayer(assetManager, resources));
      layerManager.addLayer(new ModMessierLayer(assetManager, resources));
      layerManager.addLayer(new ModConstellationsLayer(assetManager, resources));
      layerManager.addLayer(new ModPlanetsLayer(getModel(), resources, preferences));

      layerManager.addLayer(new NewMessierLayer(assetManager, resources));
      layerManager.addLayer(new NewConstellationsLayer(assetManager, resources));
      layerManager.addLayer(new PlanetsLayer(getModel(), resources, preferences));
      layerManager.addLayer(new MeteorShowerLayer(getModel(), resources));
      layerManager.addLayer(new GridLayer(resources, 24, 19));
      layerManager.addLayer(new HorizonLayer(getModel(), resources));
      layerManager.addLayer(new EclipticLayer(resources));
      layerManager.addLayer(new SkyGradientLayer(getModel(), resources));
      //layerManager.addLayer(new IssLayer(resources, getModel()));

      Log.d("seq","init layer now");
      layerManager.initialize();
    } else {
      Log.i(TAG, "LayerManager already initialized.");
    }
    return layerManager;
  }

  /**
   * Return the model.
   */
  public static synchronized AstronomerModel getModel() {
    if (model == null) {
      model = new AstronomerModelImpl(new ZeroMagneticDeclinationCalculator());
    }
    return model;
  }

  /**
   * Schedules this runnable to run as soon as possible on a background
   * thread.
   *
   * @param runnable
   */
  // TODO(johntaylor): the idea, and I'm not sure yet whether it's a good one,
  // is to centralize the management of background threads so we don't have
  // them scattered all over the app.  We can then control how many threads
  // are spawned, perhaps having a VIP service for extra important runnables
  // that we'd prefer not to queue, as well as providing convenience functions
  // to facilitate callbacks on the UI thread.
  public static void runInBackground(Runnable runnable) {
    backgroundExecutor.submit(runnable);
  }

  /**
   * Check what features are available to this phone and report back to analytics
   * so we can judge when to add/drop support.
   */
  private void performFeatureCheck() {
    Analytics analytics = Analytics.getInstance(this);
    SensorManager sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
    if (sensorManager == null) {
      Log.e(TAG, "No sensor manager");
      analytics.trackEvent(
          Analytics.SENSOR_CATEGORY, Analytics.SENSOR_AVAILABILITY, "No Sensor Manager", 0);
    }
    // Minimum requirements
    if (sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
      if (sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null) {
        Log.i(TAG, "Minimal sensors available");
        analytics.trackEvent(
            Analytics.SENSOR_CATEGORY, Analytics.SENSOR_AVAILABILITY, "Minimal Sensors: Yes", 1);
      } else {
        Log.e(TAG, "No magnetic field sensor");
        analytics.trackEvent(
            Analytics.SENSOR_CATEGORY, Analytics.SENSOR_AVAILABILITY, "No Mag Field Sensor", 0);
      }
    } else {
      if (sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null) {
        Log.e(TAG, "No accelerometer");
        analytics.trackEvent(
            Analytics.SENSOR_CATEGORY, Analytics.SENSOR_AVAILABILITY, "No Accel Sensor", 0);
      } else {
        Log.e(TAG, "No magnetic field sensor or accelerometer");
        analytics.trackEvent(
            Analytics.SENSOR_CATEGORY, Analytics.SENSOR_AVAILABILITY, "No Mag Field/Accel Sensors", 0);
      }
    }

    // Do we at least have defaults for the main ones?
    int[] importantSensorTypes = {Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE,
        Sensor.TYPE_MAGNETIC_FIELD, Sensor.TYPE_LIGHT, Sensor.TYPE_ROTATION_VECTOR,
        Sensor.TYPE_ORIENTATION};

    for (int sensorType : importantSensorTypes) {
      if (sensorManager.getDefaultSensor(sensorType) == null) {
        Log.i(TAG, "No sensor of type " + sensorType);
        analytics.trackEvent(
            Analytics.SENSOR_CATEGORY, Analytics.SENSOR_TYPE + sensorType, "Sensor Absent", 0);
      } else {
        Log.i(TAG, "Sensor present of type " + sensorType);
        analytics.trackEvent(
            Analytics.SENSOR_CATEGORY, Analytics.SENSOR_TYPE + sensorType, "Sensor Present", 1);
      }
    }

    // Lastly a dump of all the sensors.
    Log.d(TAG, "All sensors:");
    List<Sensor> allSensors = sensorManager.getSensorList(Sensor.TYPE_ALL);
    Set<String> sensorTypes = new HashSet<>();
    for (Sensor sensor : allSensors) {
      Log.i(TAG, sensor.getName());
      sensorTypes.add(Analytics.getSafeNameForSensor(sensor));
    }
    Log.d(TAG, "All sensors summary:");
    for (String sensorType : sensorTypes) {
      Log.i(TAG, sensorType);
      analytics.trackEvent(
          Analytics.SENSOR_CATEGORY, Analytics.SENSOR_NAME, sensorType, 1);
    }
  }
}
