package com.multiagentapp;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode rootNode = mapper.readTree(response.body());
                JsonNode dataArray = rootNode.path("data");
                System.out.println("Pobrano " + dataArray.size() + "pojazdów z API.\n");

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


