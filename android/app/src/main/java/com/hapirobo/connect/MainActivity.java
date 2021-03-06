package com.hapirobo.connect;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.robotemi.sdk.BatteryData;
import com.robotemi.sdk.Robot;
import com.robotemi.sdk.TtsRequest;
import com.robotemi.sdk.listeners.OnBatteryStatusChangedListener;
import com.robotemi.sdk.listeners.OnDetectionStateChangedListener;
import com.robotemi.sdk.listeners.OnGoToLocationStatusChangedListener;
import com.robotemi.sdk.listeners.OnRobotReadyListener;
import com.robotemi.sdk.listeners.OnUserInteractionChangedListener;
import com.robotemi.sdk.navigation.listener.OnCurrentPositionChangedListener;
import com.robotemi.sdk.navigation.model.Position;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import org.jitsi.meet.sdk.JitsiMeetView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

public class MainActivity extends AppCompatActivity implements
//        JitsiMeetActivityInterface,
        OnRobotReadyListener,
        OnBatteryStatusChangedListener,
        OnCurrentPositionChangedListener,
        OnGoToLocationStatusChangedListener,
        OnDetectionStateChangedListener,
        OnUserInteractionChangedListener {
    private static final String TAG = "MAIN";
    public static final String VIDEO_URL = "com.hapirobo.test_media.VIDEO_URL";
    public static final String WEBVIEW_URL = "com.hapirobo.test_media.WEBVIEW_URL";

    private static Handler sHandler = new Handler();
    private static JitsiMeetView sView;
    private static Robot sRobot;
    private static String sSerialNumber;

    private MqttAndroidClient mMqttClient;
    private Runnable periodicTask = new Runnable() {
        // periodically publishes robot status to the MQTT broker.
        @SuppressLint("LogNotTimber")
        @Override
        public void run() {
            Log.i(TAG, "Publish status");
            sHandler.postDelayed(this, 3000);

            try {
                MainActivity.this.robotPublishStatus();
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    };

    //----------------------------------------------------------------------------------------------
    // ACTIVITY LIFE CYCLE METHODS
    //----------------------------------------------------------------------------------------------
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button quitButton = findViewById(R.id.Quit);
        quitButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Code here executes on main thread after user presses button
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setMessage("Quit Connect?")
                        .setCancelable(false)
                        .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                finishAffinity();
                                System.exit(0);
                            }
                        })
                        .setNegativeButton("No", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.cancel();
                            }
                        });
                AlertDialog alert = builder.create();
                alert.show();
            }
        });

        // initialize robot
        sRobot = Robot.getInstance();

//         // initialize default options for Jitsi activity
//         URL serverUrl;
//         try {
//             serverUrl = new URL("https://meet.jit.si");
//         } catch (MalformedURLException e) {
//             e.printStackTrace();
//             throw new RuntimeException("Invalid server URL!");
//         }
//         JitsiMeetConferenceOptions defaultOptions
//             = new JitsiMeetConferenceOptions.Builder()
//                 .setServerURL(serverUrl)
//                 .setWelcomePageEnabled(false)
//                 .build();
//         JitsiMeet.setDefaultConferenceOptions(defaultOptions);

    }

    @Override
    protected void onStart() {
        super.onStart();

        SharedPreferences sharedPreferences = getPreferences(MODE_PRIVATE);
        String host_name = sharedPreferences.getString(getResources().getString(R.string.host_name), "");
        if (host_name.length() >= 7) {
            EditText hostNameView = findViewById(R.id.edit_text_host_name);
            hostNameView.setText(host_name);
        }

        // add robot event listeners
        sRobot.addOnRobotReadyListener(this);
        sRobot.addOnBatteryStatusChangedListener(this);
        sRobot.addOnGoToLocationStatusChangedListener(this);
        sRobot.addOnDetectionStateChangedListener(this);
        sRobot.addOnUserInteractionChangedListener(this);
        sRobot.addOnCurrentPositionChangedListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        SharedPreferences sharedPreferences = getPreferences(MODE_PRIVATE);
        String host_name = sharedPreferences.getString(getResources().getString(R.string.host_name), "");
        if (host_name.length() >= 7) {
            EditText hostNameView = findViewById(R.id.edit_text_host_name);
            hostNameView.setText(host_name);
        }
//        // resume Jitsi activity
//        JitsiMeetActivityDelegate.onHostResume(this);
    }

    @Override
    protected void onPause() {
        super.onPause();

//        // pause Jitsi activity
//        JitsiMeetActivityDelegate.onHostPause(this);
    }

    @Override
    protected void onStop() {
        super.onStop();

        // remove robot event listeners
        sRobot.removeOnRobotReadyListener(this);
        sRobot.removeOnBatteryStatusChangedListener(this);
        sRobot.removeOnGoToLocationStatusChangedListener(this);
        sRobot.removeDetectionStateChangedListener(this);
        sRobot.removeOnUserInteractionChangedListener(this);
        sRobot.removeOnCurrentPositionChangedListener(this);
    }

    @SuppressLint("LogNotTimber")
    @Override
    protected void onDestroy() {
        super.onDestroy();

        // disconnect MQTT client from broker
        if (mMqttClient != null && mMqttClient.isConnected()) {
            try {
                Log.i(TAG, "[MQTT] Disconnecting MQTT client from broker");
                mMqttClient.unsubscribe("temi/" + sSerialNumber + "/command/#");
                mMqttClient.disconnect();
                Log.i(TAG, "[MQTT] Done");
            } catch (MqttException e) {
                e.printStackTrace();
            }
        }

//        // remove Jitsi activity
//        sView.dispose();
//        sView = null;
//        JitsiMeetActivityDelegate.onHostDestroy(this);
    }

    //----------------------------------------------------------------------------------------------
    // ROBOT EVENT LISTENERS
    //----------------------------------------------------------------------------------------------
    /**
     * Configures robot after it is ready
     * @param isReady True if robot initialized correctly; False otherwise
     */
    @SuppressLint("LogNotTimber")
    @Override
    public void onRobotReady(boolean isReady) {
        if (isReady) {
            sSerialNumber = sRobot.getSerialNumber();
            sRobot.hideTopBar(); // hides temi's top menu bar
            sRobot.toggleNavigationBillboard(true); // hides navigation billboard
            Log.i(TAG, "[ROBOT][READY]");

            // place app in temi's top bar menu
            try {
                final ActivityInfo activityInfo = getPackageManager().getActivityInfo(getComponentName(), PackageManager.GET_META_DATA);
                sRobot.onStart(activityInfo);
            } catch (PackageManager.NameNotFoundException e) {
                throw new RuntimeException(e);
            }

            // get robot's map data
//            final MapDataModel mapDataModel = Robot.getInstance().getMapData();
//            MapImage mapImage = mapDataModel.mapImage;
//            List<Integer> mapData = mapImage.getData();
//            int width = mapImage.getCols();
//            int height = mapImage.getRows();
//            Log.i(TAG, "[MAP] Width: " + width + " Height: " + height);

//            // fake map
//            int[] map = new int[] {
//                    Color.argb(255, 0, 0, 0),
//                    Color.argb(100, 0, 0, 0),
//                    Color.argb(100, 0, 0, 0),
//                    Color.argb(255, 0, 0, 0)};
//            // https://developer.android.com/reference/android/graphics/Bitmap#createBitmap(int[],%20int,%20int,%20android.graphics.Bitmap.Config)
//            Bitmap bitmap = Bitmap.createBitmap(map, 2, 2, Bitmap.Config.ARGB_8888);
//
//            final String relativePath = Environment.DIRECTORY_DOWNLOADS + File.separator;
//            final String fileName = "test.png";
//            final String mimeType = "image/*";
//
//            final ContentValues contentValues = new ContentValues();
//            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
//            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
//            contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath);
//            final ContentResolver resolver = this.getContentResolver();
//
//            OutputStream stream = null;
//            Uri uri = null;
//
//            try {
//                final Uri contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
//                uri = resolver.insert(contentUri, contentValues);
//                if (uri == null) {
//                    Log.i(TAG, "[BITMAP] Failed to create media record");
//                    return;
//                }
//
//                stream = resolver.openOutputStream(uri);
//                if (stream == null) {
//                    Log.i(TAG, "[BITMAP] Failed to get output stream");
//                }
//
//                boolean saved = bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream); // PNG is a lossless format, the compression factor (100) is ignored
//                if (!saved) {
//                    Log.i(TAG, "[BITMAP] Failed to save file");
//                }
//            } catch (IOException e) {
//                if (uri != null) {
//                    resolver.delete(uri, null, null);
//                }
//            } finally {
//                if (stream != null) {
//                    try {
//                        stream.close();
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                }
//            }
        }
    }

    /**
     * Handles battery update events
     * @param batteryData Object containing battery state
     */
    @Override
    public void onBatteryStatusChanged(@Nullable BatteryData batteryData) {
        JSONObject payload = new JSONObject();

        try {
            if (batteryData != null) {
                payload.put("percentage", batteryData.getBatteryPercentage());
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        try {
            if (batteryData != null) {
                payload.put("is_charging", batteryData.isCharging());
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        try {
            if (mMqttClient != null && mMqttClient.isConnected()) {
                MqttMessage message = new MqttMessage(payload.toString().getBytes(StandardCharsets.UTF_8));
                mMqttClient.publish("temi/" + sSerialNumber + "/status/utils/battery", message);
            }
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    /**
     * Handles go-to event updates
     * @param location Go-to location name
     * @param status Current status
     * @param descriptionId Description-identifier of the event
     * @param description Verbose description of the event
     */
    @Override
    public void onGoToLocationStatusChanged(@NotNull String location, @NotNull String status, int descriptionId, @NotNull String description) {
        JSONObject payload = new JSONObject();

        try {
            payload.put("location", location);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        try {
            payload.put("status", status);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        try {
            payload.put("description_id", descriptionId);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        try {
            payload.put("description", description);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        try {
            if (mMqttClient != null && mMqttClient.isConnected()) {
                MqttMessage message = new MqttMessage(payload.toString().getBytes(StandardCharsets.UTF_8));
                mMqttClient.publish("temi/" + sSerialNumber + "/event/waypoint/goto", message);
            }
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    /**
     * On user detection events
     * @param state User detection state
     */
    @Override
    public void onDetectionStateChanged(int state) {
        JSONObject payload = new JSONObject();

        try {
            payload.put("state", state);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        try {
            if (mMqttClient != null && mMqttClient.isConnected()) {
                MqttMessage message = new MqttMessage(payload.toString().getBytes(StandardCharsets.UTF_8));
                mMqttClient.publish("temi/" + sSerialNumber + "/event/user/detection", message);
            }
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    /**
     * On user interaction
     * @param isInteracting User interaction state
     */
    @Override
    public void onUserInteraction(boolean isInteracting) {
        JSONObject payload = new JSONObject();

        try {
            payload.put("is_interacting", isInteracting);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        try {
            if (mMqttClient != null && mMqttClient.isConnected()) {
                MqttMessage message = new MqttMessage(payload.toString().getBytes(StandardCharsets.UTF_8));
                mMqttClient.publish("temi/" + sSerialNumber + "/event/user/interaction", message);
            }
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    //----------------------------------------------------------------------------------------------
    // UI HANDLER
    //----------------------------------------------------------------------------------------------
    /**
     * Connects to MQTT broker
     * @param v View context
     */
    @SuppressLint("LogNotTimber")
    public void onConnect(View v) {
        EditText hostNameView = findViewById(R.id.edit_text_host_name);

        String hostUri;
        String host_name = hostNameView.getText().toString();
        if (host_name.length() > 0) {
            hostUri = "tcp://" + hostNameView.getText().toString().trim() + ":1883";
            SharedPreferences sharedPreferences = getPreferences(MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPreferences.edit();

            editor.putString(getResources().getString(R.string.host_name), host_name);
            editor.apply();
        } else {
            // for development purposes
            hostUri = "tcp://" + BuildConfig.MQTT_HOSTNAME.trim() + ":1883";
        }
        Log.i(TAG, hostUri);

        // hide keyboard
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(hostNameView.getWindowToken(), 0);
        }


        // initialize MQTT
        if (mMqttClient != null && mMqttClient.isConnected() && hostUri.equals(mMqttClient.getServerURI())) {
            Toast.makeText(MainActivity.this, "Already Connected", Toast.LENGTH_SHORT).show();
            Log.i(TAG, "Already connected to MQTT broker");
        } else {
            initMqtt(hostUri, "temi-" + sSerialNumber);
        }
    }

    /**
     * Initializes MQTT client
     * @param hostUri Host name / URI
     * @param clientId Identifier used to uniquely identify this client
     */
    @SuppressLint("LogNotTimber")
    private void initMqtt(String hostUri, String clientId) {
        Log.i(TAG, "Connecting to MQTT broker");

        mMqttClient = new MqttAndroidClient(getApplicationContext(), hostUri, clientId);
        mMqttClient.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                // this method is called when connection to server is lost
                Toast.makeText(MainActivity.this, "Connection Lost", Toast.LENGTH_SHORT).show();
                Log.i(TAG, "Connection Lost");
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                // called when delivery for a message has been completed, and all acknowledgements have been received
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws JSONException {
                // this method is called when a message arrives from the server
                Log.i(TAG, topic);
                Log.i(TAG, message.toString());
                JSONObject payload = new JSONObject(message.toString());
                parseMessage(topic, payload);
            }
        });

        // options that control how the client connects to a server
        // https://www.eclipse.org/paho/files/javadoc/org/eclipse/paho/client/mqttv3/MqttConnectOptions.html
        MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();

        // client and server should forget the state across reconnects.
        mqttConnectOptions.setCleanSession(true);

        // have the client automatically attempt to reconnect to the server if the connection is lost
        mqttConnectOptions.setAutomaticReconnect(true);

        // the maximum time interval the client will wait for the network connection to the MQTT server to be established [seconds]
        mqttConnectOptions.setConnectionTimeout(10);

        // set the "Last Will and Testament" (LWT) for the connection
        JSONObject payload = new JSONObject();
        mqttConnectOptions.setWill("temi/" + sSerialNumber + "/lwt", payload.toString().getBytes(StandardCharsets.UTF_8), 1, false);

        // set username and password
        mqttConnectOptions.setUserName(BuildConfig.MQTT_USERNAME);
        mqttConnectOptions.setPassword(BuildConfig.MQTT_PASSWORD.toCharArray());

        try {
            mMqttClient.connect(mqttConnectOptions, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Toast.makeText(MainActivity.this, "Successfully Connected", Toast.LENGTH_SHORT).show();
                    EditText hostNameView = findViewById(R.id.edit_text_host_name);
                    Log.i(TAG, "Successfully connected to MQTT broker");
                    try {
                        // subscribe to all command-type messages directed at this robot
                        mMqttClient.subscribe("temi/" + sSerialNumber + "/command/#", 0);
                    } catch (MqttException e) {
                        e.printStackTrace();
                    }

                    // start a background task that periodically sends robot status information
                    // to the MQTT broker
                    sHandler.post(periodicTask);
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Toast.makeText(MainActivity.this, "Failed to Connect", Toast.LENGTH_SHORT).show();
                    Log.i(TAG, "Failed to connect to MQTT broker");
                }
            });
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    /**
     * Publish robot status information
     * @throws JSONException JSON exception
     */
    public void robotPublishStatus() throws JSONException {
        JSONObject payload = new JSONObject();
        JSONArray waypointArray = new JSONArray();

        List<String> waypointList = sRobot.getLocations();

        // collect all waypoints
        for (String waypoint : waypointList) {
            waypointArray.put(waypoint);
        }

        // generate payload
        payload.put("waypoint_list", waypointArray);
        payload.put("battery_percentage", Objects.requireNonNull(sRobot.getBatteryData()).getBatteryPercentage());

        try {
            MqttMessage message = new MqttMessage(payload.toString().getBytes(StandardCharsets.UTF_8));
            mMqttClient.publish("temi/" + sSerialNumber + "/status/info", message);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    //----------------------------------------------------------------------------------------------
    // MQTT MESSAGE PARSER
    //----------------------------------------------------------------------------------------------
    /**
     * Parses MQTT messages
     * @param topic Message topic
     * @param payload Message payload
     * @throws JSONException JSON exception
     */
    @SuppressLint("LogNotTimber")
    private void parseMessage(String topic, JSONObject payload) throws JSONException {
        String[] topicTree = topic.split("/");

        String robotID = topicTree[1];
        String type = topicTree[2];
        String category = topicTree[3];

        Log.d(TAG, "Robot-ID: " + robotID);
        Log.d(TAG, "Type: " + type);
        Log.d(TAG, "Category: " + category);

        if (robotID.equals(sSerialNumber)) {
            switch (category) {
                case "waypoint":
                    parseWaypoint(topicTree[4], payload);
                    break;

                case "move":
                    parseMove(topicTree[4], payload);
                    break;

                case "call":
                    parseCall(topicTree[4], payload);
                    break;

                case "tts":
                    sRobot.speak(TtsRequest.create(payload.getString("utterance"), true));
                    break;

                case "media":
                    parseMedia(topicTree[4], payload);
                    break;

                case "app":
                    launchApp(payload.getString("package_name"));
                    break;

                default:
                    Log.i(TAG, "Invalid topic: " + topic);
                    break;
            }
        }
    }

    /**
     * Parses waypoint messages
     * @param command Command type
     * @param payload Message Payload
     * @throws JSONException JSON exception
     */
    @SuppressLint("LogNotTimber")
    private void parseWaypoint(String command, JSONObject payload) throws JSONException {
        String locationName = payload.getString("location");

        switch (command) {
            case "save":
                sRobot.saveLocation(locationName);
                break;

            case "delete":
                sRobot.deleteLocation(locationName);
                break;

            case "goto":
                // check that the location exists, then go to that location
                for (String location : sRobot.getLocations()){
                    if (location.equals(locationName.toLowerCase().trim())) {
                        sRobot.goTo(locationName.toLowerCase().trim());
                    }
                }
                break;

            default:
                Log.i(TAG, "[WAYPOINT] Unknown Locations Command");
                break;
        }
    }

    /**
     * Parses Move messages
     * @param command Command type
     * @param payload Message Payload
     * @throws JSONException JSON exception
     */
    @SuppressLint("LogNotTimber")
    private void parseMove(String command, JSONObject payload) throws JSONException {
        switch (command) {
            case "joystick":
                float x = Float.parseFloat(payload.getString("x"));
                float y = Float.parseFloat(payload.getString("y"));
                sRobot.skidJoy(x, y);
                break;

            case "forward":
                sRobot.skidJoy(+1.0F, 0.0F);
                break;

            case "backward":
                sRobot.skidJoy(-1.0F, 0.0F);
                break;

            case "turn_by":
                sRobot.turnBy(Integer.parseInt(payload.getString("angle")));
                break;

            case "tilt":
                sRobot.tiltAngle(Integer.parseInt(payload.getString("angle")));
                break;

            case "tilt_by":
                sRobot.tiltBy(Integer.parseInt(payload.getString("angle")));
                break;

            case "stop":
                sRobot.stopMovement();
                break;

            default:
                Log.i(TAG, "[MOVE] Unknown Movement Command");
                break;
        }
    }

    /**
     * Parses Call messages
     * @param command Command type
     * @param payload Message Payload
     * @throws JSONException JSON exception
     */
    @SuppressLint("LogNotTimber")
    private void parseCall(String command, JSONObject payload) throws JSONException {
        switch (command) {
            case "start":
//                startCall(this, payload.getString("room_name"));
                break;

            case "end":
//                endCall(this);
                break;

            default:
                Log.i(TAG, "[MOVE] Unknown Movement Command");
                break;
        }
    }

    /**
     * Parses Media messages
     * @param media Media type
     * @param payload Message Payload
     * @throws JSONException JSON exception
     */
    @SuppressLint("LogNotTimber")
    private void parseMedia(String media, JSONObject payload) throws JSONException {
        switch (media) {
            case "video":
                playVideo(this, payload.getString("url"));
                break;

            case"youtube":
                playYoutube(this, payload.getString("video_id"));
                break;

            case "webview":
                showWebview(this, payload.getString("url"));
                break;

            default:
                Log.i(TAG, "[MOVE] Unknown Media");
                break;
        }
    }

    //----------------------------------------------------------------------------------------------
    // JitsiMeetView Methods
    // https://jitsi.github.io/handbook/docs/dev-guide/dev-guide-android-sdk
    //----------------------------------------------------------------------------------------------
//    /**
//     * Start video-call
//     */
//    private void startCall(Context context, String roomName) {
//        // build options object for joining the conference
//        // the SDK will merge the default one we set earlier and this one when joining
//        JitsiMeetConferenceOptions options
//                = new JitsiMeetConferenceOptions.Builder()
//                .setRoom(roomName)
//                .build();
//
//        // Launch the new view with the given options
//        sView = new JitsiMeetView(context);
//        sView.join(options);
//        setContentView(sView);
//        JitsiMeetActivity.launch(context, options);
//    }
//
//    /**
//     * End video call
//     */
//    private void endCall(Activity activity) {
//        sView.leave();
//        sView.dispose();
//        sView = null;
//        JitsiMeetActivityDelegate.onHostDestroy(activity);
//
//        // set main menu
//        setContentView(R.layout.activity_main);
//    }
//
//    @Override
//    protected void onActivityResult(
//            int requestCode,
//            int resultCode,
//            Intent data) {
//        super.onActivityResult(requestCode, resultCode, data);
//        JitsiMeetActivityDelegate.onActivityResult(
//                this, requestCode, resultCode, data);
//    }
//
//    @Override
//    public void onBackPressed() {
//        JitsiMeetActivityDelegate.onBackPressed();
//    }
//
//    @Override
//    public void onNewIntent(Intent intent) {
//        super.onNewIntent(intent);
//        JitsiMeetActivityDelegate.onNewIntent(intent);
//    }
//
//    @Override
//    public void onRequestPermissionsResult(
//            final int requestCode,
//            final String[] permissions,
//            final int[] grantResults) {
//        JitsiMeetActivityDelegate.onRequestPermissionsResult(requestCode, permissions, grantResults);
//    }
//
//    @Override
//    public void requestPermissions(String[] strings, int i, PermissionListener permissionListener) {
//
//    }

    //----------------------------------------------------------------------------------------------
    // MEDIA SERVICES
    //----------------------------------------------------------------------------------------------
     /**
      * Play YouTube from URL
      * @param context Context
      * @param videoId YouTube video ID
      */
     @SuppressLint("LogNotTimber")
     public static void playYoutube(Context context, String videoId){
         Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:" + videoId));
         intent.putExtra("VIDEO_ID", videoId);
         intent.putExtra("force_fullscreen", true);

         try {
             context.startActivity(intent);
         } catch (ActivityNotFoundException e) {
             Log.i(TAG, "YouTube not found");
         }
     }

    /**
     * Play video from URL
     * @param url URL to compatible video (https://developer.android.com/guide/topics/media/media-formats)
     */
    @SuppressLint("LogNotTimber")
    public void playVideo(Context context, String url) {
        Intent intent = new Intent(this, VideoActivity.class);
        intent.putExtra(VIDEO_URL, url);

        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Log.i(TAG, "Video not found");
        }
    }

    /**
     * Show web-view from URL
     * @param url URL to display
     */
    @SuppressLint("LogNotTimber")
    public void showWebview(Context context, String url) {
        Intent intent = new Intent(this, WebviewActivity.class);
        intent.putExtra(WEBVIEW_URL, url);

        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Log.i(TAG, "URL not found");
        }
    }

    //----------------------------------------------------------------------------------------------
    // LAUNCH ANDROID APP
    //----------------------------------------------------------------------------------------------

    /**
     * Launch App using package name
     * @param packageName Name of package
     */
    @SuppressLint("LogNotTimber")
    public void launchApp(String packageName) {
        Intent mIntent = getPackageManager().getLaunchIntentForPackage(packageName);

        if (mIntent != null) {
            try {
                startActivity(mIntent);
            } catch (ActivityNotFoundException e) {
                Log.i(TAG, "App not found");
            }
        }
    }

    @Override
    public void onCurrentPositionChanged(@NotNull Position position) {

        JSONObject payload = new JSONObject();

        try {
            if (position != null) {
                payload.put("position_x", position.getX());
                payload.put("position_y", position.getY());
                payload.put("tilt", position.getTiltAngle());
                payload.put("yaw", position.getYaw());
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        try {
            if (mMqttClient != null && mMqttClient.isConnected()) {
                MqttMessage message = new MqttMessage(payload.toString().getBytes(StandardCharsets.UTF_8));
                mMqttClient.publish("temi/" + sSerialNumber + "/status/position", message);
            }
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }
}
