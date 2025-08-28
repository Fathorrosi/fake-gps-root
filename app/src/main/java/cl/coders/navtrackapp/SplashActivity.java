package cl.coders.navtrackapp;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class SplashActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "AppPrefs";
    private static final String KEY_TOKEN = "user_token";

    private LinearLayout inputLayout;
    private ProgressBar progressBar;
    private EditText tokenInput;
    private Button submitBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        inputLayout = findViewById(R.id.inputLayout);
        progressBar = findViewById(R.id.progressBar);
        tokenInput = findViewById(R.id.tokenInput);
        submitBtn = findViewById(R.id.submitBtn);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String savedToken = prefs.getString(KEY_TOKEN, null);

        if (savedToken != null) {
            // Token sudah pernah disimpan → langsung validasi
            showLoading(true);
            String deviceId = getAndroidId();
            checkToken(savedToken, deviceId, false);
        } else {
            // Belum ada token → tampilkan input
            showLoading(false);
            submitBtn.setOnClickListener(v -> {
                String token = tokenInput.getText().toString().trim();
                if (token.isEmpty()) {
                    Toast.makeText(this, "Token tidak boleh kosong", Toast.LENGTH_SHORT).show();
                } else {
                    showLoading(true);
                    String deviceId = getAndroidId();
                    checkToken(token, deviceId, true);
                }
            });
        }
    }

    private String getAndroidId() {
        return Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ANDROID_ID
        );
    }


    private void checkToken(String token, String deviceId, boolean saveIfValid) {
        OkHttpClient client = new OkHttpClient();
        String url = "http://thorsi.my.id/api/check_token?token=" + token + "&device_id=" + deviceId;
        Log.d("SplashActivity", "Request URL: " + url);

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    showLoading(false);
                    Toast.makeText(SplashActivity.this, "Gagal cek token", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String body = response.body().string();
                    try {
                        JSONObject json = new JSONObject(body);
                        String status = json.getString("status");

                        runOnUiThread(() -> {
                            if ("valid".equalsIgnoreCase(status)) {
                                if (saveIfValid) {
                                    // simpan token agar tidak ditanya lagi
                                    getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                                            .edit()
                                            .putString(KEY_TOKEN, token)
                                            .apply();
                                }
                                // Lanjut MainActivity
                                startActivity(new Intent(SplashActivity.this, MainActivity.class));
                                finish();
                            } else {
                                showLoading(false);
                                String msg = json.optString("message", "Token tidak valid");
                                Toast.makeText(SplashActivity.this, msg, Toast.LENGTH_SHORT).show();
                            }
                        });

                    } catch (JSONException e) {
                        runOnUiThread(() -> {
                            showLoading(false);
                            Toast.makeText(SplashActivity.this, "Format respons tidak sesuai", Toast.LENGTH_SHORT).show();
                        });
                    }
                } else {
                    runOnUiThread(() -> {
                        showLoading(false);
                        Toast.makeText(SplashActivity.this, "Server error: " + response.code(), Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    private void showLoading(boolean loading) {
        if (loading) {
            inputLayout.setVisibility(LinearLayout.GONE);
            progressBar.setVisibility(ProgressBar.VISIBLE);
        } else {
            inputLayout.setVisibility(LinearLayout.VISIBLE);
            progressBar.setVisibility(ProgressBar.GONE);
        }
    }
}
