package saarland.cispa.bletrackerlib;

import android.app.Activity;
import android.app.Notification;
import android.util.Log;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;

import saarland.cispa.bletrackerlib.exceptions.BleOtherServiceStillRunningException;
import saarland.cispa.bletrackerlib.helper.BluetoothHelper;
import saarland.cispa.bletrackerlib.helper.LocationHelper;
import saarland.cispa.bletrackerlib.remote.RemoteConnection;
import saarland.cispa.bletrackerlib.remote.RemotePreferences;
import saarland.cispa.bletrackerlib.remote.SendMode;
import saarland.cispa.bletrackerlib.service.BleTrackerService;
import saarland.cispa.bletrackerlib.service.BeaconNotifier;

/**
 * This is the main entry point for interacting with the lib.
 * Here you can control the {@link BleTrackerService} (e.g. create, start, stop the service).
 * You can see it as an wrapper class for the BleTrackerService.
 * At this point is possible to register listeners for getting the found beacons,
 * getting notified if a beacon is near and getting status information of the service.
 * Also it is possible to add new {@link RemoteConnection}s pointing to your specified REST endpoint.
 */

public class BleTracker {

    private static BleTracker bleTracker;
    private static BleTrackerPreferences preferences = new BleTrackerPreferences();

    private BleTrackerService service;
    private final ArrayList<ServiceNotifier> serviceNotifiers = new ArrayList<>();
    private ArrayList<BeaconNotifier> beaconNotifiers = new ArrayList<>();

    private RemoteConnection cispaConnection;

    public static BleTracker getInstance() {
        if (bleTracker == null) {
            bleTracker = new BleTracker();
        }
        return bleTracker;
    }

    /**
     * Creates the beacon service with your settings
     * @param activity The application activity
     * @param preferences The preferences if you want to use your specific
     */
    public void init(Activity activity, BleTrackerPreferences preferences) {
        BleTracker.preferences = preferences;
        init(activity);
    }

    /**
     * Creates the beacon service with default settings
     * @param activity The application activity
     */
    public void init(Activity activity) {
        setActivity(activity);
        initCispaConnection();
    }

    private void initCispaConnection() {
        RemotePreferences remotePreferences = new RemotePreferences();

        if (preferences.isSendToCispa()) {
            try {
                InputStream inputStream = service.getResources().openRawResource(
                        service.getResources().getIdentifier("cispa", "raw", service.getPackageName()));

                CertificateFactory cf = CertificateFactory.getInstance("X.509");

                Certificate ca;
                try {
                    ca = cf.generateCertificate(inputStream);
                } finally {
                    inputStream.close();
                }

                System.out.println("ca=" + ((X509Certificate) ca).getSubjectDN());

                // Create a KeyStore containing our trusted CAs
                String keyStoreType = KeyStore.getDefaultType();
                KeyStore keyStore = KeyStore.getInstance(keyStoreType);
                keyStore.load(null, null);
                keyStore.setCertificateEntry("ca", ca);


                remotePreferences.setKeyStore(keyStore);
                Log.d("CERTX", "Loaded ");
            } catch (Exception e){
                Log.d("CERTX", "Failed ");
                e.printStackTrace();
            }
            remotePreferences.setSendMode(SendMode.DO_ONLY_SEND_IF_BEACONS_HAVE_GPS);
        } else {
            remotePreferences.setSendMode(SendMode.DO_NOT_SEND_BEACONS);
        }
        cispaConnection = new RemoteConnection("https://ble.faber.rocks/api/beacon",
                service.getApplicationContext(), remotePreferences);
    }

    /**
     * Gets the specified preferences
     * @return the preferences
     */
    public static BleTrackerPreferences getPreferences() {
        return preferences;
    }

    /**
     * Adds a beaconNotifier which get's called if there are beacons near
     * @param beaconNotifier the callback
     */
    public void addBeaconNotifier(BeaconNotifier beaconNotifier) {
        beaconNotifiers.add(beaconNotifier);
    }

    /**
     * Adds a serviceNotifier which get's called if the service state changes
     * @param serviceNotifier
     */
    public void addServiceNotifier(ServiceNotifier serviceNotifier) {
        serviceNotifiers.add(serviceNotifier);
    }

    /**
     * Add a custom RESTful API connection
     * @param connection a remote connection to a RESTful endpoint
     */
    public void addRemoteConnection(RemoteConnection connection) {
        service.addRemoteConnection(connection);
    }

    /**
     * Returns the connection to CISPA if sendToCispa=true in constructor
     * @return the connection to CISPA
     */
    public RemoteConnection getCispaConnection() {
        return cispaConnection;
    }

    /**
     * Creates a background service which operates in the background and gets called from time to time by the system
     * This causes low battery drain but also the refresh rate is low
     * Also this will NOT! send to CISPA because of too low accuracy
     * @throws BleOtherServiceStillRunningException if an old service is still running. Stop old service first before creating a new one
     */
    public void createBackgroundService() throws BleOtherServiceStillRunningException {
        if (isRunning()) {
            throw new BleOtherServiceStillRunningException();
        }
        // CISPA connection get's reinitialized with send mode false set in preferences.
        preferences.setSendToCispa(false);
        initCispaConnection();

        service.createBackgroundService(beaconNotifiers, cispaConnection);
    }

    /**
     * Creates a service which also operates in background but will never go asleep thus your app stays in foreground
     * This causes huge battery drain but also gives a very good refresh rate
     * @param foregroundNotification this is needed because we need to display a permanent notification if tracking should run as foreground service
     *                               You can use ForegroundNotification.parse() for this type of notification
     * @throws BleOtherServiceStillRunningException if an old service is still running. Stop old service first before creating a new one
     */
    public void createForegroundService(Notification foregroundNotification) throws BleOtherServiceStillRunningException {
        if (isRunning()) {
            throw new BleOtherServiceStillRunningException();
        }
        service.createForegroundService(beaconNotifiers, foregroundNotification, cispaConnection);
    }

    /**
     * Starts the service and asks user to turn on bluetooth and GPS
     * Service then will start even if bluetooth is turned off and will work after they are turned on later
     * @param activity The application activity. Here the message will be displayed to turn on location an bluetooth
     */
    public void start(Activity activity) {
        LocationHelper.showDialogIfGpsIsOff(activity);
        BluetoothHelper.showDialogIfBluetoothIsOff(activity);
        startWithoutChecks();
    }

    /**
     * Tries to start the service even if GPS and bluetooth is not turned on
     * This in normal case is not the best idea.
     * Service then will start do work if bluetooth is turned on and gps too
     */
    public void startWithoutChecks() {
        service.enableMonitoring();
        for (ServiceNotifier serviceNotifier : serviceNotifiers) {
            serviceNotifier.onStart();
        }
    }

    /**
     * Stops the service
     */
    public void stop() {
        service.disableMonitoring();
        for (ServiceNotifier serviceNotifier : serviceNotifiers) {
            serviceNotifier.onStop();
        }
    }

    /**
     * Indicates if the services is running
     * @return true if service is running
     */
    public boolean isRunning() {
        return (service != null) && service.isMonitoring();
    }

    /**
     * Sets the Activity. Should be called in every Activity.onResume()
     * @param activity
     */
    public void setActivity(Activity activity) {
        if (activity != null) {
            service = (BleTrackerService) activity.getApplicationContext();
        }
    }
}
