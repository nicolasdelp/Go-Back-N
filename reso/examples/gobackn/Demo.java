package reso.examples.gobackn;

import reso.common.*;
import reso.ethernet.*;
import reso.examples.static_routing.AppSniffer;
import reso.ip.*;
import reso.scheduler.AbstractScheduler;
import reso.scheduler.Scheduler;
import reso.utilities.NetworkBuilder;

public class Demo {

    public static void main(String [] args) {
		AbstractScheduler scheduler= new Scheduler(); //On crée un ordonanceur 
		Network network= new Network(scheduler); //On crée notre réseaux
    	try {
    		final EthernetAddress MAC_ADDR1= EthernetAddress.getByAddress(0x00, 0x26, 0xbb, 0x4e, 0xfc, 0x28); //Adresse MAC du premier noeud
    		final EthernetAddress MAC_ADDR2= EthernetAddress.getByAddress(0x00, 0x26, 0xbb, 0x4e, 0xfc, 0x29); //Adresse MAC du deuxième noeud
    		final IPAddress IP_ADDR1= IPAddress.getByAddress(192, 168, 0, 1); //Adresse IP du premier noeud
    		final IPAddress IP_ADDR2= IPAddress.getByAddress(192, 168, 0, 2); //Adresse IP du deuxième noeud

    		IPHost host1= NetworkBuilder.createHost(network, "H1", IP_ADDR1, MAC_ADDR1); //On crée le premier noeud
    		host1.getIPLayer().addRoute(IP_ADDR2, "eth0"); //On crée une route sur une interface

			//C'est ici qu'on change le nombre de paquets à envoyer
    		host1.addApplication(new AppSender(host1, IP_ADDR2, 50)); //On lui ajoute son application d'envoie

    		IPHost host2= NetworkBuilder.createHost(network,"H2", IP_ADDR2, MAC_ADDR2); //On crée le second noeud
    		host2.getIPLayer().addRoute(IP_ADDR1, "eth0"); //On crée une route sur une interface
    		host2.addApplication(new AppReceiver(host2)); //On lui ajoute son application de réception

    		EthernetInterface h1_eth0= (EthernetInterface) host1.getInterfaceByName("eth0");
    		EthernetInterface h2_eth0= (EthernetInterface) host2.getInterfaceByName("eth0");
    		
    		new Link<EthernetFrame>(h1_eth0, h2_eth0, 5000000, 100000); //On connect les 2 interfaces avec un lien d'une longueur de 5000km avec un débit de 100000 bits/s

            ((IPEthernetAdapter) host2.getIPLayer().getInterfaceByName("eth0")).addARPEntry(IP_ADDR1, MAC_ADDR1);
            ((IPEthernetAdapter) host1.getIPLayer().getInterfaceByName("eth0")).addARPEntry(IP_ADDR2, MAC_ADDR2);

    		host1.start(); //On lance le noeud expéditaire
    		host2.start(); //On lance le noeud receveur
    		
    		scheduler.run(); //On démarre l'ordonanceur
    	} catch (Exception e) {
    		System.err.println(e.getMessage());
    		e.printStackTrace(System.err);
    	}
    }

}
