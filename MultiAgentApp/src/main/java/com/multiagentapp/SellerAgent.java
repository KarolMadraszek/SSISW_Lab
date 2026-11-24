package com.multiagentapp;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class SellerAgent extends Agent {
    private static final long RESERVATION_TIME = 5000;
    private List<Car> inventory;
    private final Map<String, Reservation> reservations = new HashMap<>();

    private static class Reservation {
        private final String buyer;
        private final long expiresAt;

        Reservation(String buyer, long expiresAt) {
            this.buyer = buyer;
            this.expiresAt = expiresAt;
        }
    }

    @Override
    protected void setup() {
        Object[] args = getArguments();
        int sellerIndex = (args != null && args.length > 0) ? (int) args[0] : 1;
        inventory = DataService.generateCarsForSeller(getLocalName(), sellerIndex);
        System.out.println(getLocalName() + " gotowy. Posiada aut: " + inventory.size());

        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("car-selling");
        sd.setName("JADE-car-trading");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (Exception e) {
            e.printStackTrace();
        }

        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                ACLMessage msg = receive();
                if (msg != null) {
                    if (msg.getPerformative() == ACLMessage.CFP) {
                        ACLMessage reply = msg.createReply();

                        int minRequiredYear = 1975;
                        try {
                            if (msg.getContent() != null && !msg.getContent().isBlank()) {
                                minRequiredYear = Integer.parseInt(msg.getContent().trim());
                            }
                        } catch (NumberFormatException ignored) {
                        }

                        Car bestMatchingCar = null;
                        double lowestTotalCost = Double.MAX_VALUE;

                        synchronized (inventory) {
                            Iterator<Map.Entry<String, Reservation>> iterator = reservations.entrySet().iterator();

                            while (iterator.hasNext()) {
                                Map.Entry<String, Reservation> entry = iterator.next();

                                if (System.currentTimeMillis() >= entry.getValue().expiresAt) {

                                    System.out.println(
                                            "[" + getLocalName()
                                                    + "] Rezerwacja wygasła: "
                                                    + entry.getKey());

                                    iterator.remove();
                                }
                            }
                            for (Car c : inventory) {

                                if (reservations.containsKey(c.getId())) {
                                    continue;
                                }
                                if (c.getRokProdukcji() >= minRequiredYear) {
                                    double totalCost = c.getCena() + c.getKosztDodatkowy();
                                    if (totalCost < lowestTotalCost) {
                                        lowestTotalCost = totalCost;
                                        bestMatchingCar = c;

                                    } else if (Double.compare(
                                            totalCost,
                                            lowestTotalCost) == 0) {

                                        if (bestMatchingCar == null
                                                || c.getCena() < bestMatchingCar.getCena()) {

                                            bestMatchingCar = c;
                                        }
                                    }
                                }
                            }
                        }

                        if (bestMatchingCar != null) {
                            reply.setPerformative(ACLMessage.PROPOSE);
                            try {
                                reply.setContentObject(bestMatchingCar);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        } else {
                            reply.setPerformative(ACLMessage.REFUSE);
                            reply.setContent("Brak samochodu spełniającego wymagania (wymagany min. rok: "
                                    + minRequiredYear + ")");
                        }

                        send(reply);

                    } else if (msg.getPerformative() == ACLMessage.ACCEPT_PROPOSAL) {
                        String requestedCarId = msg.getContent();
                        Car carToReserve = null;

                        synchronized (inventory) {
                            Iterator<Map.Entry<String, Reservation>> iterator = reservations.entrySet().iterator();

                            while (iterator.hasNext()) {
                                Map.Entry<String, Reservation> entry = iterator.next();

                                if (System.currentTimeMillis() >= entry.getValue().expiresAt) {

                                    System.out.println(
                                            "[" + getLocalName()
                                                    + "] Rezerwacja wygasła: "
                                                    + entry.getKey());

                                    iterator.remove();
                                }
                            }

                            for (Car c : inventory) {

                                if (c.getId().equals(requestedCarId)) {
                                    carToReserve = c;
                                    break;
                                }
                            }

                            if (carToReserve != null
                                    && !reservations.containsKey(requestedCarId)) {

                                reservations.put(
                                        requestedCarId,
                                        new Reservation(
                                                msg.getSender().getLocalName(),
                                                System.currentTimeMillis()
                                                        + RESERVATION_TIME));

                            } else {
                                carToReserve = null;
                            }
                        }

                        ACLMessage reply = msg.createReply();

                        if (carToReserve != null) {

                            reply.setPerformative(
                                    ACLMessage.INFORM);

                            reply.setContent(
                                    "RESERVED:" + carToReserve.getId());

                            System.out.println(
                                    "["
                                            + getLocalName()
                                            + "] Zarezerwowano: "
                                            + carToReserve.getId()
                                            + " dla "
                                            + msg.getSender().getLocalName()
                                            + " na 5 sekund.");

                        } else {

                            reply.setPerformative(
                                    ACLMessage.FAILURE);

                            reply.setContent(
                                    "SAMOCHÓD NIEDOSTĘPNY");

                            System.out.println(
                                    "["
                                            + getLocalName()
                                            + "] "
                                            + msg.getSender().getLocalName()
                                            + " nie może zarezerwować "
                                            + requestedCarId
                                            + " - samochód niedostępny.");
                        }

                        send(reply);

                    } else if (msg.getPerformative() == ACLMessage.CONFIRM) {

                        String requestedCarId = msg.getContent();

                        Car carToSell = null;

                        synchronized (inventory) {

                            Reservation reservation = reservations.get(requestedCarId);

                            if (reservation != null) {

                                if (System.currentTimeMillis() < reservation.expiresAt) {

                                    if (reservation.buyer.equals(
                                            msg.getSender().getLocalName())) {

                                        for (Car c : inventory) {

                                            if (c.getId().equals(
                                                    requestedCarId)) {

                                                carToSell = c;
                                                break;
                                            }
                                        }

                                        if (carToSell != null) {
                                            inventory.remove(carToSell);
                                        }

                                        reservations.remove(
                                                requestedCarId);
                                    }
                                } else {

                                    reservations.remove(
                                            requestedCarId);
                                }
                            }
                        }

                        ACLMessage reply = msg.createReply();

                        if (carToSell != null) {

                            reply.setPerformative(
                                    ACLMessage.INFORM);

                            reply.setContent(
                                    carToSell.getId());

                            System.out.println(
                                    "["
                                            + getLocalName()
                                            + "] Sprzedano: "
                                            + carToSell.getId()
                                            + " dla "
                                            + msg.getSender().getLocalName());

                        } else {

                            reply.setPerformative(
                                    ACLMessage.FAILURE);

                            reply.setContent(
                                    "SAMOCHÓD NIEDOSTĘPNY");

                            System.out.println(
                                    "["
                                            + getLocalName()
                                            + "] Nie udało się sprzedać "
                                            + requestedCarId
                                            + " - rezerwacja wygasła "
                                            + "lub auto jest już niedostępne.");
                        }

                        send(reply);

                    } else if (msg.getPerformative() == ACLMessage.CANCEL) {

                        /*
                         * CANCEL = kupujący rezygnuje z rezerwacji.
                         */
                        String requestedCarId = msg.getContent();

                        synchronized (inventory) {

                            Reservation reservation = reservations.get(requestedCarId);

                            if (reservation != null
                                    && reservation.buyer.equals(
                                            msg.getSender().getLocalName())) {

                                reservations.remove(
                                        requestedCarId);

                                System.out.println(
                                        "["
                                                + getLocalName()
                                                + "] Zwolniono rezerwację: "
                                                + requestedCarId
                                                + " przez "
                                                + msg.getSender().getLocalName());
                            }
                        }

                        ACLMessage reply = msg.createReply();

                        reply.setPerformative(
                                ACLMessage.INFORM);

                        reply.setContent(
                                "RELEASED:" + requestedCarId);

                        send(reply);
                    }

                } else {
                    block();
                }
            }
        });
    }
}