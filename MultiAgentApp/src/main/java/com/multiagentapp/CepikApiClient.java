package com.multiagentapp;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class CepikApiClient {
    public static void main(String[] args) {
        String wojewodztwo = "10";
        String dataOd = "20260101";
        String dataDo = "20260110";
        String limit = "100";

        String url = String.format(
                "https://api.cepik.gov.pl/pojazdy?wojewodztwo=%s&data-od=%s&data-do=%s&limit=%s",
                wojewodztwo, dataOd, dataDo, limit
        );

        TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return null; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) { }
                    public void checkServerTrusted(X509Certificate[] certs, String authType) { }
                }
        };

        HttpClient client = null;
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new SecureRandom());
            client = HttpClient.newBuilder()
                    .sslContext(sslContext)
                    .build();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .GET()
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode rootNode = mapper.readTree(response.body());
                JsonNode dataArray = rootNode.path("data");
                System.out.println("Pobrano " + dataArray.size() + " pojazdów z API.\n");

                for (JsonNode node : dataArray) {
                    JsonNode attributes = node.path("attributes");

                    String producent = attributes.path("nazwa-producenta").asText("Brak danych");
                    String marka = attributes.path("marka").asText("Brak danych");
                    String model = attributes.path("model").asText("Brak danych");
                    String rokProdukcji = attributes.path("rok-produkcji").asText("Brak danych");
                    String typPojazdu = attributes.path("rodzaj-pojazdu").asText("Brak danych");
                    String typNadwozia = attributes.path("podrodzaj-pojazdu").asText("Brak danych");
                    String rodzajPaliwa = attributes.path("rodzaj-paliwa").asText("Brak danych");
                    String pojemnoscSilnika = attributes.path("pojemnosc-skokowa-silnika").asText("Brak danych");

                    System.out.println("---------------");
                    System.out.println("Producent         : " + producent);
                    System.out.println("Marka             : " + marka);
                    System.out.println("Model             : " + model);
                    System.out.println("Rok produkcji     : " + rokProdukcji);
                    System.out.println("Typ pojazdu       : " + typPojazdu);
                    System.out.println("Typ nadwozia      : " + typNadwozia);
                    System.out.println("Rodzaj paliwa     : " + rodzajPaliwa + " (Pojemność: " + pojemnoscSilnika + " cm3)");
                }
                System.out.println("---------------");
            } else {
                System.out.println("Błąd zapytania. Kod HTTP: " + response.statusCode());
            }
        } catch (Exception e) {
            System.err.println("Błąd podczas komunikacji z API CEPiK:");
            e.printStackTrace();
        }
    }
}


