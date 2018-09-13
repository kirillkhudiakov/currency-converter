package khudyakov.kirill.currencyconverter;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener {

    TextView textView;
    TextView statusTextView;
    SQLiteDatabase database;
    Spinner leftSpinner;
    Spinner rightSpinner;
    String[] currencies;
    ProgressBar progressBar;
    boolean firsttime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        leftSpinner = findViewById(R.id.first_currency);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.currencies, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        leftSpinner.setAdapter(adapter);

        rightSpinner = findViewById(R.id.second_currency);
        rightSpinner.setAdapter(adapter);

        leftSpinner.setOnItemSelectedListener(this);
        rightSpinner.setOnItemSelectedListener(this);

        leftSpinner.setSelection(1);
        rightSpinner.setSelection(0);

        textView = findViewById(R.id.text_view);
        statusTextView = findViewById(R.id.status_text_view);

        database = openOrCreateDatabase("CurrencyDB", MODE_PRIVATE, null);
        database.execSQL("CREATE TABLE IF NOT EXISTS Quotes (Pair TEXT PRIMARY KEY, Price REAL, Date TEXT);");

        currencies = getResources().getStringArray(R.array.currencies);

        firsttime = true;
        progressBar = findViewById(R.id.progress_bar);
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (firsttime) {
            firsttime = false;
            return;
        }
        textView.setText("");
        statusTextView.setText("");
        Log.d("ME", "YEAAA");
        progressBar.setVisibility(View.VISIBLE);
        String leftCurrency = leftSpinner.getSelectedItem().toString();
        String rightCurrency = rightSpinner.getSelectedItem().toString();
        updatePrices(leftCurrency, rightCurrency);
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }

    void updatePrices(String c1, String c2) {
        RequestQueue queue = Volley.newRequestQueue(this);
        JsonObjectRequest request = generateRequest(c1, c2);
        request.setRetryPolicy(new DefaultRetryPolicy(250,
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        queue.add(request);
    }

    void printResult(String pair, boolean success) {
        String leftCurrency = pair.split("_")[0];
        String rightCurrency = pair.split("_")[1];
        String sql = String.format("SELECT * FROM Quotes WHERE Pair = '%s';", pair);

        Cursor cursor = database.rawQuery(sql,null);
        if (cursor.getCount() == 0) {
            textView.setText("Отстутствует подключение к интернету");
            statusTextView.setText("");
            return;
        }
        cursor.moveToFirst();

        String price = cursor.getString(cursor.getColumnIndex("Price"));
        String text = String.format("1 %s = %s %s", leftCurrency, price, rightCurrency);

        textView.setText(text);

        if (success) {
            statusTextView.setText("Текущий курс");
        } else {
            String date = cursor.getString(2);
            statusTextView.setText("Курс на " + date);
        }

        cursor.close();
    }

    JsonObjectRequest generateRequest(String c1, String c2) {
        String url = "https://free.currencyconverterapi.com/api/v6/convert?q="+ c1 + "_" +
                c2 + "&compact=ultra";

        return new JsonObjectRequest(
                Request.Method.GET, url, null, new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject response) {
                try {
                    String pair = response.names().getString(0);
                    String price = response.getString(pair);
                    String date = getTimestamp();
                    String sql = String.format(
                            "INSERT OR REPLACE INTO Quotes(Pair, Price, Date) VALUES('%s', %s, '%s');", pair, price, date);
                    database.execSQL(sql);
                    printResult(pair, true);
                    progressBar.setVisibility(View.GONE);
                    Log.d("ME", "End in onresponse");
                } catch (Exception e) {
                    Log.d("ME", e.toString());
                    Log.d("ME", "End in onresponse catch");
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.d("ME", "End in onerror");
                progressBar.setVisibility(View.GONE);
                String c1 = leftSpinner.getSelectedItem().toString();
                String c2 = rightSpinner.getSelectedItem().toString();
                printResult(c1 + "_" + c2, false);
            }
        });
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager
                = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    String getTimestamp() {
        String[] timestamp = new Date().toString().split(" ");
        String month = timestamp[1];
        String day = timestamp[2];
        String time = timestamp[3].substring(0, 5);
        String year = timestamp[5].substring(2);
        SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm dd.MM.yy");

        return dateFormat.format(new Date());
    }
}
