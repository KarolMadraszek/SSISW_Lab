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
    private final Random rand = new Random();

    @Override
    protected void setup() {
        System.out.println(getLocalName() + " rozpoczyna poszukiwania (cel: 3 samochody).");

        addBehaviour(new TickerBehaviour(this, 4000) {
            @Override
            protected void onTick() {
                if (purchasedCount >= TARGET_PURCHASES) {
                    System.out.println(">>> " + getLocalName() + " zakupił komplet 3 samochodów i kończy działanie.");
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

                        System.out.println("\n[" + getLocalName() + "] Wysłano zapytanie o auto (min. rocznik: " + requiredMinYear + ")");

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

                                System.out.println("Oferta od " + reply.getSender().getLocalName() + " (" + offeredCar.getMarka() + " " + offeredCar.getModel() + ", rok " + offeredCar.getRokProdukcji() + "):");
                                System.out.println("Cena: " + Math.round(cena) + " PLN");
                                System.out.println("Koszt dodatkowy: " + Math.round(kosztDodatkowy) + " PLN");
                                System.out.println("-----------------------");
                                System.out.println("Koszt całkowity: " + Math.round(kosztCalkowity) + " PLN\n");

                                if (kosztCalkowity < bestTotalCost) {
                                    bestTotalCost = kosztCalkowity;
                                    bestCar = offeredCar;
                                    bestSeller = reply.getSender();
                                }
                            } else if (reply.getPerformative() == ACLMessage.REFUSE) {
                                System.out.println("[" + reply.getSender().getLocalName() + "] Odmowa: " + reply.getContent());
                            }
                        }

                        if (bestCar != null && bestSeller != null) {
                            System.out.println("[" + getLocalName() + "] Wybrano najkorzystniejszą ofertę od " + bestSeller.getLocalName() + " za " + Math.round(bestTotalCost) + " PLN.");

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
                            System.out.println("[" + getLocalName() + "] Żaden sprzedawca nie posiadał auta spełniającego wymagania w tej rundzie.");
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }
}