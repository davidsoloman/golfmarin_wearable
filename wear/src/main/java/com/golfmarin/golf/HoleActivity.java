package com.golfmarin.golf;

/*
        Copyright (C) 2015  Michael Hahn
        This program is free software: you can redistribute it and/or modify
        it under the terms of the GNU General Public License as published by
        Free Software Foundation, either version 3 of the License, or
        (at your option) any later version.

        This program is distributed in the hope that it will be useful,
        but WITHOUT ANY WARRANTY; without even the implied warranty of
        MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
        GNU General Public License for more details.

        You should have received a copy of the GNU General Public License
        along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

import android.app.Activity;
import android.support.wearable.activity.WearableActivity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;//
import android.content.IntentSender;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.GestureDetectorCompat;
import android.support.wearable.view.DismissOverlayView;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;
import java.util.ArrayList;
//import static android.util.FloatMath.cos;
//import static android.util.FloatMath.sin;
//import static android.util.FloatMath.sqrt;connectionResult

/*
  This activity initializes with the closest course
  then displays distances from current location to the hole placements.
*/

public class HoleActivity extends WearableActivity implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener{

    private static final String TAG = "WearHoleActivity";
    // Wearable data layer constants
    public static final String WEARABLE_MESSAGE_HOLE_PATH = "/wearable_message/hole";
    public static final String WEARABLE_MESSAGE_COURSE_PATH = "/wearable_message/course";
    public static final String WEARABLE_DATA_PATH = "/wearable_data";
    public static final String WEARABLE_START_PATH = "/wearable_start";

    private static final String KEY_IN_RESOLUTION = "is_in_resolution";

    /**
     * Request code for auto Google Play Services error resolution.
     */
    protected static final int REQUEST_CODE_RESOLUTION = 1;

    private TextView holeView;
    private TextView backView;
    private TextView middleView;
    private TextView frontView;
    private ProgressBar progressView;

    private DismissOverlayView dismissOverlayView;

    private GestureDetectorCompat gestureDetector;

    private GoogleApiClient googleClient;



    // Sensor globals
    private boolean mIsInResolution;
    /*
    private SensorManager senSensorManager;
    private Sensor senAccelerometer;

    // Constant to convert nanoseconds to seconds.
    private static final float NS2S = 1.0f / 1000000000.0f;
    private final float[] deltaRotationVector = new float[4];
    */

    private float timestamp;

    /*
     * Local broadcasts (future for data layer)
     */
    MessageReceiver messageReceiver;
    IntentFilter messageFilter;

    /*
    * Golf course variables
     */
    private ArrayList<Course> allCourses;
    private Course currentCourse;
    private ArrayList<Hole> allHoles;
    private Integer currentHoleNum;
    private Hole currentHole;
    private Boolean startup = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_hole);
        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                holeView = (TextView) stub.findViewById(R.id.hole);
                backView = (TextView) stub.findViewById(R.id.back);
                middleView = (TextView) stub.findViewById(R.id.middle);
                frontView = (TextView) stub.findViewById(R.id.front);
                progressView = (ProgressBar) stub.findViewById(R.id.progress_bar);

//                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        });

        // Set startup state, which triggers a new search for the closest course.
        startup = true;

        // Enable the ambient mode
        setAmbientEnabled();

        // Initialize data model containing all golf courses
        DataModel dm = new DataModel(this);
        allCourses = dm.getCourses();

        // Check for an extra that specifies the golf course name
        // If present, initialize currentCourse object and disable course selection
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            String courseName = extras.getString("course");
            // Find the course object and verify that it has holes
            for (Course course : allCourses) {
                if (course.name.equals(courseName) && (course.holeList.size() >0)) {
                    currentCourse = course;
                    allHoles = currentCourse.holeList;
                    currentHoleNum = 1;
                    currentHole = allHoles.get(0);
                    Log.i(TAG, "Set currentCourse using a notification extra: " + currentCourse.name);
                    Log.i(TAG, "ProgressView: " + progressView);
                //    progressView.setVisibility(View.GONE);
                //    holeView.setText("Hole");
                    startup = false;
                    break;
                }
            }
        }

        // Setup a local broadcast receiver
        messageFilter = new IntentFilter(Intent.ACTION_SEND);
        messageReceiver = new MessageReceiver();

        // Set up gesture detector
        gestureDetector = new GestureDetectorCompat(this, new MyGestureListener());

        // Set up dismiss overlay view
        dismissOverlayView = (DismissOverlayView) findViewById(R.id.dismiss_overlay);
        dismissOverlayView.setIntroText(R.string.dismiss_intro);
        dismissOverlayView.showIntroIfNecessary();
/*
        // Set up sensor listener
        senSensorM*anager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        senAccelerometer = senSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        senSensorManager.registerListener(this, senAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
*/
        // Turn off auto brightness
        Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
    }

    // *************************
    // App lifecycle callbacks
    // **************************

    // Connect to location services and data layer when the Activity starts
    @Override
    protected void onStart() {
        super.onStart();

        // Create a Google API client for the data layer and location services
        if (googleClient == null) {
            googleClient = new GoogleApiClient.Builder(this)
                    .addApi(Wearable.API)
                    .addApi(LocationServices.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
        }
        // Connect the Google API client when the Activity becomes visible
        googleClient.connect();

    }

    // Disconnect from Google Play Services when the Activity is no longer visible
    @Override
    protected void onStop() {
        if ((googleClient != null) && googleClient.isConnected()) {
            googleClient.disconnect();
        }
        // Must be done to manage selection of the current golf course
        startup = true;
        super.onStop();
    }

    @Override
    protected void onPause() {
        // Unregister listeners
        LocalBroadcastManager.getInstance(this).unregisterReceiver(messageReceiver);
     //   senSensorManager.unregisterListener(this);
        super.onPause();
    }

    @Override
    protected void onResume() {
        // Register a local broadcast receiver, defined below.
        super.onResume();
        LocalBroadcastManager.getInstance(this).registerReceiver(messageReceiver, messageFilter);

        // Register the sensor manager
     //   senSensorManager.registerListener(this, senAccelerometer, SensorManager.SENSOR_DELAY_UI);
    }

    /**
     * Save the resolution state.
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_IN_RESOLUTION, mIsInResolution);
    }

    // ******************************
    // Google Connection callbacks
    // ******************************
    @Override
    public void onConnected(Bundle connectionHint) {

        // Register for location services

        // Create the LocationRequest object
        LocationRequest locationRequest = LocationRequest.create();
        // Use high accuracy
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        // Set the update interval to 2 seconds
        locationRequest.setInterval(2);
        // Set the fastest update interval to 2 seconds
        locationRequest.setFastestInterval(2);
        // Set the minimum displacement
        locationRequest.setSmallestDisplacement(2);

        // Register listener using the LocationRequest object
        LocationServices.FusedLocationApi.requestLocationUpdates(googleClient, locationRequest, this);

        // Get the current location and try to invoke the golf course setup procedure
        onLocationChanged(LocationServices.FusedLocationApi.getLastLocation(googleClient));

    }

    @Override
    public void onConnectionSuspended(int cause) {
        retryConnecting();
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

        if (!connectionResult.hasResolution()) {
            // Show a localized error dialog.
            GooglePlayServicesUtil.getErrorDialog(
                    connectionResult.getErrorCode(), this, 0, new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialog) {
                            retryConnecting();
                        }
                    }).show();
            return;
        }
        // If there is an existing resolution error being displayed or a resolution
        // activity has started before, do nothing and wait for resolution
        // progress to be completed.
        if (mIsInResolution) {
            return;
        }
        mIsInResolution = true;
        try {
            connectionResult.startResolutionForResult(this, REQUEST_CODE_RESOLUTION);
        } catch (IntentSender.SendIntentException e) {
            retryConnecting();
        }

    }

    /**
     * Handle Google Play Services resolution callbacks.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_CODE_RESOLUTION:
                retryConnecting();
                break;
        }
    }

    private void retryConnecting() {
        mIsInResolution = false;
        if (!googleClient.isConnecting()) {
            googleClient.connect();
        }
    }

    /**
     * Location service callback
    */

    @Override
    public void onLocationChanged(Location location) {

        Log.i(TAG, "Current location accuracy: " +location.getAccuracy());

        // Wait for a usable location
        if ((location != null) &&
                (location.getAccuracy() < 25.0) &&
                (location.getAccuracy() > 0.0)) {
            // Find closest course, if just starting up
            if (startup) {
                if (getCurrentCourse(location).size() > 0) {
                    currentCourse = (getCurrentCourse(location)).get(0);
                    startup = false;
                    Log.i(TAG, "Current course: " + currentCourse.name);

                    allHoles = currentCourse.holeList;
                    currentHoleNum = 1;
                    currentHole = allHoles.get(0);
                //    progressView.setVisibility(View.GONE);
                //    holeView.setText("Hole");
                }
            }
        }
        // Refresh the distances to hole placements§
        if (!startup) updateDisplay(location);

    }

    // Local broadcast receiver callback to receive messages
    // forwarded by the data layer listener service.
    public class MessageReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {

            String path = intent.getStringExtra("path");
            String message = intent.getStringExtra("message");

            if (path.equals(WEARABLE_MESSAGE_HOLE_PATH) && !startup) {
                // The user swiped the wearable left or right
                // The message is the resultant hole number

                currentHoleNum = Integer.parseInt(message);
                currentHole = allHoles.get(currentHoleNum - 1);
                updateDisplay(LocationServices.FusedLocationApi.getLastLocation(googleClient));
            }
        }
    }

    //************************************
    // Gesture handling
    // Swipe increments or decrements hole number
    // Double tap changes to alternate course
    // Long press to dismiss app
    //************************************

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        this.gestureDetector.onTouchEvent(event);
        return gestureDetector.onTouchEvent(event)||super.onTouchEvent(event);
    }

    class MyGestureListener extends GestureDetector.SimpleOnGestureListener {
        private static final String DEBUG_TAG = "Gestures";

        @Override
        public boolean onDown(MotionEvent event) {

            return true;
        }

        // Display dismiss overlay to close this app

        @Override
        public void onLongPress(MotionEvent event) {

            dismissOverlayView.show();
        }

        @Override
        public boolean onDoubleTap(MotionEvent event) {

            // Change to alternate course at the same golf club (future)

            return true;
        }

        // Move to next or previous hole

        @Override
        public boolean onFling(MotionEvent event1, MotionEvent event2,
                               float velocityX, float velocityY) {

        if(!startup){
            if (event1.getX() < event2.getX()) {
                // Swipe left (minus)
                if (currentHoleNum > 1) {
                    currentHoleNum--;
                    currentHole = allHoles.get(currentHoleNum - 1);
                }
            } else {
                // Swipe right (plus)
                if (currentHoleNum < allHoles.size()) {
                    currentHoleNum++;
                    currentHole = allHoles.get(currentHoleNum - 1);
                }
            }
            updateDisplay(LocationServices.FusedLocationApi.getLastLocation(googleClient));

            // Send hole number to data layer
            new SendToDataLayerThread(WEARABLE_MESSAGE_HOLE_PATH,currentHoleNum.toString()).start();
        }
            return true;
        }
    }


    class SendToDataLayerThread extends Thread {
        String path;
        String message;

        // Constructor to send a message to the data layer
        SendToDataLayerThread(String p, String msg) {
            path = p;
            message = msg;
        }

        public void run() {
            NodeApi.GetConnectedNodesResult nodes = Wearable.NodeApi.getConnectedNodes(googleClient).await();
            for (Node node : nodes.getNodes()) {
                MessageApi.SendMessageResult result = Wearable.MessageApi.sendMessage(googleClient, node.getId(), path, message.getBytes()).await();
                if (result.getStatus().isSuccess()) {
                    Log.i(TAG, "Sent data layer: " + message);

                } else {
                    // Log an error

                }
            }
        }
    }
    /******************************
     * Ambient mode callbacks
     * This replaces the earlier motion detection
     *******************************/

    @Override
    public void onEnterAmbient(Bundle ambientDetails) {
        super.onEnterAmbient(ambientDetails);
        Log.v(TAG,"Entered Ambient.");
         /*
         WallpaperManager wallpaperManager = WallpaperManager.getInstance(getApplicationContext());
         try {
             wallpaperManager.setResource(R.raw.green_outline);
             Log.v(TAG,"Wallpaper set.");
         }
         catch (IOException e) {
             Log.v(TAG, "Wallpaper manger failed");
         }
         */
        // Disable antialias for devices with low-bit ambient
        holeView.getPaint().setAntiAlias(false);
        frontView.getPaint().setAntiAlias(false);
        middleView.getPaint().setAntiAlias(false);
        backView.getPaint().setAntiAlias(false);
    }

    @Override
    public void onExitAmbient() {
        Log.v(TAG, "ExitedAmbient");
        // Restore display
        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        stub.invalidate();
/*
          WallpaperManager wallpaperManager = WallpaperManager.getInstance(getApplicationContext());
          try {
              wallpaperManager.setResource(R.raw.greenback);
              Log.v(TAG,"Wallpaper set.");
          }
          catch (IOException e) {
              Log.v(TAG, "Wallpaper manger failed");
          }

*/
        // Renable antialias for normal display
        holeView.getPaint().setAntiAlias(true);
        frontView.getPaint().setAntiAlias(true);
        middleView.getPaint().setAntiAlias(true);
        backView.getPaint().setAntiAlias(true);

        super.onExitAmbient();
    }

    @Override
    public void onUpdateAmbient() {
        // Update hole distances using current location
        Location currentLocation = LocationServices.FusedLocationApi.getLastLocation(googleClient);
        Log.v(TAG, "Distances updating using" + currentLocation.toString());
        updateDisplay(currentLocation);
    }

    /*****************************
     * Course and hole methods
     ******************************/

    /**
     * Scans through list of courses to find the one close to the current location.
     *
     * @param location current gps location
     * @return Courses closest to current location
     */
    private ArrayList<Course> getCurrentCourse(Location location) {

        // Search for course closest to current location

        ArrayList<Course> bestCourses = new ArrayList<Course>();
        float bestYards = 20000;
        float conv = (float) 1.0936133;

        for (Course course : allCourses) {

            // Not all courses have hole locations, skip those
            if (course.holeList != null) {
                float yards = location.distanceTo(course.getLocation()) * conv;
                if (yards < bestYards) {
                    bestYards = yards;
                    bestCourses.clear();
                    bestCourses.add(course);

                }
                // Some clubs have multiple courses
                else if (yards == bestYards) {
                    bestCourses.add(course);

                }
            }

/*
            if (course.name.equals("Golf Course Name")) {
                bestCourses.clear();
                bestCourses.add(course);
            }
*/
    //Verify that a course with holes was identified.
        }
        if(bestCourses.size() >0 ) startup =false;
        return bestCourses;
    }

    /**
     * Calculates distances to placements and updates the UI
     *
     * @param location Current watch location
     */

    private void updateDisplay(Location location) {
        // float accuracy;
        // accuracy = location.getAccuracy();
        // Only use whe there is a current hole defined.
        if (currentHole != null) {

            float conv = (float) 1.0936133;
            float yards = location.distanceTo(currentHole.getLocation("front")) * conv;
            String front = String.valueOf((int) yards);
            frontView.setText(front);

            yards = location.distanceTo(currentHole.getLocation("middle")) * conv;
            String middle = String.valueOf((int) yards);
            middleView.setText(middle);

            yards = location.distanceTo(currentHole.getLocation("back")) * conv;
            String back = String.valueOf((int) yards);
            backView.setText(back);

            // Keep the hole number display current
            String holeViewText = ("Hole " + currentHole.holeNum);
            holeView.setText(holeViewText);

            // Make sure startup elements are normal
            progressView.setVisibility(View.GONE);
        }
    }

    /**
     * Handle Sensor callbacks
     * Used to dim the display when not in view (power saving)
     */

    private Handler displayHandler = new Handler();
/*
    @Override
    public final void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Do something here if sensor accuracy changes.
    }

    @Override
    public final void onSensorChanged(SensorEvent event) {

        // This timestep's delta rotation to be multiplied by the current rotation
        // after computing it from the gyro sample data.

        if (timestamp != 0) {
            final float dT = (event.timestamp - timestamp) * NS2S;
            // Axis of the rotation sample, not normalized yet.
            float axisX = event.values[0];
            float axisY = event.values[1];
            float axisZ = event.values[2];

            // Calculate the angular speed of the sample
            // float omegaMagnitude = sqrt(axisX * axisX + axisY * axisY + axisZ * axisZ);

            // Normalize the rotation vector if it's big enough to get the axis
            // (that is, EPSILON should represent your maximum allowable margin of error)
            //   if (omegaMagnitude > EPSILON) {
            // axisX /= omegaMagnitude;
            // axisY /= omegaMagnitude;
            // axisZ /= omegaMagnitude;

            //   }
            if ((axisZ < .7) ) {
            // dim brightness
                Settings.System.putString(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, "0");
                //WindowManager.LayoutParams lp = this.getWindow().getAttributes();
                //    lp.screenBrightness =0.0f;
                //    lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF;
                requireRotate = 0;

            } else if ((axisZ > .9) && (requireRotate == 0))  {
            // restore brightness
                Settings.System.putString(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, "255");

                // Start a handler to implement the maximum time for normal brightness
                displayHandler.postDelayed(displayTimeout, 4000);
                requireRotate = 1;
            }
        }
        timestamp = event.timestamp;
    }
*/

    // Dim the display after a timeout
    // Set flag that requires wrist rotation to restore brightness

    int requireRotate = 0;

    protected Runnable displayTimeout = new Runnable() {
        @Override
        public void run() {
            Settings.System.putString(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, "0");
            requireRotate = 1;
        }
    };
}


