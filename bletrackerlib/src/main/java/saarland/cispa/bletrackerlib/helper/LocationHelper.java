package saarland.cispa.bletrackerlib.helper;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.LocationManager;
import android.provider.Settings;

import java.util.concurrent.atomic.AtomicBoolean;

import androidx.appcompat.app.AlertDialog;
import saarland.cispa.bletrackerlib.R;

/**
 * Helper class to easy ensure that Location is on. If not ask user.
 */

public class LocationHelper extends BaseHelper {
    /**
     * Open a dialog and explain the user to turn on GPS and Network location
     * @param activity is needed for showing the message
     */
    public static void showDialogIfGpsIsOff(final Activity activity) {
        final AtomicBoolean positiveClicked = new AtomicBoolean(false);
        if(!isGpsOn(activity)) {
            // notify user
            AlertDialog.Builder dialog = new AlertDialog.Builder(activity);
            dialog.setMessage(R.string.gps_network_not_enabled);
            dialog.setPositiveButton(R.string.open_location_settings, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface paramDialogInterface, int paramInt) {
                    Intent gpsOptionsIntet = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                    activity.startActivity(gpsOptionsIntet);
                    positiveClicked.set(true);
                }
            });
            dialog.setNegativeButton(activity.getString(R.string.cancel),
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface paramDialogInterface, int paramInt) {
                        }
                    });
            dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog1) {
                    if (!positiveClicked.get()) {
                        showAppFunctionalityLimitedWithout(activity, R.string.functionality_limited_gps);
                    }
                }
            });
            dialog.show();
        }
    }

    /**
     * Check if gps is on and location mode high accuracy
     * @return true if gps is on and location mode is high accuracy
     * @param context is needed to check if this service is available
     */
    public static boolean isGpsOn(Context context) {
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        boolean gps_enabled = false;
        //TODO: What to do with devices which have no network provider (most devices without play services installed)
        //boolean network_enabled = false;

        try {
            gps_enabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch(Exception ex) {

        }

//        try {
//            network_enabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
//        } catch(Exception ex) {
//
//        }
        return gps_enabled; //&& network_enabled;
    }
}
