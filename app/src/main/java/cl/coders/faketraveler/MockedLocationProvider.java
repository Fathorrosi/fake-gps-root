package cl.coders.faketraveler;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

public class MockedLocationProvider {

    private static final String TAG = MockedLocationProvider.class.getSimpleName();

    private final String providerName;
    private final Context ctx;
    private final LocationManager lm;

    /**
     * Class constructor
     *
     * @param name provider
     * @param ctx  context
     */
    public MockedLocationProvider(String name, Context ctx) {
        this.providerName = name;
        this.ctx = ctx;
        this.lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);

        int powerUsage = 0;
        int accuracy = 5;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            powerUsage = 1;
            accuracy = 2;
        }

        startup(lm, powerUsage, accuracy);
    }

    private void startup(LocationManager lm, int powerUsage, int accuracy) {
        try {
            // Remove existing provider if exists
            try {
                if (lm.getProvider(providerName) != null) {
                    lm.removeTestProvider(providerName);
                    Log.d(TAG, "Removed existing test provider: " + providerName);
                    // Small delay to ensure removal completes
                    Thread.sleep(100);
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not remove existing test provider: " + e.getMessage());
            }
            
            // Add new test provider
            try {
                lm.addTestProvider(
                    providerName,
                    false,  // requiresNetwork
                    false,  // requiresSatellite
                    false,  // requiresCell
                    false,  // hasMonetaryCost
                    true,   // supportsAltitude
                    true,   // supportsSpeed
                    true,   // supportsBearing
                    android.location.Criteria.POWER_LOW,
                    android.location.Criteria.ACCURACY_FINE
                );
                lm.setTestProviderEnabled(providerName, true);
                Log.d(TAG, "Test provider " + providerName + " added and enabled");
            } catch (IllegalArgumentException e) {
                // If provider still exists, try to use existing one
                if (e.getMessage().contains("already exists")) {
                    Log.w(TAG, "Using existing test provider: " + providerName);
                    lm.setTestProviderEnabled(providerName, true);
                } else {
                    throw e;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to setup test provider: " + e.getMessage());
            throw new SecurityException("Failed to setup test provider: " + e.getMessage());
        }
    }

    /**
     * Pushes the location in the system (mock). This is where the magic gets done.
     *
     * @param lat latitude
     * @param lon longitude
     */
    public void pushLocation(double lat, double lon) {
        try {
            Location mockLocation = new Location(providerName);
            mockLocation.setLatitude(lat);
            mockLocation.setLongitude(lon);
            mockLocation.setAltitude(0.0);
            mockLocation.setTime(System.currentTimeMillis());
            mockLocation.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
            mockLocation.setAccuracy(1.0f);
            mockLocation.setBearing(0.0f);
            mockLocation.setSpeed(0.0f);

            lm.setTestProviderLocation(providerName, mockLocation);
            Log.d(TAG, "Mock location pushed: " + lat + ", " + lon);
        } catch (Exception e) {
            Log.e(TAG, "Failed to push mock location: " + e.getMessage());
            android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
            mainHandler.post(() ->
                android.widget.Toast.makeText(ctx,
                    "Mock location failed: " + e.getMessage(),
                    android.widget.Toast.LENGTH_SHORT).show()
            );
        }
    }

    /**
     * Removes the provider.
     */
    public void shutdown() {
        try {
            lm.removeTestProvider(providerName);
            Log.d(TAG, "Test provider " + providerName + " removed");
        } catch (Exception e) {
            Log.e(TAG, "Failed to remove test provider: " + e.getMessage());
            throw new SecurityException("Failed to remove test provider: " + e.getMessage());
        }
    }

}
