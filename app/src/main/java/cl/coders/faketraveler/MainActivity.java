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
import android.content.pm.PackageManager.NameNotFoundException;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;

import java.io.DataOutputStream;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

import android.text.Editable;
import android.text.TextWatcher;

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
    @SuppressLint("SetJavaScriptEnabled")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_layout), (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
            );
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        context = getApplicationContext();
        webView = findViewById(R.id.webView0);
        WebAppInterface webAppInterface = new WebAppInterface(this);

        buttonApplyStop = findViewById(R.id.button_applyStop);
//        MaterialButton buttonSettings = findViewById(R.id.button_settings);
        editTextLat = findViewById(R.id.editTextLat);
        editTextLng = findViewById(R.id.editTextLng);

        buttonApplyStop.setOnClickListener(view -> {
            Intent intent = new Intent(this, MockedLocationService.class);
            bindService(intent, this, BIND_AUTO_CREATE);
        });
//        buttonSettings.setOnClickListener(view -> {
//            Intent myIntent = new Intent(getBaseContext(), MoreActivity.class);
//            startActivity(myIntent);
//        });

        webView.getSettings().setJavaScriptEnabled(true);
        webView.setWebChromeClient(new WebChromeClient());
        webView.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
        webView.addJavascriptInterface(webAppInterface, "Android");

        try {
            PackageInfo pInfo = this.getPackageManager().getPackageInfo(getPackageName(), 0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                currentVersion = (int) (pInfo.getLongVersionCode() >> 32);
            } else {
                currentVersion = pInfo.versionCode;
            }
        } catch (NameNotFoundException e) {
            Log.e(TAG, "Could not read version info!", e);
        }

        loadSharedPrefs();

        // Set initial position
        setLatLng(lat, lng, LOAD);

            webView.loadUrl(Uri.parse("file:///android_asset/map.html").buildUpon()
                    .appendQueryParameter("lat", "" + lat)
                    .appendQueryParameter("lng", "" + lng)
                    .appendQueryParameter("zoom", "" + zoom)
                    .appendQueryParameter("provider", mapProvider)
                    .build()
                    .toString());

        editTextLat.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (!editTextLat.getText().toString().isEmpty() && !editTextLat.getText().toString().equals("-")) {
                    if (srcChange != CHANGE_FROM_MAP) {
                        try {
                            lat = Double.parseDouble(editTextLat.getText().toString());
                            setLatLng(lat, lng, CHANGE_FROM_EDITTEXT);
                        } catch (Throwable t) {
                            Log.e(TAG, "Could not read latitude!", t);
                        }
                    }
                }
            }
        });

        editTextLng.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (!editTextLng.getText().toString().isEmpty() && !editTextLng.getText().toString().equals("-")) {
                    if (srcChange != CHANGE_FROM_MAP) {
                        try {
                            lng = Double.parseDouble(editTextLng.getText().toString());
                            setLatLng(lat, lng, CHANGE_FROM_EDITTEXT);
                        } catch (Throwable t) {
                            Log.e(TAG, "Could not read longitude!", t);
                        }
                    }
                }
            }
        });


        if (endTime > System.currentTimeMillis()) {
            changeButtonToStop();
        } else {
            endTime = 0;
            saveSettings();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        context = getApplicationContext();
        loadSharedPrefs();
    }

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
        mapProvider = sharedPref.getString("mapProvider", "OpenStreetMap");

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

    protected void applyLocation() {
        if (!hasRootAccess() && !executeAppOpsCommand()) {
            toast(R.string.MainActivity_EnableMockLocation);
            return;
        }

        if (latIsEmpty() || lngIsEmpty()) {
            toast(R.string.MainActivity_NoLatLong);
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
                changeButtonToApply();
            }
        }
    }

    void toast(String str) {
        Toast.makeText(context, str, Toast.LENGTH_SHORT).show();
    }

    void toast(@StringRes int strRes) {
        Toast.makeText(context, strRes, Toast.LENGTH_SHORT).show();
    }

    boolean latIsEmpty() {
        return editTextLat.getText().toString().isBlank();
    }

    boolean lngIsEmpty() {
        return editTextLng.getText().toString().isBlank();
    }

    protected void setMapMarker(double lat, double lng) {
        if (webView == null || webView.getUrl() == null) return;
        webView.loadUrl("javascript:setOnMap(" + lat + "," + lng + ");");
    }

    public void executeJavaScript(String javascript) {
        if (webView == null || webView.getUrl() == null) return;
        webView.loadUrl("javascript:" + javascript);
    }

    void changeButtonToApply() {
        buttonApplyStop.setText(R.string.ActivityMain_Apply);
        buttonApplyStop.setOnClickListener(view -> {
            if (binder == null) {
                Intent intent = new Intent(this, MockedLocationService.class);
                bindService(intent, this, BIND_AUTO_CREATE);
            } else {
                binder.continueMock();
            }
        });
    }

    void changeButtonToStop() {
        buttonApplyStop.setText(R.string.ActivityMain_Stop);
        buttonApplyStop.setOnClickListener(view -> {
            unbindService(this);
            disconnectService();
        });
    }

    public void setZoom(double zoom) {
        this.zoom = zoom;
        saveSettings();
    }

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

        if (srcChange != CHANGE_FROM_EDITTEXT) {
            saveSettings();
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        binder = (MockedLocationService.MockedBinder) service;
        binder.mockState.observe(this, this::onMockedStateChange);
        binder.mockedLocation.observe(this, location -> {
            if (location != null) {
                setLatLng(location.getLatitude(), location.getLongitude(), CHANGE_FROM_MAP);
            }
        });
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        disconnectService();
    }

    private void disconnectService() {
        if (binder != null) {
            binder.mockState.removeObservers(this);
            binder.mockedLocation.removeObservers(this);
        }
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
        firstMock = true;
    }

    private boolean hasRootAccess() {
        String[] paths = {"/system/bin/su", "/system/xbin/su", "/sbin/su", "/data/local/bin/su", "/data/local/xbin/su"};
        for (String path : paths) {
            if (new java.io.File(path).exists()) return true;
        }
        return false;
    }

    private boolean executeAppOpsCommand() {
        try {
            Process process = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(process.getOutputStream());
            os.writeBytes("appops set " + getPackageName() + " android:mock_location allow\n");
            os.writeBytes("exit\n");
            os.flush();
            os.close();
            process.waitFor();
            Thread.sleep(500);
            return process.exitValue() == 0;
        } catch (Exception e) {
            Log.e(TAG, "Failed to execute AppOps command", e);
            return false;
        }
    }

    public enum SourceChange {
        NONE, LOAD, CHANGE_FROM_EDITTEXT, CHANGE_FROM_MAP
    }
}
