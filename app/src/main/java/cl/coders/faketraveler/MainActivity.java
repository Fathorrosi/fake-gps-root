package cl.coders.faketraveler;

import static cl.coders.faketraveler.MainActivity.SourceChange.CHANGE_FROM_EDITTEXT;
import static cl.coders.faketraveler.MainActivity.SourceChange.CHANGE_FROM_MAP;
import static cl.coders.faketraveler.MainActivity.SourceChange.LOAD;
import static cl.coders.faketraveler.MainActivity.SourceChange.NONE;
import static cl.coders.faketraveler.SharedPrefsUtil.getDouble;
import static cl.coders.faketraveler.SharedPrefsUtil.migrateOldPreferences;
import static cl.coders.faketraveler.SharedPrefsUtil.putDouble;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.provider.Settings;
import java.io.DataOutputStream;
import android.content.pm.PackageManager.NameNotFoundException;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements ServiceConnection {

    private static final String TAG = MainActivity.class.getSimpleName();
    public static final String sharedPrefKey = "cl.coders.faketraveler.sharedprefs";
    public static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("0.######", DecimalFormatSymbols.getInstance(Locale.ROOT));

    private MaterialButton buttonApplyStop;
    private WebView webView;
    private EditText editTextLat;
    private EditText editTextLng;
    private Context context;
    private int currentVersion;
    private boolean firstMock = true;

    private SourceChange srcChange = NONE;

    private MockedLocationService.MockedBinder binder = null;

    // Config
    private int version;
    private double lat;
    private double lng;
    private double zoom;
    private int mockCount;
    private int mockFrequency;
    private double dLat;
    private double dLng;
    private long endTime;
    private String mapProvider;

    @Override
    @SuppressLint("SetJavaScriptEnabled") // XSS unlikely an issue here...
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_layout), (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout()
            );
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        context = getApplicationContext();
        webView = findViewById(R.id.webView0);

        // **Tambahkan WebAppInterface**
        WebAppInterface webAppInterface = new WebAppInterface();
        webView.getSettings().setJavaScriptEnabled(true);
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient());
        webView.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setDatabaseEnabled(true);
        webView.addJavascriptInterface(webAppInterface, "Android");

        buttonApplyStop = findViewById(R.id.button_applyStop);
        MaterialButton buttonSettings = findViewById(R.id.button_settings);
        editTextLat = findViewById(R.id.editTextLat);
        editTextLng = findViewById(R.id.editTextLng);

        buttonApplyStop.setOnClickListener(view -> {
            Intent intent = new Intent(this, MockedLocationService.class);
            bindService(intent, this, BIND_AUTO_CREATE);
        });
        buttonSettings.setOnClickListener(view -> {
            Intent myIntent = new Intent(getBaseContext(), MoreActivity.class);
            startActivity(myIntent);
        });

        try {
            PackageInfo pInfo = this.getPackageManager().getPackageInfo(getPackageName(), 0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                currentVersion = (int) (pInfo.getLongVersionCode() >> 32);
            } else {
                currentVersion = pInfo.versionCode;
            }
        } catch (NameNotFoundException e) {
            Log.e(MainActivity.class.toString(), "Could not read version info!", e);
        }

        loadSharedPrefs();

        setLatLng(lat, lng, LOAD);

        webView.loadUrl(Uri.parse("file:///android_asset/map.html").buildUpon()
                .appendQueryParameter("lat", "" + lat)
                .appendQueryParameter("lng", "" + lng)
                .appendQueryParameter("zoom", "" + zoom)
                .appendQueryParameter("provider", mapProvider)
                .build()
                .toString());

        // TextWatcher untuk lat/lng tetap sama
        editTextLat.addTextChangedListener(new TextWatcher() {
            @Override public void afterTextChanged(Editable s) {
                if (!editTextLat.getText().toString().isEmpty() && !editTextLat.getText().toString().equals("-")) {
                    if (srcChange != CHANGE_FROM_MAP) {
                        try {
                            lat = Double.parseDouble(editTextLat.getText().toString());
                            setLatLng(lat, lng, CHANGE_FROM_EDITTEXT);
                        } catch (Throwable t) {
                            Log.e(MainActivity.class.toString(), "Could not read latitude!", t);
                        }
                    }
                }
            }
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });

        editTextLng.addTextChangedListener(new TextWatcher() {
            @Override public void afterTextChanged(Editable s) {
                if (!editTextLng.getText().toString().isEmpty() && !editTextLng.getText().toString().equals("-")) {
                    if (srcChange != CHANGE_FROM_MAP) {
                        try {
                            lng = Double.parseDouble(editTextLng.getText().toString());
                            setLatLng(lat, lng, CHANGE_FROM_EDITTEXT);
                        } catch (Throwable t) {
                            Log.e(MainActivity.class.toString(), "Could not read longitude!", t);
                        }
                    }
                }
            }
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });

        if (endTime > System.currentTimeMillis()) {
            changeButtonToStop();
        } else {
            endTime = 0;
            saveSettings();
        }

    }

    // ===================== WebAppInterface =====================
    public class WebAppInterface {

        @JavascriptInterface
        public void searchAddress(String query) {
            new Thread(() -> {
                try {
                    String apiUrl = "https://nominatim.openstreetmap.org/search?q=" +
                            URLEncoder.encode(query, "UTF-8") +
                            "&format=json&limit=1";

                    URL url = new URL(apiUrl);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("User-Agent", "FakeTraveler/1.0"); // wajib untuk Nominatim

                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) {
                        response.append(line);
                    }
                    in.close();

                    String jsonResult = response.toString();

                    // Kirim balik ke JS
                    new Handler(Looper.getMainLooper()).post(() -> {
                            // jsonResult adalah hasil JSON dari API
                            webView.evaluateJavascript("handleSearchResult(" + JSONObject.quote(jsonResult) + ")", null);
                        });

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        }
    }

    // ===================== Sisanya tetap sama =====================
    @Override
    protected void onResume() {
        super.onResume();
        context = getApplicationContext();
        loadSharedPrefs();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }


    /**
     * Check and (re-)initialize shared preferences.
     */
    private void loadSharedPrefs() {
        migrateOldPreferences(context);

        SharedPreferences sharedPref = context.getSharedPreferences(sharedPrefKey, Context.MODE_PRIVATE);

        version = sharedPref.getInt("version", 0);
        lat = getDouble(sharedPref, "lat", 12);
        lng = getDouble(sharedPref, "lng", 15);
        zoom = getDouble(sharedPref, "zoom", 12);
        mockCount = sharedPref.getInt("mockCount", 0);
        mockFrequency = sharedPref.getInt("mockFrequency", 10);
        dLat = getDouble(sharedPref, "dLat", 0);
        dLng = getDouble(sharedPref, "dLng", 0);
        endTime = sharedPref.getLong("endTime", 0);
        mapProvider = sharedPref.getString("mapProvider", MapProviderUtil.getDefaultMapProvider(Locale.getDefault()));

        if (version != currentVersion) {
            version = currentVersion;
            saveSettings();
        }
    }

    private void saveSettings() {
        Editor editor = context.getSharedPreferences(sharedPrefKey, Context.MODE_PRIVATE).edit();

        editor.putInt("version", version);
        putDouble(editor, "lat", lat);
        putDouble(editor, "lng", lng);
        putDouble(editor, "zoom", zoom);
        editor.putInt("mockCount", mockCount);
        editor.putInt("mockFrequency", mockFrequency);
        putDouble(editor, "dLat", dLat);
        putDouble(editor, "dLng", dLng);
        editor.putLong("endTime", endTime);
        editor.putString("mapProvider", mapProvider);

        editor.apply();
    }

    /**
     * Apply a mocked location, and start an alarm to keep doing it if mockCount is > 1
     * This method is called when "Apply" button is pressed.
     */
    protected void applyLocation() {
        // Ensure AppOps permission is set first
        if (!hasRootAccess() && !executeAppOpsCommand()) {
            toast(R.string.MainActivity_EnableMockLocation);
            return;
        }
        
        // Double check permission if we have root
        if (hasRootAccess() && !executeAppOpsCommand()) {
            toast("Failed to set mock location permission via AppOps");
            return;
        }
        
        if (latIsEmpty() || lngIsEmpty()) {
            toast(context.getResources().getString(R.string.MainActivity_NoLatLong));
            return;
        }

        lat = Double.parseDouble(editTextLat.getText().toString());
        lng = Double.parseDouble(editTextLng.getText().toString());

        if (binder != null) {
            try {
                binder.startMock(lng, lat, dLng / 1000000, dLat / 1000000, mockFrequency * 1000L, mockCount);
                endTime = System.currentTimeMillis() + (mockCount - 1L) * mockFrequency * 1000L;
                saveSettings();
            } catch (SecurityException e) {
                toast("Permission error: " + e.getMessage());
                if (!executeAppOpsCommand()) {
                    toast("Failed to set mock location permission via AppOps");
                }
                changeButtonToApply();
            }
        }
    }

    /**
     * Shows a toast
     */
    void toast(String str) {
        Toast.makeText(context, str, Toast.LENGTH_SHORT).show();
    }

    /**
     * Shows a toast
     */
    void toast(@StringRes int strRes) {
        Toast.makeText(context, strRes, Toast.LENGTH_SHORT).show();
    }

    /**
     * Returns true editTextLat has no text
     */
    boolean latIsEmpty() {
        return editTextLat.getText().toString().isBlank();
    }

    /**
     * Returns true editTextLng has no text
     */
    boolean lngIsEmpty() {
        return editTextLng.getText().toString().isBlank();
    }

    protected void setMapMarker(double lat, double lng) {
        if (webView == null || webView.getUrl() == null) return;
        webView.loadUrl("javascript:setOnMap(" + lat + "," + lng + ");");
    }

    /**
     * Changes the button to Apply, and its behavior.
     */
    void changeButtonToApply() {
        buttonApplyStop.setText(context.getResources().getString(R.string.ActivityMain_Apply));
        buttonApplyStop.setOnClickListener(view -> {
            if (binder == null) {
                Intent intent = new Intent(this, MockedLocationService.class);
                bindService(intent, this, BIND_AUTO_CREATE);
            } else {
                binder.continueMock();
            }
        });
    }

    /**
     * Changes the button to Stop, and its behavior.
     */
    void changeButtonToStop() {
        buttonApplyStop.setText(context.getResources().getString(R.string.ActivityMain_Stop));
        buttonApplyStop.setOnClickListener(view -> {
            unbindService(this);
            disconnectService();
        });
    }

    public void setZoom(double zoom) {
        this.zoom = zoom;
        saveSettings();
    }

    /**
     * Sets latitude and longitude
     *
     * @param mLat      latitude
     * @param mLng      longitude
     * @param srcChange CHANGE_FROM_EDITTEXT or CHANGE_FROM_MAP, indicates from where comes the change
     */
    void setLatLng(double mLat, double mLng, SourceChange srcChange) {
        lat = mLat;
        lng = mLng;

        setMapMarker(lat, lng);

        if (srcChange == CHANGE_FROM_MAP || srcChange == LOAD) {
            this.srcChange = CHANGE_FROM_MAP;
            editTextLat.setText(DECIMAL_FORMAT.format(lat));
            editTextLng.setText(DECIMAL_FORMAT.format(lng));
            this.srcChange = NONE;
        }

        saveSettings();
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        binder = (MockedLocationService.MockedBinder) service;
        binder.mockState.observe(this, this::onMockedStateChange);
        binder.mockedLocation.observe(this, this::onMockedLocationChange);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        disconnectService();
    }

    private void disconnectService() {
        binder.mockState.removeObservers(this);
        binder.mockedLocation.removeObservers(this);
        binder = null;
        indicateMockStop();
    }

    private void onMockedStateChange(MockState state) {
        switch (state) {
            case NOT_MOCKED -> indicateMockStop();
            case SERVICE_BOUND -> applyLocation();
            case MOCKED -> {
                changeButtonToStop();
                if (firstMock) {
                    toast(R.string.MainActivity_MockApplied);
                    firstMock = false;
                }
            }
            case MOCK_ERROR -> toast(R.string.MainActivity_MockNotApplied);
        }
    }

    private void indicateMockStop() {
        toast(R.string.MainActivity_MockStopped);
        changeButtonToApply();
        firstMock = true; // Reset for next mock session
    }

    private void onMockedLocationChange(Location location) {
        setMapMarker(location.getLatitude(), location.getLongitude());
    }

    private boolean hasRootAccess() {
        // Safer root check that doesn't execute commands
        String[] paths = { 
            "/system/bin/su",
            "/system/xbin/su", 
            "/sbin/su",
            "/data/local/bin/su",
            "/data/local/xbin/su"
        };
        for (String path : paths) {
            if (new java.io.File(path).exists()) {
                return true;
            }
        }
        return false;
    }

    private boolean executeAppOpsCommand() {
        try {
            Process process = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(process.getOutputStream());
            String cmd = "appops set " + getPackageName() + " android:mock_location allow\n";
            os.writeBytes(cmd);
            os.writeBytes("exit\n");
            os.flush();
            os.close();
            process.waitFor();
            
            // Add small delay to allow permission to take effect
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            return process.exitValue() == 0;
        } catch (Exception e) {
            Log.e(TAG, "Failed to execute AppOps command", e);
            return false;
        }
    }

    public enum SourceChange { NONE, LOAD, CHANGE_FROM_EDITTEXT, CHANGE_FROM_MAP }

}
