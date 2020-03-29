package niederhauser.loic.dronetohand;

import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM;
import com.parrot.arsdk.arcommands.ARCOMMANDS_COMMON_MAVLINKSTATE_MAVLINKFILEPLAYINGSTATECHANGED_STATE_ENUM;
import com.parrot.arsdk.arcontroller.ARCONTROLLER_DEVICE_STATE_ENUM;
import com.parrot.arsdk.arcontroller.ARCONTROLLER_DICTIONARY_KEY_ENUM;
import com.parrot.arsdk.arcontroller.ARCONTROLLER_ERROR_ENUM;
import com.parrot.arsdk.arcontroller.ARControllerArgumentDictionary;
import com.parrot.arsdk.arcontroller.ARControllerDictionary;
import com.parrot.arsdk.arcontroller.ARControllerException;
import com.parrot.arsdk.arcontroller.ARDeviceController;
import com.parrot.arsdk.arcontroller.ARDeviceControllerListener;
import com.parrot.arsdk.arcontroller.ARFeatureARDrone3;
import com.parrot.arsdk.arcontroller.ARFeatureCommon;
import com.parrot.arsdk.ardiscovery.ARDISCOVERY_PRODUCT_ENUM;
import com.parrot.arsdk.ardiscovery.ARDiscoveryDevice;
import com.parrot.arsdk.ardiscovery.ARDiscoveryDeviceNetService;
import com.parrot.arsdk.ardiscovery.ARDiscoveryDeviceService;
import com.parrot.arsdk.ardiscovery.ARDiscoveryException;
import com.parrot.arsdk.arsal.ARSALPrint;

import android.util.Log;


import static android.content.ContentValues.TAG;
import static java.lang.Math.atan2;
import static java.lang.Math.cos;
import static java.lang.Math.round;
import static java.lang.Math.sin;
import static java.lang.Math.toDegrees;
import static java.lang.StrictMath.abs;

public class DroneHandler implements ARDeviceControllerListener {
    private final int critBatteryLevel = 5;
    private final float distTolerance = 2;
    private final float angleTolerance = 10;
    private ARDeviceController mDroneController;
    private ARDiscoveryDeviceService mService;
    static private droneState state = droneState.NOT_CONNECTED;
    private double latitude;
    private double longitude;
    private double altitude;
    private double goalLatitude;
    private double goalLongitude;
    private float yaw;
    private float pitch;
    private float roll;
    private double forwardAngle = 20;
    private boolean gotoPt = false;

    public enum droneState{
        IDLE, FLYING, WAITING_TO_LAND, LANDED,NOT_CONNECTED
    }

    public DroneHandler(ARDiscoveryDeviceService service) {
        state = droneState.NOT_CONNECTED;
        mService = service;
        ARDiscoveryDevice device = createDiscoveryDevice();
        mDroneController = createController(device);
        if (mDroneController != null) {
            mDroneController.addListener(this);
            ARCONTROLLER_ERROR_ENUM error = mDroneController.start();
            mDroneController.getFeatureCommon().sendWifiSettingsOutdoorSetting((byte)1);
            state = droneState.IDLE;
        }
        else{
            Log.e(TAG, "NO CONTROLLER");
        }

    }

    //region PublicFunctions

    public void takeOff()
    {
        if (ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM.ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_LANDED.equals(getPilotingState()))
        {
            ARCONTROLLER_ERROR_ENUM error = mDroneController.getFeatureARDrone3().sendPilotingTakeOff();

            if (!error.equals(ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK))
            {
                ARSALPrint.e(TAG, "Error while sending take off: " + error);
            }
            else{
                state = droneState.FLYING;
            }
        }

    }

    public void land()
    {
        gotoPt = false;
        mDroneController.getFeatureARDrone3().setPilotingPCMDPitch((byte) 0);
        mDroneController.getFeatureARDrone3().setPilotingPCMDYaw((byte) 0);
        mDroneController.getFeatureARDrone3().setPilotingPCMDGaz((byte) 0);
        mDroneController.getFeatureARDrone3().setPilotingPCMDFlag((byte)0);

        ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM flyingState = getPilotingState();
        if (ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM.ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_HOVERING.equals(flyingState) ||
                ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM.ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_HOVERING.equals(flyingState))
        {
            ARCONTROLLER_ERROR_ENUM error = mDroneController.getFeatureARDrone3().sendPilotingLanding();
            state = droneState.LANDED;
            if (!error.equals(ARCONTROLLER_ERROR_ENUM.ARCONTROLLER_OK))
            {
                ARSALPrint.e(TAG, "Error while sending take off: " + error);
            }
            else{
                state = droneState.FLYING;
            }

        }
    }

    static public droneState getDroneState(){
        if (state == null){
            return droneState.NOT_CONNECTED;
        }
        else{
            return state;
        }
    }


    public void goTo( double goalLatitude, double goalLongitude)
    {
        gotoPt=true;
        this.goalLatitude=goalLatitude;
        this.goalLongitude=goalLongitude;
    }

    @Override
    public void onExtensionStateChanged (ARDeviceController deviceController, ARCONTROLLER_DEVICE_STATE_ENUM newState, ARDISCOVERY_PRODUCT_ENUM product, String name, ARCONTROLLER_ERROR_ENUM error)
    {
        return;
    }

    @Override
    public void onCommandReceived (ARDeviceController deviceController, ARCONTROLLER_DICTIONARY_KEY_ENUM commandKey, ARControllerDictionary elementDictionary) {

        // On GPS position change
        if (commandKey == ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSTATE_POSITIONCHANGED && (elementDictionary != null)){
            ARControllerArgumentDictionary<Object> args = elementDictionary.get(ARControllerDictionary.ARCONTROLLER_DICTIONARY_SINGLE_KEY);
            if (args != null) {
                double newLatitude = (double)args.get(ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSTATE_POSITIONCHANGED_LATITUDE);
                double newLongitude = (double)args.get(ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSTATE_POSITIONCHANGED_LONGITUDE);
                double newAltitude = (double)args.get(ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSTATE_POSITIONCHANGED_ALTITUDE);
                if (abs(newLatitude)<90 && abs(newLongitude)<90){
                    latitude = newLatitude;
                    longitude = newLongitude;
                    altitude = newAltitude;
                }
                if(state.equals(droneState.FLYING) && gotoPt){
                    if  (computeDistToPoint()<distTolerance){
                        mDroneController.getFeatureARDrone3().setPilotingPCMDPitch((byte) 0);
                        mDroneController.getFeatureARDrone3().setPilotingPCMDYaw((byte) 0);
                        mDroneController.getFeatureARDrone3().setPilotingPCMDFlag((byte)0);
                        state = droneState.WAITING_TO_LAND;
                        land();
                        Log.e(TAG, "WE ARE DONE");
                    }
                    else{
                        mDroneController.getFeatureARDrone3().setPilotingPCMDFlag((byte)1);
                        if (abs(toDegrees(angleToPoint())-toDegrees(yaw))<angleTolerance){
                            Log.e(TAG, "FWD");
                            mDroneController.getFeatureARDrone3().setPilotingPCMDPitch((byte) ((int)(round(forwardAngle/180.0*100.0))));
                            mDroneController.getFeatureARDrone3().setPilotingPCMDYaw((byte) 0);
                            mDroneController.getFeatureARDrone3().setPilotingPCMDGaz((byte) 0);
                        }
                        else{
                            int yawToReach =  (int) (round(toDegrees(angleToPoint()))/180.0*100.0);
                            Log.e(TAG,"Angle: "+  yawToReach+ "Actual: " + toDegrees(yaw));
                            mDroneController.getFeatureARDrone3().setPilotingPCMDPitch((byte) 0);
                            mDroneController.getFeatureARDrone3().setPilotingPCMDYaw((byte) 50);
                        }
                    }
                }
            }
        }

        // On battery level change
        if ((commandKey == ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_COMMON_COMMONSTATE_BATTERYSTATECHANGED) && (elementDictionary != null)){
            ARControllerArgumentDictionary<Object> args = elementDictionary.get(ARControllerDictionary.ARCONTROLLER_DICTIONARY_SINGLE_KEY);
            if (args != null) {
                byte percent = (byte)((Integer)args.get(ARFeatureCommon.ARCONTROLLER_DICTIONARY_KEY_COMMON_COMMONSTATE_BATTERYSTATECHANGED_PERCENT)).intValue();
                int batLevel = percent;
                if (batLevel<critBatteryLevel)
                {
                    land();
                }
            }
        }

        // On attitude change
        if ((commandKey == ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSTATE_ATTITUDECHANGED) && (elementDictionary != null)){
            ARControllerArgumentDictionary<Object> args = elementDictionary.get(ARControllerDictionary.ARCONTROLLER_DICTIONARY_SINGLE_KEY);
            if (args != null) {
                roll = (float)((Double)args.get(ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSTATE_ATTITUDECHANGED_ROLL)).doubleValue();
                pitch = (float)((Double)args.get(ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSTATE_ATTITUDECHANGED_PITCH)).doubleValue();
                yaw = (float)((Double)args.get(ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSTATE_ATTITUDECHANGED_YAW)).doubleValue();
                if (abs(toDegrees(angleToPoint())-toDegrees(yaw))<angleTolerance && state.equals(droneState.FLYING) && gotoPt){
                    mDroneController.getFeatureARDrone3().setPilotingPCMDYaw((byte) 0);
                }
            }
        }
    }

    @Override
    // called when the state of the device controller has changed
    public void onStateChanged (ARDeviceController deviceController, ARCONTROLLER_DEVICE_STATE_ENUM newState, ARCONTROLLER_ERROR_ENUM error)
    {
        switch (newState)
        {
            case ARCONTROLLER_DEVICE_STATE_RUNNING:
                break;
            case ARCONTROLLER_DEVICE_STATE_STOPPED:
                break;
            case ARCONTROLLER_DEVICE_STATE_STARTING:
                break;
            case ARCONTROLLER_DEVICE_STATE_STOPPING:
                break;
            default:
                break;
        }
    }

    // Return the current ETA
    public double getETAmin(){
        return 1;
    }

    // Returns the ETA between two points
    static public double computeETAmin(int startPosition, int endPosition){
        return 1;
    }
    //endregion

    private float computeDistToPoint(){
        float earthRadiusKm = 6371.0f;
        double dLat = abs(goalLatitude-latitude);
        double dLon = abs(goalLongitude-longitude);

        double a = sin(dLat/2) * sin(dLat/2) +
                sin(dLon/2) * sin(dLon/2) * cos(latitude) * cos(goalLatitude);
        double c = 2 * atan2(Math.sqrt(a), Math.sqrt(1-a));
        double distance = c *earthRadiusKm;
        return (float) distance*1000;

    }
    private float angleToPoint(){
        double dLat = abs(goalLatitude-latitude);
        double dLon = abs(goalLongitude-longitude);
        float angle = (float) atan2(dLon, dLat);
        return angle;
    }
    private ARDeviceController createController(ARDiscoveryDevice device)
    {
        ARDeviceController deviceController = null;
        try {
            deviceController = new ARDeviceController(device);
        } catch (ARControllerException e) {
            e.printStackTrace();
        }
        return deviceController;
    }

    private ARDiscoveryDevice createDiscoveryDevice()
    {
        ARDiscoveryDevice device = null;
        if ((mService != null))
        {
            try
            {
                device = new ARDiscoveryDevice();

                ARDiscoveryDeviceNetService netDeviceService = (ARDiscoveryDeviceNetService) mService.getDevice();

                device.initWifi(ARDISCOVERY_PRODUCT_ENUM.ARDISCOVERY_PRODUCT_ARDRONE, netDeviceService.getName(), netDeviceService.getIp(), netDeviceService.getPort());
            }
            catch (ARDiscoveryException e)
            {
                e.printStackTrace();
                Log.e(TAG, "Error: " + e.getError());
            }
        }
        else{
            Log.e(TAG,"No Device created");
        }
        return device;
    }

    private ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM getPilotingState()
    {
        ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM flyingState = ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM.eARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_UNKNOWN_ENUM_VALUE;
        if (mDroneController != null)
        {
            try
            {
                ARControllerDictionary dict = mDroneController.getCommandElements(ARCONTROLLER_DICTIONARY_KEY_ENUM.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED);
                if (dict != null)
                {
                    ARControllerArgumentDictionary<Object> args = dict.get(ARControllerDictionary.ARCONTROLLER_DICTIONARY_SINGLE_KEY);
                    if (args != null)
                    {
                        Integer flyingStateInt = (Integer) args.get(ARFeatureARDrone3.ARCONTROLLER_DICTIONARY_KEY_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE);
                        flyingState = ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM.getFromValue(flyingStateInt);
                    }
                }
            }
            catch (ARControllerException e)
            {
                e.printStackTrace();
            }
        }
        return flyingState;
    }

}
