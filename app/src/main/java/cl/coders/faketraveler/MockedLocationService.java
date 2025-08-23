package cl.coders.faketraveler;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

public class MockedLocationService extends Service {

    private static final String TAG = MockedLocationService.class.getSimpleName();

    protected final MutableLiveData<MockState> mockState = new MutableLiveData<>();
    protected final MutableLiveData<Location> mockedLocation = new MutableLiveData<>();

    private final List<MockedLocationProvider> providers = new ArrayList<>();

    private final Timer timer = new Timer();
    private final Set<TimerTask> tasks = Collections.synchronizedSet(new HashSet<>());

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        indicateBinding();
        return new MockedBinder(this);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "Mock is finished");
        for (TimerTask t : tasks)
            t.cancel();
        tasks.clear();
        for (MockedLocationProvider prov : providers)
            prov.shutdown();
        providers.clear();
        mockState.postValue(MockState.NOT_MOCKED);
        return super.onUnbind(intent);
    }

    private void indicateBinding() {
        mockState.postValue(MockState.SERVICE_BOUND);
    }

    protected void startMockedService(double longitude, double latitude, double longitudeDistance, double latitudeDistance, long mockMilli, int maxTime) {
        try {
            providers.clear();
            // Only use GPS provider for more stable spoofing
            providers.add(new MockedLocationProvider(LocationManager.GPS_PROVIDER, this));
            
            // Disable other location providers
            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.setTestProviderEnabled(LocationManager.NETWORK_PROVIDER, false);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && lm.isProviderEnabled(LocationManager.FUSED_PROVIDER)) {
                lm.setTestProviderEnabled(LocationManager.FUSED_PROVIDER, false);
            }
            
            // Increase update frequency to 1 second (1000ms)
            mockMilli = 1000;

            MockedTask mockedTask = new MockedTask(longitude, latitude, longitudeDistance, latitudeDistance, maxTime);
            timer.schedule(mockedTask, 0L, mockMilli);
            tasks.add(mockedTask);
            mockState.postValue(MockState.MOCKED);
        } catch (SecurityException e) {
            Log.e(TAG, "Could not construct mock location providers!", e);
            mockState.postValue(MockState.MOCK_ERROR);
        }
    }

    class MockedTask extends TimerTask {
        private double longitude;
        private double latitude;
        private final double longitudeMockedDistance;
        private final double latitudeMockedDistance;
        private final int maxLocationTimes;
        private int currentTimes = 0;
        private Location lastLocation;
        
        // Add small random variation to make location appear more natural
        private double getRandomVariation() {
            return (Math.random() * 0.0001) - 0.00005; // +/- ~5 meters
        }

        public MockedTask(double longitude, double latitude, double longitudeMockedDistance, double latitudeMockedDistance, int maxTimes) {
            this.longitude = longitude;
            this.latitude = latitude;
            this.longitudeMockedDistance = longitudeMockedDistance;
            this.latitudeMockedDistance = latitudeMockedDistance;
            this.maxLocationTimes = maxTimes;
        }

        @Override
        public void run() {
            // Create location with small random variation
            Location value = new Location(LocationManager.GPS_PROVIDER);
            value.setLongitude(longitude + getRandomVariation());
            value.setLatitude(latitude + getRandomVariation());
            value.setTime(System.currentTimeMillis());
            value.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
            value.setAccuracy(5.0f);
            
            // Only update if location changed significantly
            if (lastLocation == null || 
                value.distanceTo(lastLocation) > 5.0) { // ~5 meters
                mockedLocation.postValue(value);
                for (MockedLocationProvider prov : providers)
                    prov.pushLocation(value.getLatitude(), value.getLongitude());
                lastLocation = value;
            }
            ++currentTimes;
            if (maxLocationTimes != 0 && maxLocationTimes == currentTimes) {
                this.cancel();
                stopSelf();
                mockState.postValue(MockState.NOT_MOCKED);
            }
            latitude += latitudeMockedDistance;
            longitude += longitudeMockedDistance;
        }
    }

    public static class MockedBinder extends Binder {
        private final MockedLocationService service;
        public final LiveData<MockState> mockState;
        public final LiveData<Location> mockedLocation;

        public MockedBinder(MockedLocationService service) {
            this.service = service;
            this.mockState = service.mockState;
            this.mockedLocation = service.mockedLocation;
        }

        public void continueMock() {
            service.indicateBinding();
        }

        public void startMock(double longitude, double latitude, double longitudeDistance, double latitudeDistance, long mockMilli, int maxTimes) {
            service.startMockedService(longitude, latitude, longitudeDistance, latitudeDistance, mockMilli, maxTimes);
        }
    }

}
