package reso.examples.gobackn;

import reso.common.AbstractApplication;
import reso.ip.IPAddress;
import reso.ip.IPHost;
import java.util.Random;

public class AppSender extends AbstractApplication { 
	
    private final IPAddress dst;
    private final int numberOfPackets;

    /**
     * Constructeur du noeud qui envoie les paquets
     * @param host L'IP du noeud
     * @param dst L'IP du noeud qui reçoit les paquets
     * @param numberOfPackets Le nombre de paquets à envoyer
     */
    public AppSender(IPHost host, IPAddress dst, int numberOfPackets) {	
    	super(host, "sender");
    	this.dst = dst;
    	this.numberOfPackets = numberOfPackets;
    }

    /**
     * Méthode qui démarre le noeud
     * @throws Exception Une exception qui pourrait être levé
     */
    @Override
    public void start() throws Exception {
        GoBackNProtocol transport = new GoBackNProtocol((IPHost) host); //Couche transport
        Random rand = new Random();
        for(int i=0; i < numberOfPackets; i++){
            transport.sendData(rand.nextInt(), dst);
        }
    }
    
    public void stop() {}
    
}

