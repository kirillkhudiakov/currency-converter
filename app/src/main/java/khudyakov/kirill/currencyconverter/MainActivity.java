package khudyakov.kirill.currencyconverter;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
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
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener {

    TextView resultTextView;
    TextView statusTextView;
    Spinner leftSpinner;
    Spinner rightSpinner;
    ProgressBar progressBar;
    SQLiteDatabase database;

    // Значение этого флага можно найти в комментариях метода onItemSelected()
    boolean firstTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Находим все нужные нам элементы.
        resultTextView = findViewById(R.id.text_view);
        statusTextView = findViewById(R.id.status_text_view);

        progressBar = findViewById(R.id.progress_bar);

        // Заполняем выпадающие списки (спиннеры) списком валют
        // и подписываем их на обработчик событий.
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.currencies, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        leftSpinner = findViewById(R.id.first_currency);
        leftSpinner.setAdapter(adapter);
        leftSpinner.setSelection(1);
        leftSpinner.setOnItemSelectedListener(this);

        rightSpinner = findViewById(R.id.second_currency);
        rightSpinner.setAdapter(adapter);
        rightSpinner.setOnItemSelectedListener(this);
        rightSpinner.setSelection(0);


        // Создадим базу данных, в которой будем хранить котировки для того, чтобы использовать
        // приложение даже без интернета.
        String sql = "CREATE TABLE IF NOT EXISTS Quotes " +
                "(Pair TEXT PRIMARY KEY, Price REAL, Date TEXT);";
        database = openOrCreateDatabase("CurrencyDB", MODE_PRIVATE, null);
        database.execSQL(sql);

        firstTime = true;
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        // Данный метод вызывается каждый раз, когда у спиннера меняется выбранный элемент.
        // При запуске приложения этот метод вызовется дважды (для каждого из спиннеров).
        // Поэтому с помощью флага firstTime мы можем пропустить первый вызов метода для того,
        // чтобы не дублировать запросы.
        if (firstTime) {
            firstTime = false;
            return;
        }

        // Сбрасываем предыдущий результат и активируем иконку загрузки.
        resultTextView.setText("");
        statusTextView.setText("");
        progressBar.setVisibility(View.VISIBLE);

        // Получим выбранные на данный момент валюты и попытаемся обновить котировки.
        String leftCurrency = leftSpinner.getSelectedItem().toString();
        String rightCurrency = rightSpinner.getSelectedItem().toString();

        updatePrices(leftCurrency, rightCurrency);
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }

    /**
     * Метод отправляет запрос на получение текущего курса.
     * @param c1 Первая валюта.
     * @param c2 Вторая валюта.
     */
    void updatePrices(String c1, String c2) {
        RequestQueue queue = Volley.newRequestQueue(this);

        // Получим запрос для выбранный валютной пары.
        JsonObjectRequest request = generateRequest(c1, c2);

        // Установим максимальное время в 250мс для нашего запроса.
        request.setRetryPolicy(new DefaultRetryPolicy(250,
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

        // Отправим запрос.
        queue.add(request);
    }

    /**
     * Создает запрос на получение курса валют. Если все прошло успешно, сохраняет изменения в
     * базе данных.
     * @param c1 Первая валюта.
     * @param c2 Вторая валюта.
     * @return Запрос.
     */
    private JsonObjectRequest generateRequest(String c1, String c2) {
        String url = "https://free.currencyconverterapi.com/api/v6/convert?q=" +
                c1 + "_" + c2 + "&compact=ultra";

        return new JsonObjectRequest(
                Request.Method.GET, url, null, new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject response) {
                try {
                    // Если запрос был успешен, добавим полученную информацию в базу данных
                    // с заменой старой информации.
                    String pair = response.names().getString(0);
                    String price = response.getString(pair);
                    String date = getTimestamp();

                    String sql = String.format("INSERT OR REPLACE INTO Quotes(Pair, Price, Date)" +
                            " VALUES('%s', %s, '%s');", pair, price, date);
                    database.execSQL(sql);

                    // Выведем на экран новые данные по валютной паре.
                    printResult(pair, true);
                } catch (Exception e) {
                    statusTextView.setText(e.toString());
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                // Если при запросе произошла ошибка (например, отстутствовал интернет), то мы
                // окажемся здесь. Все равно попробуем вывести ранее сохраненную информацию о курсе
                // валют (если таковая имеется).
                String c1 = leftSpinner.getSelectedItem().toString();
                String c2 = rightSpinner.getSelectedItem().toString();

                printResult(c1 + "_" + c2, false);
            }
        });
    }

    /**
     * Выводит изменившиейся данные на экран пользователя.
     * @param pair Валютная пара.
     * @param success Флаг, который хранит информацию о том, произошла ли ошибка при обновлении
     *                котировок.
     */
    private void printResult(String pair, boolean success) {

        progressBar.setVisibility(View.GONE);

        String leftCurrency = pair.split("_")[0];
        String rightCurrency = pair.split("_")[1];
        String sql = String.format("SELECT * FROM Quotes WHERE Pair = '%s';", pair);

        Cursor cursor = database.rawQuery(sql,null);

        // Если в курсор не содержит ни одной строки, это означает, что запрос прошел неудачно,
        // и ранее пользователь никогда не узнавал данный курс валют.
        if (cursor.getCount() == 0) {
            resultTextView.setText("");
            statusTextView.setText(
                    "Чтобы узнать курс ваших любимых валют, подключитесь к интернету");
            return;
        }

        // Получим курс валют из базы данных.
        cursor.moveToFirst();

        String price = cutDecimals(cursor.getString(1));
        String text = String.format("1 %s = %s %s", leftCurrency, price, rightCurrency);

        // Отобразим, собственно, цену.
        resultTextView.setText(text);

        // Данный метод вызывается после запроса обновления котировок. Параметр success говорит
        // о том получена ли котировка сейчас, или запрос был неудачен, и придется пользоваться
        // старыми данными.
        if (success) {
            statusTextView.setText("Текущий курс");
        } else {
            String date = cursor.getString(2);
            statusTextView.setText(String.format("Курс на %s", date));
        }

        cursor.close();
    }

    /**
     * Выдает текущее время в формате чч.мм дд.мм.гг
     * @return Строка, содержащая время.
     */
    private String getTimestamp() {
        SimpleDateFormat dateFormat =
                new SimpleDateFormat("HH:mm dd.MM.yy", Locale.getDefault());

        return dateFormat.format(new Date());
    }

    /**
     * Оставляет у числа 2 знака после запятой, если у числа вообще есть знаки после запятой.
     * @param number Строковое представление числа.
     * @return Укороченное число.
     */
    private String cutDecimals(String number) {
        int dotPosition = number.indexOf('.');
        if (dotPosition == -1)
            return number;

        return number.substring(0, dotPosition + 3);
    }
}
