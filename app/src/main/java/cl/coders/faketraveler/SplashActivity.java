package cl.coders.faketraveler;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        String token = "4489eea6c9f348f8ab6858c945e7c0c0";

        // Ambil deviceId dari Android
        String deviceId = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ANDROID_ID
        );

        checkToken(token, deviceId);
    }

    private void checkToken(String token, String deviceId) {
        OkHttpClient client = new OkHttpClient();

        // URL dengan parameter GET
        String url = "http://thorsi.my.id/api/check_token?token=" + token + "&device_id=" + deviceId;
        Log.d("SplashActivity", "Request URL: " + url);


        Request request = new Request.Builder()
                .url(url)
                .get() // GET request
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace(); // tampilkan stacktrace di Logcat
                runOnUiThread(() -> {
                    Toast.makeText(SplashActivity.this, "Gagal cek token", Toast.LENGTH_SHORT).show();
                    finish();
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
                                // Token valid → lanjut MainActivity
                                Intent intent = new Intent(SplashActivity.this, MainActivity.class);
                                startActivity(intent);
                                finish();
                            } else {
                                String msg = json.optString("message", "Token tidak valid");
                                Toast.makeText(SplashActivity.this, msg, Toast.LENGTH_SHORT).show();
                                finish();
                            }
                        });

                    } catch (JSONException e) {
                        runOnUiThread(() -> {
                            Toast.makeText(SplashActivity.this, "Format respons tidak sesuai", Toast.LENGTH_SHORT).show();
                            finish();
                        });
                    }
                } else {
                    runOnUiThread(() -> {
                        Toast.makeText(SplashActivity.this, "Server error: " + response.code(), Toast.LENGTH_SHORT).show();
                        finish();
                    });
                }
            }
        });
    }
}
