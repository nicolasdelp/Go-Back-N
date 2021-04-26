package reso.examples.gobackn;

import reso.common.*;
import reso.ip.IPAddress;
import reso.ip.IPHost;
import reso.ip.IPLayer;

public class AppSender extends AbstractApplication {

    private final IPLayer ip;
    private final IPAddress dst;
    
    //Noeud qui envoie le paquet et recoit ACK ou NAK
    public AppSender(IPHost host, IPAddress dst) {
        super(host, "sender");
    	this.dst= dst;
    	ip= host.getIPLayer();
    }

    @Override
    public void start() throws Exception {
        // TODO
        
    }

    @Override
    public void stop() {
        // Quand il n'y a plus de paquets
        
    }
    
}
