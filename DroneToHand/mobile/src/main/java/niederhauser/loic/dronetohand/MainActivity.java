package niederhauser.loic.dronetohand;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.IBinder;
import android.support.design.widget.Snackbar;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.widget.ContentLoadingProgressBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.ViewStub;
import android.widget.AdapterView;
import android.widget.ListView;

import com.parrot.arsdk.ARSDK;
import com.parrot.arsdk.ardiscovery.ARDiscoveryDeviceService;
import com.parrot.arsdk.ardiscovery.ARDiscoveryService;
import com.parrot.arsdk.ardiscovery.receivers.ARDiscoveryServicesDevicesListUpdatedReceiver;
import com.parrot.arsdk.ardiscovery.receivers.ARDiscoveryServicesDevicesListUpdatedReceiverDelegate;

import java.util.List;

public class MainActivity extends AppCompatActivity
        implements ARDiscoveryServicesDevicesListUpdatedReceiverDelegate {


    public static final String EXTRA_DEVICE_SERVICE = "DeviceService";
    private static final String TAG = "MainActivity";

    private ARDiscoveryService mArdiscoveryService;
    private ServiceConnection mArdiscoveryServiceConnection;
    private ARDiscoveryServicesDevicesListUpdatedReceiver mArdiscoveryServicesDevicesListUpdatedReceiver;

    private DeviceListAdapter mAdapter;
    private ContentLoadingProgressBar mProgress;
    private View mEmptyView;
    private WifiManager mWifiManager;
    private WifiStateChangedReceiver mWifiStateReceiver;
    private ARDiscoveryDeviceService mSelectedDrone;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ARSDK.loadSDKLibs();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        mWifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        mWifiStateReceiver = new WifiStateChangedReceiver();

        ListView deviceListView = (ListView) findViewById(android.R.id.list);
        mAdapter = new DeviceListAdapter(this);
        deviceListView.setAdapter(mAdapter);

        mProgress = findViewById(android.R.id.progress);
        mProgress.show();
        deviceListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> view, View item, int pos, long id) {
                mSelectedDrone = mAdapter.getItem(pos);
                if(mSelectedDrone == null) {
                    Log.e(TAG,"ERROR: never got the actual device");
                }
                Intent mIntent = new Intent(MainActivity.this, PilotActivity.class);
                mIntent.putExtra("DRONE",mSelectedDrone);
                startActivity(mIntent);
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (!mWifiManager.isWifiEnabled()) {
            showWifiDisabledMessage();
        }

        registerReceiver(mWifiStateReceiver,
                new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION));
        initDiscoveryService();
        registerReceivers();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mProgress.hide();
        unregisterReceiver(mWifiStateReceiver);
        closeServices();
        unregisterReceivers();
    }


    // Start Discovery service
    private void initDiscoveryService() {
        // Initialize connaction
        if (mArdiscoveryServiceConnection == null) {
            mArdiscoveryServiceConnection = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    mArdiscoveryService = ((ARDiscoveryService.LocalBinder) service).getService();
                    Log.d(TAG, "onServiceConnected: discovery service is now bound.");
                    startDiscovery();
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    mArdiscoveryService = null;
                }
            };
        }

        if (mArdiscoveryService == null) {
            // Create and bind if doesn't exists
            Intent discoveryService = new Intent(getApplicationContext(), ARDiscoveryService.class);
            getApplicationContext().bindService(discoveryService, mArdiscoveryServiceConnection, Context.BIND_AUTO_CREATE);
        } else {
            // Start reasearch if exists
            startDiscovery();
        }
    }

    // Look for devices
    private void startDiscovery() {
        Log.d(TAG, "Starting discovery...");
        if (mArdiscoveryService != null) {
            mArdiscoveryService.start();
            mProgress.show();
        }
    }

    // CB for when research is over
    @Override
    public void onServicesDevicesListUpdated() {
        Log.d(TAG, "onServicesDevicesListUpdated");
        if (mArdiscoveryService != null) {

            // Get device list
            List<ARDiscoveryDeviceService> deviceList = mArdiscoveryService.getDeviceServicesArray();
            Log.d(TAG, "onServicesDevicesListUpdated: found " + deviceList.size() + " devices.");

            // Update ListView
            mAdapter.clear();
            mAdapter.addAll(deviceList);
            mProgress.hide();

            showEmptyView(deviceList.isEmpty());
        }
    }

    // Register reciever to get notified when list is up to date
    private void registerReceivers() {
        mArdiscoveryServicesDevicesListUpdatedReceiver = new ARDiscoveryServicesDevicesListUpdatedReceiver(this);
        LocalBroadcastManager localBroadcastMgr = LocalBroadcastManager.getInstance(getApplicationContext());
        localBroadcastMgr.registerReceiver(mArdiscoveryServicesDevicesListUpdatedReceiver,
                new IntentFilter(ARDiscoveryService.kARDiscoveryServiceNotificationServicesDevicesListUpdated));
    }

    // Stop listening to receivers
    private void unregisterReceivers() {
        LocalBroadcastManager localBroadcastMgr = LocalBroadcastManager.getInstance(getApplicationContext());
        localBroadcastMgr.unregisterReceiver(mArdiscoveryServicesDevicesListUpdatedReceiver);
    }


    private void closeServices() {
        if (mArdiscoveryService != null) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    mArdiscoveryService.stop();
                    getApplicationContext().unbindService(mArdiscoveryServiceConnection);
                    mArdiscoveryService = null;
                }
            }).start();
        }
    }

    // If noting was detected
    private void showEmptyView(boolean shown) {
        if (mEmptyView == null) {
            mEmptyView = ((ViewStub) findViewById(android.R.id.empty)).inflate();
        }
        mEmptyView.setVisibility(shown ? View.VISIBLE : View.GONE);
    }

    // If the wifi is disabled suggest to reactivate it
    private void showWifiDisabledMessage() {
        Snackbar.make(findViewById(R.id.activity_main), R.string.wifi_disabled, Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.enable, new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Log.v(TAG, "Enabling Wi-Fi...");
                        mWifiManager.setWifiEnabled(true);
                    }
                }).show();
    }

    // Listen to wifi state changes
    public class WifiStateChangedReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(intent.getAction())
                    && mWifiManager.isWifiEnabled()) {
                mProgress.show();
                startDiscovery();
            }
        }
    }
}