
import com.activfinancial.contentplatform.contentgatewayapi.common.UsEquityOptionHelper;
import com.activfinancial.contentplatform.contentgatewayapi.consts.Exchange;
import com.activfinancial.middleware.activbase.MiddlewareException;
import com.activfinancial.middleware.fieldtypes.Rational;
import org.javatuples.Quartet;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.activfinancial.middleware.fieldtypes.Date;

// https://query2.finance.yahoo.com/v7/finance/options/META?date=1659657600

public class OptionChain {

    private static final Logger LOGGER = Logger.getLogger(OptionChain.class.getName());

    private static final String CSV_FILE_NAME = "/Users/chaklader/IdeaProjects/OptionChain/data/data.csv";

    private static HttpURLConnection conn;

    private final static String BASE_URL = "https://query2.finance.yahoo.com/v7/finance/options/";







    public static void main(String[] args) throws IOException {

        final OptionChain optionChain = new OptionChain();

        String[] equities = {"META"};

        List<String[]> dataLinesForAllEquities = new ArrayList<>();

        for (String equity : equities) {

            final List<String> optionsChain = getCompleteOptionsChainForGivenEquity(equity);

            for (String optionContract : optionsChain) {

                final String activType_b_hashCode = convertOsiSymbolToActivType_B_HashCode(optionContract);
                if (activType_b_hashCode.isEmpty()) {
                    continue;
                }

                String[] data = {equity, optionContract, activType_b_hashCode};
                dataLinesForAllEquities.add(data);
            }
        }

        optionChain.givenDataArray_whenConvertToCSV_thenOutputCreated(dataLinesForAllEquities);
    }


    public void givenDataArray_whenConvertToCSV_thenOutputCreated(List<String[]> dataLines) throws IOException {
        File csvOutputFile = new File(CSV_FILE_NAME);
        try (PrintWriter pw = new PrintWriter(csvOutputFile)) {
            dataLines.stream()
                .map(this::convertToCSV)
                .forEach(pw::println);
        }

    }

    public String convertToCSV(String[] data) {
        return Stream.of(data)
            .map(this::escapeSpecialCharacters)
            .collect(Collectors.joining(","));
    }

    public String escapeSpecialCharacters(String data) {
        String escapedData = data.replaceAll("\\R", " ");
        if (data.contains(",") || data.contains("\"") || data.contains("'")) {
            data = data.replace("\"", "\"\"");
            escapedData = "\"" + data + "\"";
        }
        return escapedData;
    }

    public static String convertOsiSymbolToActivType_B_HashCode(String osiSymbol) {

        final Quartet<String, String, String, String> optionsData = createOptionsData(osiSymbol);

        final String curSymbol = optionsData.getValue0();
        final String expirationDate = optionsData.getValue1();
        final String strikePrice = optionsData.getValue2();
        final String optionType = optionsData.getValue3();


        try {
            final String activHashCode = getActivType_B_HashCode(curSymbol, expirationDate, strikePrice, optionType);
            return activHashCode;
        } catch (MiddlewareException e) {
            e.printStackTrace();
        }

        return "";
    }


    public static String getActivType_B_HashCode(String symbol, String expirationDate, String strikePrice, String optionType) throws MiddlewareException {

        final String[] split = expirationDate.split("-");
        final int dateVal = Integer.parseInt(split[0]);
        final int monthVal = Integer.parseInt(split[1]);
        final int yearVal = Integer.parseInt(split[2]);

        UsEquityOptionHelper helper = new UsEquityOptionHelper();

        helper.setExchange(Exchange.EXCHANGE_US_OPTIONS_COMPOSITE);
        helper.setRoot(symbol);

        final UsEquityOptionHelper.OptionType optionTypeEnum = optionType.equalsIgnoreCase("C") ? UsEquityOptionHelper.OptionType.OPTION_TYPE_CALL : UsEquityOptionHelper.OptionType.OPTION_TYPE_PUT;
        helper.setExpirationDateAndOptionType(new Date(yearVal, monthVal, dateVal), optionTypeEnum);

        final double strikePriceUpdated = Double.parseDouble(strikePrice) * 10;
        helper.setStrikePrice(new Rational((long) strikePriceUpdated, Rational.Denominator.DENOMINATOR_1DP));

        StringBuilder sb = new StringBuilder();
        helper.serialize(sb);

        return sb.toString();
    }

    private static Quartet<String, String, String, String> createOptionsData(String osiSymbol) {

        StringBuilder stringBuilder = new StringBuilder();

        int index = -1;

        for (int k = 0; k < osiSymbol.length(); k++) {
            if (Character.isLetter(osiSymbol.charAt(k))) {
                stringBuilder.append(osiSymbol.charAt(k));
            } else {
                index = k;
                break;
            }
        }

        String symbol = stringBuilder.toString();

        String restOfStr = osiSymbol.substring(index);

        String str = restOfStr.substring(0, 6);
        final List<String> strings = splitStringEqually(str, 2);

        String DASH = "-";
        String expirationDate = strings.get(2) + DASH + strings.get(1) + DASH + "20" + strings.get(0);
        String optionType = restOfStr.substring(6, 7);


        restOfStr = restOfStr.substring(7);
        final String strikePrice = String.valueOf(Double.parseDouble(restOfStr) / 1000);


        List<String> listOfFields = Arrays.asList(symbol, expirationDate, strikePrice, optionType);
        Quartet<String, String, String, String> quartet = Quartet.fromCollection(listOfFields);

        return quartet;
    }

    public static List<String> splitStringEqually(String text, int size) {
        List<String> result = new ArrayList<String>((text.length() + size - 1) / size);
        for (int i = 0; i < text.length(); i += size) {
            result.add(text.substring(i, Math.min(text.length(), i + size)));
        }
        return result;
    }

    public static List<String> getCompleteOptionsChainForGivenEquity(String equityName) {


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
