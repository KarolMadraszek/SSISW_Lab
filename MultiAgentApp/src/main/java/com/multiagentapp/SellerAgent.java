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
                        if (!inventory.isEmpty()) {
                            Car offeredCar = inventory.get(0);
                            reply.setPerformative(ACLMessage.PROPOSE);
                            try {
                                reply.setContentObject(offeredCar);
                            } catch (Exception e) { e.printStackTrace(); }
                        } else {
                            reply.setPerformative(ACLMessage.REFUSE);
                            reply.setContent("Brak samochodów");
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
                            reply.setContent("Niestety, to auto zostało przed chwilą sprzedane.");
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