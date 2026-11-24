package com.multiagentapp;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.*;

public class BuyerAgent extends Agent {
    private int purchasedCount = 0;
    private static final int TARGET_PURCHASES = 3;
    private double budget = 100000.0;
    private int failedRounds = 0;
    private static final int MAX_FAILED_ROUNDS = 5;
    private final Random rand = new Random();

    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args != null && args.length > 0 && args[0] instanceof Number) {
            this.budget = ((Number) args[0]).doubleValue();
        }

        System.out.println(getLocalName() + " rozpoczyna poszukiwania (cel: " + TARGET_PURCHASES
                + " auta, budżet: " + Math.round(budget) + " PLN).");

        addBehaviour(new TickerBehaviour(this, 4000) {
            @Override
            protected void onTick() {
                if (purchasedCount >= TARGET_PURCHASES) {
                    System.out.println(">>> " + getLocalName() + " zakupił komplet " + TARGET_PURCHASES
                            + " samochodów (pozostały budżet: " + Math.round(budget) + " PLN) i kończy działanie.");
                    stop();
                    return;
                }

                if (failedRounds >= MAX_FAILED_ROUNDS) {
                    System.out.println(">>> " + getLocalName() + " kończy działanie — brak dostępnych aut w budżecie ("
                            + Math.round(budget) + " PLN). Kupiono: " + purchasedCount + "/" + TARGET_PURCHASES);
                    stop();
                    return;
                }

                try {
                    DFAgentDescription template = new DFAgentDescription();
                    ServiceDescription sd = new ServiceDescription();
                    sd.setType("car-selling");
                    template.addServices(sd);
                    DFAgentDescription[] result = DFService.search(myAgent, template);

                    if (result.length > 0) {
                        int requiredMinYear = 1975 + rand.nextInt(46);
                        String convId = "car-trade-" + UUID.randomUUID();

                        ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
                        for (DFAgentDescription sellerDfd : result) {
                            cfp.addReceiver(sellerDfd.getName());
                        }
                        cfp.setConversationId(convId);
                        cfp.setContent(String.valueOf(requiredMinYear));
                        myAgent.send(cfp);

                        Car bestCar = null;
                        AID bestSeller = null;
                        double bestTotalCost = Double.MAX_VALUE;

                        MessageTemplate mt = MessageTemplate.MatchConversationId(convId);

                        for (int i = 0; i < result.length; i++) {
                            ACLMessage reply = myAgent.blockingReceive(mt, 1500);
                            if (reply == null) break;

                            if (reply.getPerformative() == ACLMessage.PROPOSE) {
                                Car offeredCar = (Car) reply.getContentObject();
                                double cena = offeredCar.getCena();
                                double kosztDodatkowy = offeredCar.getKosztDodatkowy();
                                double kosztCalkowity = cena + kosztDodatkowy;

                                StringBuilder offerLog = new StringBuilder();
                                offerLog.append("\n[").append(getLocalName()).append("] Oferta od ")
                                        .append(reply.getSender().getLocalName())
                                        .append(" (").append(offeredCar.getId()).append(" - ")
                                        .append(offeredCar.getMarka()).append(" ").append(offeredCar.getModel())
                                        .append(", rok ").append(offeredCar.getRokProdukcji()).append("):\n")
                                        .append("[").append(getLocalName()).append("] Cena: ").append(Math.round(cena)).append(" PLN\n")
                                        .append("[").append(getLocalName()).append("] Koszt dodatkowy: ").append(Math.round(kosztDodatkowy)).append(" PLN\n")
                                        .append("[").append(getLocalName()).append("] -----------------------\n")
                                        .append("[").append(getLocalName()).append("] Koszt całkowity: ").append(Math.round(kosztCalkowity)).append(" PLN");

                                synchronized (System.out) {
                                    System.out.println(offerLog.toString());
                                }

                                if (kosztCalkowity > budget) {
                                    StringBuilder rejectLog = new StringBuilder();
                                    rejectLog.append("\n[").append(getLocalName()).append("] ODRZUCONO ofertę (przekroczony budżet) - ")
                                            .append(offeredCar.getId()).append(" (").append(offeredCar.getMarka()).append(" ").append(offeredCar.getModel()).append("):\n")
                                            .append("Budżet: ").append(Math.round(budget)).append(" PLN\n")
                                            .append("Cena: ").append(Math.round(cena)).append(" PLN\n")
                                            .append("Opłaty: ").append(Math.round(kosztDodatkowy)).append(" PLN\n")
                                            .append("-----------------------\n")
                                            .append("Razem: ").append(Math.round(kosztCalkowity)).append(" PLN (Za drogo!)");
                                    synchronized (System.out) {
                                        System.out.println(rejectLog.toString());
                                    }
                                    continue;
                                }

                                if (kosztCalkowity < bestTotalCost) {
                                    bestTotalCost = kosztCalkowity;
                                    bestCar = offeredCar;
                                    bestSeller = reply.getSender();
                                }
                            } else if (reply.getPerformative() == ACLMessage.REFUSE) {
                                synchronized (System.out) {
                                    System.out.println("[" + getLocalName() + "] Odmowa od "
                                            + reply.getSender().getLocalName() + ": " + reply.getContent());
                                }
                            }
                        }

                        if (bestCar != null && bestSeller != null) {
                            ACLMessage accept = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
                            accept.addReceiver(bestSeller);
                            accept.setConversationId(convId);
                            accept.setContent(bestCar.getId());
                            myAgent.send(accept);

                            ACLMessage confirm = myAgent.blockingReceive(mt, 2000);
                            if (confirm != null) {
                                if (confirm.getPerformative() == ACLMessage.INFORM) {
                                    purchasedCount++;
                                    System.out.println("+++ [" + getLocalName() + "] Kupiono auto (" + purchasedCount + "/3): " + bestCar.getMarka() + " " + bestCar.getModel() + " (Koszt całkowity: " + Math.round(bestTotalCost) + " PLN)");

                                    Map<String, Object> tx = new HashMap<>();
                                    tx.put("buyer", getLocalName());
                                    tx.put("seller", bestSeller.getLocalName());
                                    tx.put("carId", bestCar.getId());
                                    tx.put("marka", bestCar.getMarka());
                                    tx.put("model", bestCar.getModel());
                                    tx.put("cena", bestCar.getCena());
                                    tx.put("kosztDodatkowy", bestCar.getKosztDodatkowy());
                                    tx.put("totalCost", bestTotalCost);
                                    tx.put("timestamp", new Date());
                                    DataService.sendToElastic("transactions", UUID.randomUUID().toString(), tx);
                                } else if (confirm.getPerformative() == ACLMessage.FAILURE) {
                                    System.out.println("--- [" + getLocalName() + "] Nie udało się kupić auta: " + confirm.getContent());
                                }
                            }

                        } else {
                            failedRounds++;
                            synchronized (System.out) {
                                System.out.println("[" + getLocalName() + "] Brak ofert mieszczących się w budżecie ("
                                        + Math.round(budget) + " PLN) i kryteriach w tej rundzie.");
                            }
                        }
                    }

                } catch (Exception e) {

                    e.printStackTrace();
                }
            }
        });
    }
}