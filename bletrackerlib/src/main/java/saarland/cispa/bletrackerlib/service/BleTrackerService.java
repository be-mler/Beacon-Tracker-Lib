package saarland.cispa.bletrackerlib.service;

import android.app.Application;
import android.app.Notification;
import android.os.RemoteException;
import android.util.Log;

import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.Region;
import org.altbeacon.beacon.powersave.BackgroundPowerSaver;
import org.altbeacon.beacon.startup.BootstrapNotifier;
import org.altbeacon.beacon.startup.RegionBootstrap;

import java.util.ArrayList;

import saarland.cispa.bletrackerlib.BleTracker;
import saarland.cispa.bletrackerlib.remote.RemoteConnection;

/**
 * This is the heart of the Lib here the AltBeacon scanner service is created and invoked.
 * This class has to be the application context yor app and must be specified in the android manifest!!
 * There the callback is fired if a beacon is near.
 * For getting the beacon information a {@link RangeNotifierImpl} gets created for both,
 * the background scanner and the foreground scanner in the specific methods
 * {@link BleTrackerService#createBackgroundService(ArrayList, RemoteConnection)} and
 * {@link BleTrackerService#createForegroundService(ArrayList, Notification, RemoteConnection)}.
 */

public final class BleTrackerService extends Application implements BootstrapNotifier {

    private static final String TAG = "BleTrackerService";
    private BeaconManager beaconManager;
    private final Region region = new Region("AllBeaconsRegion", null, null, null);
    private RegionBootstrap regionBootstrap;
    private BackgroundPowerSaver backgroundPowerSaver;
    private RangeNotifierImpl rangeNotifier;
    private ArrayList<BeaconNotifier> stateNotifiers;

    public BleTrackerService() {

    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    /**
     * Creates a service which operates in background
     * Do not change the order of the calls in this constructor without knowing what you are doing!
     * @param stateNotifiers notification callbacks
     * @param cispaConnection the cispaConnection
     */
    public void createBackgroundService(ArrayList<BeaconNotifier> stateNotifiers, RemoteConnection cispaConnection) {
        this.stateNotifiers = stateNotifiers;
        this.rangeNotifier = new RangeNotifierImpl(this, stateNotifiers, cispaConnection);

        beaconManager = BeaconManager.getInstanceForApplication(this);

        //backgroundPowerSaver = new BackgroundPowerSaver(this);

        //TODO: remove debug code before release
        //TimedBeaconSimulator simulator = new TimedBeaconSimulator();
        //simulator.USE_SIMULATED_BEACONS = true;
        //BeaconManager.setBeaconSimulator(simulator);
        //simulator = ((TimedBeaconSimulator) BeaconManager.getBeaconSimulator());
        //simulator.createTimedSimulatedBeacons();
        //beaconManager.setDebug(true);

        beaconManager.addRangeNotifier(rangeNotifier);

        // Set the Layout of the beacons to which are we listening to
        LayoutManager.setAllLayouts(beaconManager);
    }

    /**
     * Creates a service which also operates in background but will never go asleep
     * Do not change the order of the calls in this constructor without knowing what you are doing!
     * @param stateNotifiers notification callback
     * @param notification a notification shown if the service is running
     * @param cispaConnection the cispaConnection
     */
    public void createForegroundService(ArrayList<BeaconNotifier> stateNotifiers, Notification notification, RemoteConnection cispaConnection) {
        this.stateNotifiers = stateNotifiers;
        this.rangeNotifier = new RangeNotifierImpl(this, stateNotifiers, cispaConnection);

        beaconManager = BeaconManager.getInstanceForApplication(this);

        beaconManager.disableForegroundServiceScanning();
        beaconManager.enableForegroundServiceScanning(notification, 456);
        beaconManager.setEnableScheduledScanJobs(false);
        beaconManager.setBackgroundBetweenScanPeriod(0);
        beaconManager.setBackgroundScanPeriod(BleTracker.getPreferences().getScanInterval());

        backgroundPowerSaver = null;

        //TODO: remove debug code before release
        //TimedBeaconSimulator simulator = new TimedBeaconSimulator();
        //simulator.USE_SIMULATED_BEACONS = true;
        //BeaconManager.setBeaconSimulator(simulator);
        //simulator = ((TimedBeaconSimulator) BeaconManager.getBeaconSimulator());
        //simulator.createTimedSimulatedBeacons();
        //beaconManager.setDebug(true);

        beaconManager.addRangeNotifier(rangeNotifier);

        // Set the Layout of the beacons to which are we listening to
        LayoutManager.setAllLayouts(beaconManager);
    }

    /**
     * This method is called if a beacon enters the region and sendBeacon's onBeaconNearby callback to the app
     * This callback can be used to show a notification etc.
     * @param region the beacons to look for
     */

    @Override
    public void didEnterRegion(Region region) {
        Log.i(TAG, "I just saw an beacon for the first time!");
        try {
            beaconManager.startRangingBeaconsInRegion(region);
        } catch (RemoteException e) {
            Log.e(TAG, e.getMessage(), e);
        }
        for (BeaconNotifier stateNotifier : stateNotifiers) {
            stateNotifier.onBeaconNearby();
        }
    }

    /**
     * This method is called if there all no beacons seen anymore
     * @param region the beacons to look for
     */

    @Override
    public void didExitRegion(Region region) {
        Log.i(TAG, "I no longer see an beacon");
        try {
            beaconManager.stopRangingBeaconsInRegion(region);
        } catch (RemoteException e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }

    /**
     * This method is called if beacons switching state from seen/not seen
     * @param state the state
     * @param region the beacons to look for
     */
    @Override
    public void didDetermineStateForRegion(int state, Region region) {
        // Don't care
    }

    public boolean disableMonitoring() {
        if (regionBootstrap != null) {
            regionBootstrap.disable();
            regionBootstrap = null;
            return true;
        }
        return false;
    }
    public void enableMonitoring() {
        regionBootstrap = new RegionBootstrap(this, region);
    }

    public boolean isMonitoring() {
        return regionBootstrap != null;
    }

    public void addRemoteConnection(RemoteConnection connection) {
        rangeNotifier.addRemoteConnection(connection);
    }
}
