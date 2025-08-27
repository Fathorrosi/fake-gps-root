package cl.coders.navtrackapp;

import static cl.coders.navtrackapp.MainActivity.SourceChange.CHANGE_FROM_MAP;

import android.util.Log;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;


public class WebAppInterface {

    private final MainActivity mainActivity;

    public WebAppInterface(MainActivity mA) {
        mainActivity = mA;
    }

    /**
     * Set position in GUI. This method is called by javascript when there is a long press in the map.
     *
     * @param str String containing lat and lng
     */
    @JavascriptInterface
    public void setPosition(final String str) {
        mainActivity.runOnUiThread(() -> {
            String lat = str.substring(str.indexOf('(') + 1, str.indexOf(','));
            String lng = str.substring(str.indexOf(',') + 2, str.indexOf(')'));

            try {
                mainActivity.setLatLng(Double.parseDouble(lat), Double.parseDouble(lng), CHANGE_FROM_MAP);
            } catch (Throwable t) {
                Log.e(WebAppInterface.class.toString(), "Could not set new position from map!", t);
            }
        });
    }

    @JavascriptInterface
    public void setZoom(final String str) {
        mainActivity.runOnUiThread(() -> {
            try {
                mainActivity.setZoom(Double.parseDouble(str));
            } catch (Throwable t) {
                Log.e(WebAppInterface.class.toString(), "Could not save zoom!", t);
            }
        });
    }

    /**
     * Search for an address using Nominatim API. This method is called by javascript when user searches for an address.
     *
     * @param query The address query string
     */
    @JavascriptInterface
    public void searchAddress(final String query) {
        mainActivity.runOnUiThread(() -> {
            new Thread(() -> {
                try {
                    String encodedQuery = URLEncoder.encode(query, "UTF-8");
                    String apiUrl = "https://nominatim.openstreetmap.org/search?q=" + encodedQuery + "&format=json&limit=1";
                    
                    URL url = new URL(apiUrl);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setRequestProperty("User-Agent", "NavTrack/1.0");
                    
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();
                    
                    JSONArray results = new JSONArray(response.toString());
                    if (results.length() > 0) {
                        JSONObject firstResult = results.getJSONObject(0);
                        String lat = firstResult.getString("lat");
                        String lon = firstResult.getString("lon");
                        
                        // Call back to JavaScript with the result
                        mainActivity.runOnUiThread(() -> {
                            mainActivity.executeJavaScript("handleSearchResult('" + results.toString() + "')");
                        });
                    }
                    
                    connection.disconnect();
                } catch (Exception e) {
                    Log.e(WebAppInterface.class.toString(), "Error searching address: " + query, e);
                }
            }).start();
        });
    }

}
