package com.multiagentapp;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import java.util.*;

public class BuyerAgent extends Agent {
    private int purchasedCount = 0;
    private static final int TARGET_PURCHASES = 3;

    @Override
    protected void setup() {
        System.out.println(getLocalName() + " rozpoczyna poszukiwania (cel: 3 samochody).");

        addBehaviour(new TickerBehaviour(this, 3000) {
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
                        AID seller = result[new Random().nextInt(result.length)].getName();
                        ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
                        cfp.addReceiver(seller);
                        cfp.setConversationId("car-trade");
                        myAgent.send(cfp);

                        ACLMessage reply = myAgent.blockingReceive(2000);
                        if (reply != null && reply.getPerformative() == ACLMessage.PROPOSE) {
                            Car car = (Car) reply.getContentObject();

                            ACLMessage accept = reply.createReply();
                            accept.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
                            accept.setContent(car.getId());
                            myAgent.send(accept);

                            ACLMessage confirm = myAgent.blockingReceive(2000);
                            if (confirm != null) {
                                if (confirm.getPerformative() == ACLMessage.INFORM) {
                                    purchasedCount++;
                                    System.out.println("+++ [" + getLocalName() + "] Kupiono auto (" + purchasedCount + "/3): " + car.getMarka() + " " + car.getModel());

                                    Map<String, Object> tx = new HashMap<>();
                                    tx.put("buyer", getLocalName());
                                    tx.put("seller", seller.getLocalName());
                                    tx.put("carId", car.getId());
                                    tx.put("marka", car.getMarka());
                                    tx.put("model", car.getModel());
                                    tx.put("totalCost", car.getCena() + car.getKosztDodatkowy());
                                    tx.put("timestamp", new Date());
                                    DataService.sendToElastic("transactions", UUID.randomUUID().toString(), tx);

                                } else if (confirm.getPerformative() == ACLMessage.FAILURE) {
                                    System.out.println("--- [" + getLocalName() + "] Odrzucono zakup: " + confirm.getContent());
                                }
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