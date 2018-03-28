package com.ldhcjs.nearbytest;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.SwitchCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.messages.Message;
import com.google.android.gms.nearby.messages.MessageListener;
import com.google.android.gms.nearby.messages.MessagesOptions;
import com.google.android.gms.nearby.messages.NearbyPermissions;
import com.google.android.gms.nearby.messages.PublishCallback;
import com.google.android.gms.nearby.messages.PublishOptions;
import com.google.android.gms.nearby.messages.Strategy;
import com.google.android.gms.nearby.messages.SubscribeCallback;
import com.google.android.gms.nearby.messages.SubscribeOptions;
import com.lge.tonylee.nearbytest.R;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    GoogleApiClient mGoogleApiClient;

    // Views.
    private SwitchCompat mPublishSwitch;
    private SwitchCompat mSubscribeSwitch;
    private Button mPublishBtn;
    private TextView mDeviceTxt;
    private EditText mPublishEt;

    private int mNum = 0;

    private static final String TAG = MainActivity.class.getSimpleName();

    private static final int TTL_IN_SECONDS = 3 * 60; // Three minutes.

    // Key used in writing to and reading from SharedPreferences.
    private static final String KEY_UUID = "key_uuid";

    /**
     * Sets the time in seconds for a published message or a subscription to live. Set to three
     * minutes in this sample.
     */
    private static final Strategy PUB_SUB_STRATEGY = new Strategy.Builder()
            .setTtlSeconds(TTL_IN_SECONDS).build();

    /**
     * Creates a UUID and saves it to {@link SharedPreferences}. The UUID is added to the published
     * message to avoid it being undelivered due to de-duplication. See {@link DeviceMessage} for
     * details.
     */
    private static String getUUID(SharedPreferences sharedPreferences) {
        String uuid = sharedPreferences.getString(KEY_UUID, "");
        if (TextUtils.isEmpty(uuid)) {
            uuid = UUID.randomUUID().toString();
            sharedPreferences.edit().putString(KEY_UUID, uuid).apply();
        }
        return uuid;
    }

    /**
     * The {@link Message} object used to broadcast information about the device to nearby devices.
     */
    private Message mPubMessage;

    /**
     * A {@link MessageListener} for processing messages from nearby devices.
     */
    private MessageListener mMessageListener;

    /**
     * Adapter for working with messages from nearby publishers.
     */
    private ArrayAdapter<String> mNearbyDevicesArrayAdapter;


    private Message mActiveMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mSubscribeSwitch = (SwitchCompat) findViewById(R.id.subscribe_switch);
        mPublishSwitch = (SwitchCompat) findViewById(R.id.publish_switch);
        mPublishBtn = (Button) findViewById(R.id.publish_btn);
        mDeviceTxt = (TextView) findViewById(R.id.device_txt);
        mPublishEt = (EditText) findViewById(R.id.publish_et);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addApi(Nearby.MESSAGES_API, new MessagesOptions.Builder()
                            .setPermissions(NearbyPermissions.BLE)
                            .build())
                    .addConnectionCallbacks(this)
                    .enableAutoManage(this, this)
                    .build();
        }

        // Build the message that is going to be published. This contains the device name and a
        // UUID.
//        mPubMessage = DeviceMessage.newNearbyMessage(getUUID(getSharedPreferences(
//                getApplicationContext().getPackageName(), Context.MODE_PRIVATE)));

        mPubMessage = DeviceMessage.newNearbyMessage(Build.MODEL.toString() + " Nearby device ");

        mMessageListener = new MessageListener() {
            @Override
            public void onFound(final Message message) {
                // Called when a new message is found.
                if (mNum == 0)
                    mDeviceTxt.setText(new String(message.getContent()));
//                    mDeviceTxt.setText(DeviceMessage.fromNearbyMessage(message).getMessageBody());
                else
                    mNearbyDevicesArrayAdapter.add(new String(message.getContent()));

                mNum++;
            }

            @Override
            public void onLost(final Message message) {
                // Called when a message is no longer detectable nearby.
//                mNearbyDevicesArrayAdapter.remove(
//                        DeviceMessage.fromNearbyMessage(message).getMessageBody());
                mNearbyDevicesArrayAdapter.clear();
                mDeviceTxt.setText("Waiting...");
                mNum = 0;
            }
        };

        mSubscribeSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                // If GoogleApiClient is connected, perform sub actions in response to user action.
                // If it isn't connected, do nothing, and perform sub actions when it connects (see
                // onConnected()).
                if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                    if (isChecked) {
                        subscribe();
                    } else {
                        unsubscribe();
                    }
                }
            }
        });

        mPublishSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                // If GoogleApiClient is connected, perform pub actions in response to user action.
                // If it isn't connected, do nothing, and perform pub actions when it connects (see
                // onConnected()).
                if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                    if (isChecked) {
                        publish();
                    } else {
                        unpublish();
                    }
                }
            }
        });

        mPublishBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                /*
                PublishOptions options = new PublishOptions.Builder()
                        .setStrategy(PUB_SUB_STRATEGY)
                        .setCallback(new PublishCallback() {
                            @Override
                            public void onExpired() {
                                super.onExpired();
                                Log.i(TAG, "No longer publishing");
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        mPublishSwitch.setChecked(false);
                                    }
                                });
                            }
                        }).build();

                String json = "{'phonetype':'N95','cat':'WP'}";
                Message message;
                message = DeviceMessage.newNearbyMessage(getUUID(getSharedPreferences(
                        getApplicationContext().getPackageName(), Context.MODE_PRIVATE)));


                Nearby.Messages.publish(mGoogleApiClient, message, options)
                        .setResultCallback(new ResultCallback<Status>() {
                            @Override
                            public void onResult(@NonNull Status status) {
                                if (status.isSuccess()) {
                                    Log.i(TAG, "Published successfully.");
                                } else {
                                    logAndShowSnackbar("Could not publish, status = " + status);
                                    mPublishSwitch.setChecked(false);
                                }
                            }
                        });
                        */

                if(mNum > 0) {
                    publish(mPublishEt.getText().toString());
                    mPublishEt.setText("");
                }
            }
        });

        final List<String> nearbyDevicesArrayList = new ArrayList<>();
        mNearbyDevicesArrayAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1,
                nearbyDevicesArrayList);
        final ListView nearbyDevicesListView = (ListView) findViewById(
                R.id.nearby_devices_list_view);
        if (nearbyDevicesListView != null) {
            nearbyDevicesListView.setAdapter(mNearbyDevicesArrayAdapter);
        }

        buildGoogleApiClient();
    }

    /**
     * Builds {@link GoogleApiClient}, enabling automatic lifecycle management using
     * GoogleApiClient.Builder#enableAutoManage(FragmentActivity,
     * int, GoogleApiClient.OnConnectionFailedListener)}. I.e., GoogleApiClient connects in
     * {@link AppCompatActivity#onStart}, or if onStart() has already happened, it connects
     * immediately, and disconnects automatically in {@link AppCompatActivity#onStop}.
     */
    private void buildGoogleApiClient() {
        if (mGoogleApiClient != null) {
            return;
        }
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Nearby.MESSAGES_API)
                .addConnectionCallbacks(this)
                .enableAutoManage(this, this)
                .build();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.i(TAG, "GoogleApiClient connected");
        // We use the Switch buttons in the UI to track whether we were previously doing pub/sub (
        // switch buttons retain state on orientation change). Since the GoogleApiClient disconnects
        // when the activity is destroyed, foreground pubs/subs do not survive device rotation. Once
        // this activity is re-created and GoogleApiClient connects, we check the UI and pub/sub
        // again if necessary.
        if (mPublishSwitch.isChecked()) {
            publish();
        }
        if (mSubscribeSwitch.isChecked()) {
            subscribe();
        }

        mNearbyDevicesArrayAdapter.clear();
        mNum = 0;
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }


    /**
     * Subscribes to messages from nearby devices and updates the UI if the subscription either
     * fails or TTLs.
     */
    private void subscribe() {
        Log.i(TAG, "Subscribing");
        mNearbyDevicesArrayAdapter.clear();
        SubscribeOptions options = new SubscribeOptions.Builder()
                .setStrategy(PUB_SUB_STRATEGY)
                .setCallback(new SubscribeCallback() {
                    @Override
                    public void onExpired() {
                        super.onExpired();
                        Log.i(TAG, "No longer subscribing");
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mSubscribeSwitch.setChecked(false);
                            }
                        });
                    }
                }).build();

        Nearby.Messages.subscribe(mGoogleApiClient, mMessageListener, options)
                .setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(@NonNull Status status) {
                        if (status.isSuccess()) {
                            Log.i(TAG, "Subscribed successfully.");
                        } else {
                            logAndShowSnackbar("Could not subscribe, status = " + status);
                            mSubscribeSwitch.setChecked(false);
                        }
                    }
                });
    }

    /**
     * Publishes a message to nearby devices and updates the UI if the publication either fails or
     * TTLs.
     */
    private void publish() {

        Log.i(TAG, "Publishing");
        PublishOptions options = new PublishOptions.Builder()
                .setStrategy(PUB_SUB_STRATEGY)
                .setCallback(new PublishCallback() {
                    @Override
                    public void onExpired() {
                        super.onExpired();
                        Log.i(TAG, "No longer publishing");
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mPublishSwitch.setChecked(false);
                            }
                        });
                    }
                }).build();

        Nearby.Messages.publish(mGoogleApiClient, mPubMessage, options)
                .setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(@NonNull Status status) {
                        if (status.isSuccess()) {
                            Log.i(TAG, "Published successfully.");
                        } else {
                            logAndShowSnackbar("Could not publish, status = " + status);
                            mPublishSwitch.setChecked(false);
                        }
                    }
                });
    }

    private void publish(String message) {
        Log.i(TAG, "Publishing message: " + message);
        mActiveMessage = new Message(message.getBytes());
        Nearby.Messages.publish(mGoogleApiClient, mActiveMessage);
    }

    /**
     * Stops subscribing to messages from nearby devices.
     */
    private void unsubscribe() {
        Log.i(TAG, "Unsubscribing.");
        Nearby.Messages.unsubscribe(mGoogleApiClient, mMessageListener);
    }

    /**
     * Stops publishing message to nearby devices.
     */
    private void unpublish() {
        Log.i(TAG, "Unpublishing.");
        Nearby.Messages.unpublish(mGoogleApiClient, mPubMessage);
        Nearby.Messages.unpublish(mGoogleApiClient, mActiveMessage);
    }

    /**
     * Logs a message and shows a {@link Snackbar} using {@code text};
     *
     * @param text The text used in the Log message and the SnackBar.
     */
    private void logAndShowSnackbar(final String text) {
        Log.w(TAG, text);
        View container = findViewById(R.id.activity_main_container);
        if (container != null) {
            Snackbar.make(container, text, Snackbar.LENGTH_LONG).show();
        }
    }


}
