package khudyakov.kirill.currencyconverter;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONObject;

public class MainActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener {

    TextView textView;
    SQLiteDatabase database;
    Spinner leftSpinner;
    Spinner rightSpinner;
    String[] currencies;

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

        leftSpinner.setSelection(0);
        rightSpinner.setSelection(1);

        textView = findViewById(R.id.text_view);

        database = openOrCreateDatabase("CurrencyDB", MODE_PRIVATE, null);
        database.execSQL("CREATE TABLE IF NOT EXISTS Quotes (Pair TEXT PRIMARY KEY, Price REAL);");

        currencies = getResources().getStringArray(R.array.currencies);
        database.execSQL("INSERT INTO Quotes VALUES ('USD_RUB', 70);");
        database.execSQL("INSERT INTO Quotes VALUES ('RUB_USD', 70);");
        database.execSQL("INSERT INTO Quotes VALUES ('EUR_RUB', 80);");
        database.execSQL("INSERT INTO Quotes VALUES ('RUB_EUR', 80);");
        database.execSQL("INSERT INTO Quotes VALUES ('EUR_USD', 1.2);");
        database.execSQL("INSERT INTO Quotes VALUES ('USD_EUR', 1.2);");

        updateQuotes();
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        String leftCurrency = leftSpinner.getSelectedItem().toString();
        String rightCurrency = rightSpinner.getSelectedItem().toString();

        String sql = String.format("SELECT * FROM Quotes WHERE Pair = '%s_%s';",
                leftCurrency, rightCurrency);

        Cursor cursor = database.rawQuery(sql,null);
        cursor.moveToFirst();

        String price = cursor.getString(cursor.getColumnIndex("Price"));
        String text = String.format("1 %s = %s %s", leftCurrency, price, rightCurrency);

        textView.setText(text);
        cursor.close();
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }

    void updateQuotes() {
        RequestQueue queue = Volley.newRequestQueue(this);

        for (String c1: currencies) {
            for (String c2: currencies) {
                JsonObjectRequest request = getRequest(c1, c2);
                queue.add(request);
            }
        }
    }

    JsonObjectRequest getRequest(String c1, String c2) {
        String url = "https://free.currencyconverterapi.com/api/v6/convert?q="+ c1 + "_" +
                c2 + "&compact=ultra";

        return new JsonObjectRequest(
                Request.Method.GET, url, null, new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject response) {
                try {
                    String pair = response.names().getString(0);
                    String price = response.getString(pair);
                    String sql = String.format(
                            "INSERT OR REPLACE INTO Quotes(Pair, Price) VALUES('%s', %s);", pair, price);
                    database.execSQL(sql);
                } catch (Exception e) {
                    Log.d("ME", e.toString());
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {}
        });
    }
}
