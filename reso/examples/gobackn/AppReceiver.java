package reso.examples.gobackn;

import reso.common.AbstractApplication;
import reso.ip.IPHost;
import reso.ip.IPLayer;

public class AppReceiver extends AbstractApplication {
	
	private final IPLayer ip;
    	
	/**
	 * Constructeur du noeud qui reçoit les paquets
	 * @param host L'IP du noeud
	 */
	public AppReceiver(IPHost host) {
		super(host, "receiver");
		this.ip = host.getIPLayer();
    }
	
	/**
	 * Méthode qui démarre le noeud
	 */
	public void start() {
        GoBackNProtocol transport = new GoBackNProtocol((IPHost) host); //Couche transport
    }
	
	public void stop() {}
	
}
