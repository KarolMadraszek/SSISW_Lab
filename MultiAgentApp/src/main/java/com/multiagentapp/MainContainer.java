package com.multiagentapp;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;

public class MainContainer {
    public static void main(String[] args) {
        try {
            Runtime rt = Runtime.instance();
            Profile p = new ProfileImpl();
            p.setParameter(Profile.MAIN_HOST, "localhost");
            p.setParameter(Profile.GUI, "true");

            AgentContainer mainContainer = rt.createMainContainer(p);

            for (int i = 1; i <= 10; i++) {
                String agentName = String.format("SellerAgent%02d", i);
                AgentController seller = mainContainer.createNewAgent(
                        agentName, SellerAgent.class.getName(), new Object[]{i});
                seller.start();
            }

            AgentController sniffer = mainContainer.createNewAgent(
                    "Sniffer", "jade.tools.sniffer.Sniffer", new Object[]{"*"});
            sniffer.start();

            System.out.println("==================================================");
            System.out.println("Sniffer uruchomiony! Za 10 sekund wystartują kupujący...");
            System.out.println("Ułóż okna na ekranie, aby widzieć wykres.");
            System.out.println("==================================================");

            Thread.sleep(10000);

            for (int i = 1; i <= 5; i++) {
                String agentName = String.format("BuyerAgent%02d", i);
                AgentController buyer = mainContainer.createNewAgent(
                        agentName, BuyerAgent.class.getName(), null);
                buyer.start();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}