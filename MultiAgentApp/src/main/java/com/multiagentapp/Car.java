package com.multiagentapp;
import java.io.Serializable;

public class Car implements Serializable {
    private String id;              // np. CAR-001
    private String marka;           // np. Toyota
    private String model;           // np. RAV4
    private String typNadwozia;     // np. SUV
    private String typSilnika;      // np. benzyna
    private int pojemnoscSilnika;   // np. 1998 (cm3)
    private int rokProdukcji;       // np. 2024
    private double cena;            // np. 145000 PLN
    private double kosztDodatkowy;  // np. 3000 PLN
    private int moc;                // pole rozszerzone (KM)
    private String sprzedawca;      // nazwa agenta sprzedającego

    public Car() {}

    public Car(String id, String marka, String model, String typNadwozia, String typSilnika,
               int pojemnoscSilnika, int rokProdukcji, double cena, double kosztDodatkowy, int moc, String sprzedawca) {
        this.id = id;
        this.marka = marka;
        this.model = model;
        this.typNadwozia = typNadwozia;
        this.typSilnika = typSilnika;
        this.pojemnoscSilnika = pojemnoscSilnika;
        this.rokProdukcji = rokProdukcji;
        this.cena = cena;
        this.kosztDodatkowy = kosztDodatkowy;
        this.moc = moc;
        this.sprzedawca = sprzedawca;
    }

    // Gettery i Settery (wymagane przez Jackson do wysyłki do Elasticsearch)
    public String getId() { return id; }
    public String getMarka() { return marka; }
    public String getModel() { return model; }
    public String getTypNadwozia() { return typNadwozia; }
    public String getTypSilnika() { return typSilnika; }
    public int getPojemnoscSilnika() { return pojemnoscSilnika; }
    public int getRokProdukcji() { return rokProdukcji; }
    public double getCena() { return cena; }
    public double getKosztDodatkowy() { return kosztDodatkowy; }
    public int getMoc() { return moc; }
    public String getSprzedawca() { return sprzedawca; }

    @Override
    public String toString() {
        return String.format("ID: %s | %s %s (%s) | Silnik: %s %d cm3 | Rok: %d | Cena: %.0f PLN (+%.0f PLN)",
                id, marka, model, typNadwozia, typSilnika, pojemnoscSilnika, rokProdukcji, cena, kosztDodatkowy);
    }
}