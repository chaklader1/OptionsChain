
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

// https://query2.finance.yahoo.com/v7/finance/options/META?date=1659657600

public class OptionChain {

    private static final Logger LOGGER = Logger.getLogger(OptionChain.class.getName());

    private static HttpURLConnection conn;

    private final static String BASE_URL = "https://query2.finance.yahoo.com/v7/finance/options/";

    public static void main(String[] args) {

        String[] equities = {"META"};

        final List<String> optionsChain = getCompleteOptionsChainForGivenEquity("META");
        System.out.println(optionsChain);
    }




    public static List<String> getCompleteOptionsChainForGivenEquity(String equityName){


        List<String> completeOptionsChain = new ArrayList<>();
        final Map<String, List<String>> equitySymbolAndExpirationDatesMap = createEquitySymbolAndExpirationDatesMap(equityName);

        for (Map.Entry<String, List<String>> entry : equitySymbolAndExpirationDatesMap.entrySet()) {

            final String equitySymbol = entry.getKey();
            final List<String> expirationDatesList = entry.getValue();


            for (String expirationDate : expirationDatesList) {

                final String optionUrlWithExpirationDate = BASE_URL + equitySymbol + "?date=" + expirationDate;
                final String responseStr = getJsonStringFromURL(optionUrlWithExpirationDate);

                JSONObject originalJsonObject = new JSONObject(responseStr);
                final JSONArray jsonArray = originalJsonObject.getJSONObject("optionChain").getJSONArray("result");
                final JSONArray options = jsonArray.getJSONObject(0).getJSONArray("options");

                final List<String> calls = getOptionsChain(options, "calls");
                final List<String> puts = getOptionsChain(options, "puts");


                completeOptionsChain.addAll(calls);
                completeOptionsChain.addAll(puts);
            }
        }

        return completeOptionsChain;
    }

    public static List<String> getOptionsChain(JSONArray options, String type) {

        List<String> optionsChain = new ArrayList<>();
        final JSONArray calls = options.getJSONObject(0).getJSONArray(type);


        for (int i = 0; i < calls.length(); i++) {

            final String s = calls.get(i).toString();
            JSONObject callObj = new JSONObject(s);
            final String contractSymbol = callObj.getString("contractSymbol");
            optionsChain.add(contractSymbol);
        }

        return optionsChain;
    }


    public static Map<String, List<String>> createEquitySymbolAndExpirationDatesMap(String equitySymbol) {


        Map<String, List<String>> equitySymbolAndExpirationDatesMap = new LinkedHashMap<>();


        List<String> expirationDateList = new ArrayList<>();


        final String s = getJsonStringFromURL(BASE_URL + equitySymbol);

        JSONObject originalJsonObject = new JSONObject(s);

        final JSONArray jsonArray = originalJsonObject.getJSONObject("optionChain").getJSONArray("result");
        final JSONArray expirationDates = jsonArray.getJSONObject(0).getJSONArray("expirationDates");


        for (int i = 0; i < expirationDates.length(); i++) {
            final String expDate = expirationDates.get(i).toString();

            expirationDateList.add(expDate);
        }

        equitySymbolAndExpirationDatesMap.put(equitySymbol, expirationDateList);

        return equitySymbolAndExpirationDatesMap;
    }


    public static String getJsonStringFromURL(String URL) {

        String responseString = "";

        BufferedReader reader;
        String line;
        StringBuilder responseContent = new StringBuilder();

        try {
            URL url = new URL(URL);
            conn = (HttpURLConnection) url.openConnection();

            // Request setup
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);// 5000 milliseconds = 5 seconds
            conn.setReadTimeout(5000);

            // Test if the response from the server is successful
            int status = conn.getResponseCode();

            if (status >= 300) {
                reader = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
                while ((line = reader.readLine()) != null) {
                    responseContent.append(line);
                }
                reader.close();
            } else {
                reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                while ((line = reader.readLine()) != null) {
                    responseContent.append(line);
                }
                reader.close();
            }
            LOGGER.info("response code: " + status);

            responseString = responseContent.toString();


        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            conn.disconnect();
        }


        return responseString;
    }


}
