package com.multiagentapp;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class CepikApiClient {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private static final int MAX_PROB = 3;

    private static final Path KATALOG_WYJSCIOWY = Path.of("output");
    private static final Path PLIK_DANYCH =
            KATALOG_WYJSCIOWY.resolve("pojazdy-oczyszczone.json");
    private static final Path PLIK_RAPORTU =
            KATALOG_WYJSCIOWY.resolve("raport-przetwarzania.json");

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
                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }

                    @Override
                    public void checkClientTrusted(
                            X509Certificate[] certs, String authType) {
                    }

                    @Override
                    public void checkServerTrusted(
                            X509Certificate[] certs, String authType) {
                    }
                }
        };

        HttpClient client;

        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new SecureRandom());

            client = HttpClient.newBuilder()
                    .sslContext(sslContext)
                    .build();

        } catch (Exception e) {
            System.err.println("Nie udało się utworzyć klienta HTTP.");
            e.printStackTrace();
            return;
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .header("User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                                + "AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36")
                .GET()
                .build();

        try {
            // 1. Połączenie z API i pobranie danych.
            HttpResponse<String> response = pobierzDane(client, request);

            if (response == null) {
                return;
            }

            if (response.statusCode() != 200) {
                System.err.println("Błąd API. Kod HTTP: "
                        + response.statusCode());
                System.err.println("Odpowiedź: "
                        + skroc(response.body(), 1000));
                return;
            }

            // 2. Analiza formatu odpowiedzi JSON.
            JsonNode rootNode = MAPPER.readTree(response.body());

            JsonNode dataArray = rootNode.get("data");

            if (dataArray == null || !dataArray.isArray()) {
                System.err.println(
                        "Niepoprawna odpowiedź API: brak tablicy data."
                );
                return;
            }

            if (dataArray.isEmpty()) {
                System.out.println(
                        "API zwróciło poprawną odpowiedź, ale brak pojazdów."
                );
                return;
            }

            System.out.println("Pobrano rekordów: " + dataArray.size());

            // 3. Czyszczenie i przygotowanie danych.
            ZbiorPojazdow zbior = new ZbiorPojazdow();
            Raport raport = new Raport();

            raport.zrodlo = url;
            raport.dataPrzetwarzania = LocalDateTime.now().toString();
            raport.pobrano = dataArray.size();

            Set<String> wykorzystaneId = new HashSet<>();
            int aktualnyRok = LocalDateTime.now().getYear();

            for (JsonNode node : dataArray) {

                if (!node.isObject()) {
                    odrzuc(raport, "Rekord nie jest obiektem JSON");
                    continue;
                }

                JsonNode attributes = node.get("attributes");

                if (attributes == null || !attributes.isObject()) {
                    odrzuc(raport, "Brak poprawnego obiektu attributes");
                    continue;
                }

                // Analiza struktury: częstotliwość występowania pól.
                attributes.fieldNames().forEachRemaining(
                        pole -> raport.polaWystepujace.merge(
                                pole, 1, Integer::sum
                        )
                );

                String id = tekst(node.get("id"));
                String producent = tekst(attributes.get("nazwa-producenta"));
                String marka = tekst(attributes.get("marka"));
                String model = tekst(attributes.get("model"));
                String rokTekst = tekst(attributes.get("rok-produkcji"));
                String typPojazdu = tekst(attributes.get("rodzaj-pojazdu"));
                String typNadwozia = tekst(attributes.get("podrodzaj-pojazdu"));
                String paliwo = tekst(attributes.get("rodzaj-paliwa"));
                String pojemnoscTekst =
                        tekst(attributes.get("pojemnosc-skokowa-silnika"));

                // Wymagane pola: marka i prawidłowy rok produkcji.
                if (marka == null) {
                    odrzuc(raport, "Brak marki");
                    continue;
                }

                Integer rok = liczbaCalkowita(rokTekst);

                if (rok == null || rok < 1886 || rok > aktualnyRok + 1) {
                    odrzuc(raport, "Brak lub niepoprawny rok produkcji");
                    continue;
                }

                // Usuwamy duplikaty wyłącznie na podstawie ID z API.
                if (id != null && !wykorzystaneId.add(id)) {
                    raport.duplikaty++;
                    continue;
                }

                // 4. Utworzenie oczyszczonego rekordu.
                Vehicle pojazd = new Vehicle();

                pojazd.id = id;
                pojazd.producent = producent;
                pojazd.marka = marka;
                pojazd.model = model;
                pojazd.rokProdukcji = rok;
                pojazd.typPojazdu = typPojazdu;
                pojazd.typNadwozia = typNadwozia;
                pojazd.rodzajPaliwa = paliwo;
                pojazd.pojemnoscSilnika =
                        liczbaCalkowita(pojemnoscTekst);

                zbior.pojazdy.add(pojazd);
                raport.poprawne++;
            }

            raport.odrzucone =
                    raport.pobrano - raport.poprawne - raport.duplikaty;
            raport.zapisano = zbior.pojazdy.size();

            // 5. Zapis oczyszczonych danych i raportu do JSON.
            Files.createDirectories(KATALOG_WYJSCIOWY);

            MAPPER.writeValue(PLIK_DANYCH.toFile(), zbior);
            MAPPER.writeValue(PLIK_RAPORTU.toFile(), raport);

            // Wyświetlamy tylko podsumowanie, nie wszystkie rekordy.
            System.out.println("\n===== PODSUMOWANIE =====");
            System.out.println("Pobrano:     " + raport.pobrano);
            System.out.println("Poprawne:    " + raport.poprawne);
            System.out.println("Odrzucone:   " + raport.odrzucone);
            System.out.println("Duplikaty:   " + raport.duplikaty);
            System.out.println("Zapisano:    " + raport.zapisano);

            System.out.println("\nPliki zapisano w:");
            System.out.println(PLIK_DANYCH.toAbsolutePath());
            System.out.println(PLIK_RAPORTU.toAbsolutePath());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Pobieranie zostało przerwane.");

        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            System.err.println("API zwróciło niepoprawny JSON.");
            System.err.println(e.getMessage());

        } catch (java.io.IOException e) {
            System.err.println("Błąd połączenia lub zapisu pliku.");
            System.err.println(e.getMessage());

        } catch (Exception e) {
            System.err.println("Nieoczekiwany błąd aplikacji.");
            e.printStackTrace();
        }
    }

    // Pobieranie z ograniczoną liczbą prób.
    private static HttpResponse<String> pobierzDane(
            HttpClient client, HttpRequest request)
            throws java.io.IOException, InterruptedException {

        for (int proba = 1; proba <= MAX_PROB; proba++) {
            try {
                HttpResponse<String> response = client.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

                int status = response.statusCode();

                if (status == 200) {
                    return response;
                }

                // 429 = limit API; 5xx = błąd serwera.
                if ((status == 429 || status >= 500)
                        && proba < MAX_PROB) {

                    System.err.println(
                            "HTTP " + status
                                    + ". Ponawiam próbę "
                                    + (proba + 1) + "/" + MAX_PROB
                    );

                    Thread.sleep(2000L * proba);
                    continue;
                }

                return response;

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;

            } catch (java.io.IOException e) {
                if (proba == MAX_PROB) {
                    throw e;
                }

                System.err.println(
                        "Błąd połączenia. Ponawiam próbę "
                                + (proba + 1) + "/" + MAX_PROB
                );

                Thread.sleep(2000L * proba);
            }
        }

        return null;
    }

    private static void odrzuc(Raport raport, String powod) {
        raport.powodyOdrzucenia.merge(powod, 1, Integer::sum);
    }

    private static String tekst(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }

        String wartosc = node.asText().trim();

        if (wartosc.isEmpty() || wartosc.equalsIgnoreCase("null")) {
            return null;
        }

        return wartosc.replaceAll("\\s+", " ");
    }

    private static Integer liczbaCalkowita(String wartosc) {
        if (wartosc == null) {
            return null;
        }

        try {
            return Integer.valueOf(wartosc);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String skroc(String tekst, int limit) {
        if (tekst == null) {
            return "";
        }

        return tekst.length() <= limit
                ? tekst
                : tekst.substring(0, limit) + "...";
    }

    // Model pojedynczego pojazdu.
    public static class Vehicle {
        public String id;
        public String producent;
        public String marka;
        public String model;
        public Integer rokProdukcji;
        public String typPojazdu;
        public String typNadwozia;
        public String rodzajPaliwa;
        public Integer pojemnoscSilnika;
    }

    // Dokument z pojazdami. Wygodny do późniejszego importu.
    public static class ZbiorPojazdow {
        public String zrodlo = "CEPiK";
        public String dataPrzetwarzania = LocalDateTime.now().toString();
        public List<Vehicle> pojazdy = new ArrayList<>();
    }

    // Raport z procesu czyszczenia.
    public static class Raport {
        public String zrodlo;
        public String dataPrzetwarzania;

        public int pobrano;
        public int poprawne;
        public int odrzucone;
        public int duplikaty;
        public int zapisano;

        public Map<String, Integer> polaWystepujace =
                new LinkedHashMap<>();

        public Map<String, Integer> powodyOdrzucenia =
                new LinkedHashMap<>();
    }
}
