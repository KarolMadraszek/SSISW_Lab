package com.multiagentapp;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import java.util.List;

public class SellerAgent extends Agent {
    private List<Car> inventory;

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
                        } catch (NumberFormatException ignored) {}

                        Car bestMatchingCar = null;
                        double lowestTotalCost = Double.MAX_VALUE;

                        synchronized (inventory) {
                            for (Car c : inventory) {
                                if (c.getRokProdukcji() >= minRequiredYear) {
                                    double totalCost = c.getCena() + c.getKosztDodatkowy();
                                    if (totalCost < lowestTotalCost) {
                                        lowestTotalCost = totalCost;
                                        bestMatchingCar = c;
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
                            reply.setContent("Brak samochodu spełniającego wymagania (wymagany min. rok: " + minRequiredYear + ")");
                        }
                        send(reply);

                    } else if (msg.getPerformative() == ACLMessage.ACCEPT_PROPOSAL) {
                        String requestedCarId = msg.getContent();
                        Car carToSell = null;

                        synchronized (inventory) {
                            for (Car c : inventory) {
                                if (c.getId().equals(requestedCarId)) {
                                    carToSell = c;
                                    break;
                                }
                            }
                            if (carToSell != null) {
                                inventory.remove(carToSell);
                            }
                        }

                        ACLMessage reply = msg.createReply();
                        if (carToSell != null) {
                            reply.setPerformative(ACLMessage.INFORM);
                            reply.setContent(carToSell.getId());
                            System.out.println("[" + getLocalName() + "] Sprzedano: " + carToSell.getId() + " dla " + msg.getSender().getLocalName());
                        } else {
                            reply.setPerformative(ACLMessage.FAILURE);
                            reply.setContent("SAMOCHÓD NIEDOSTĘPNY");
                        }
                        send(reply);
                    }
                } else {
                    block();
                }
            }
        });
    }
}