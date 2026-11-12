package com.multiagentapp;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

public class DataService {
    private static final String ELASTIC_URL = "http://localhost:9200";
    private static final String ELASTIC_USER = "elastic";
    private static final String ELASTIC_PASS = "123456";

    private static final HttpClient httpClient = createTrustAllHttpClient();

    private static HttpClient createTrustAllHttpClient() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                        public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                        public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                    }
            };
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new SecureRandom());
            return HttpClient.newBuilder().sslContext(sslContext).build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static final ObjectMapper mapper = new ObjectMapper();

    public static List<Car> generateCarsForSeller(String sellerName, int sellerIndex) {
        List<Car> cars = new ArrayList<>();
        List fetchedVehicles = fetchRealCarsFromCepik();

        Random rand = new Random();
        for (int i = 1; i <= 8; i++) {
            int globalCarNumber = (sellerIndex - 1) * 8 + i;
            String carId = String.format("CAR-%03d", globalCarNumber);

            String marka, model, typNadwozia, typSilnika;
            int pojemnosc, rok, moc;

            if (!fetchedVehicles.isEmpty()) {
                Map<String, String> v = (Map<String, String>) fetchedVehicles.get(rand.nextInt(fetchedVehicles.size()));
                marka = v.getOrDefault("marka", "Toyota");
                model = v.getOrDefault("model", "Corolla");
                typNadwozia = v.getOrDefault("nadwozie", "hatchback");
                typSilnika = v.getOrDefault("paliwo", "benzyna");

                try { pojemnosc = (int) Double.parseDouble(v.getOrDefault("pojemnosc", "1598")); }
                catch (Exception e) { pojemnosc = 1598; }

                try { rok = (int) Double.parseDouble(v.getOrDefault("rok", "2021")); }
                catch (Exception e) { rok = 2021; }

                try { moc = (int) Double.parseDouble(v.getOrDefault("moc", "130")); }
                catch (Exception e) { moc = 130; }
            } else {
                marka = "Toyota"; model = "RAV4"; typNadwozia = "SUV";
                typSilnika = "benzyna"; pojemnosc = 1998; rok = 2022; moc = 175;
            }

            double cena = Math.max(3000.0, 5000 + (rok - 1975) * 2000 + rand.nextInt(5000));
            double kosztDodatkowy = 1000 + rand.nextInt(4000);

            Car car = new Car(carId, marka, model, typNadwozia, typSilnika, pojemnosc, rok, cena, kosztDodatkowy, moc, sellerName);
            cars.add(car);

            sendToElastic("cars", carId, car);
        }
        return cars;
    }

    private static List cachedVehicles = null;

    private static synchronized List fetchRealCarsFromCepik() {
        if (cachedVehicles != null && !cachedVehicles.isEmpty()) {
            return cachedVehicles;
        }

        List list = new ArrayList<>();
        try {
            String dynamicUrl = "https://api.cepik.gov.pl/pojazdy?wojewodztwo=10&data-od=20260101&data-do=20260110&limit=400";
            System.out.println(">>> [DEBUG] Wysyłam pierwsze i jedyne zapytanie do: " + dynamicUrl);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(dynamicUrl))
                    .header("Accept", "application/json")
                    .header("User-Agent", "JADE-Lab-Client")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = mapper.readTree(response.body());
                JsonNode dataArray = root.get("data");

                if (dataArray != null && dataArray.isArray() && dataArray.size() > 0) {
                    for (JsonNode node : dataArray) {
                        JsonNode attributes = node.get("attributes");
                        if (attributes != null) {
                            Map<String, String> v = new HashMap<>();
                            v.put("marka", attributes.path("marka").asText("Inna"));
                            v.put("model", attributes.path("model").asText("Model"));
                            v.put("nadwozie", attributes.path("kategoria-pojazdu").asText("osobowy"));
                            v.put("paliwo", attributes.path("rodzaj-paliwa").asText("benzyna"));
                            v.put("pojemnosc", attributes.path("pojemnosc-skokowa-silnika").asText("1998"));
                            v.put("rok", attributes.path("rok-produkcji").asText("2023"));
                            v.put("moc", attributes.path("moc-netto-silnika").asText("150"));
                            list.add(v);
                        }
                    }
                    cachedVehicles = list;
                    System.out.println(">>> [DEBUG] Sukces! Pobrano aut: " + list.size());
                }
            } else {
                System.out.println(">>> [DEBUG] Błąd HTTP: " + response.statusCode() + " | " + response.body());
            }
        } catch (Exception e) {
            System.out.println(">>> [DEBUG] WYJĄTEK SIECIOWY JAVY: " + e.toString());
        }

        return list;
    }

    public static void sendToElastic(String indexName, String docId, Object data) {
        try {
            String json = mapper.writeValueAsString(data);
            String auth = Base64.getEncoder().encodeToString((ELASTIC_USER + ":" + ELASTIC_PASS).getBytes());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ELASTIC_URL + "/" + indexName + "/_doc/" + docId))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Basic " + auth)
                    .PUT(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            System.err.println("Błąd wysyłki do Elasticsearch: " + e.getMessage());
        }
    }
}