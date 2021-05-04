package reso.examples.gobackn;

import java.util.ArrayList;

import reso.common.*;
import reso.scheduler.*;
import reso.common.AbstractTimer;

import reso.ip.Datagram;
import reso.ip.IPAddress;
import reso.ip.IPHost;
import reso.ip.IPInterfaceAdapter;
import reso.ip.IPInterfaceListener;
import reso.ip.IPLayer;

public class GoBackNProtocol implements IPInterfaceListener {

	public static final int IP_PROTO_GOBACKN = Datagram.allocateProtocolNumber("GOBACKN");
	
	private final IPHost host;


    private final int windowSize = 5; //Taille de la fenêtre
    private final double time = 0.0601; //Temps du timer en s

    private int actualSequenceNumber = 1; //Dernier numéro de séquence envoyé

    private ArrayList<TCPSegment> waitingSegment = new ArrayList<TCPSegment>(); //Liste des segments en attente
    private ArrayList<TCPSegment> segmentSent = new ArrayList<TCPSegment>(); //Liste des segments envoyé dans l'ordre
    private ArrayList<TCPSegment> segmentsReceived = new ArrayList<TCPSegment>(); //Liste des ségments reçus

    private ArrayList<AbstractTimer> timers = new ArrayList<AbstractTimer>(); //Liste des timers
	
	public GoBackNProtocol(IPHost host) {
		this.host = host;
    	host.getIPLayer().addListener(this.IP_PROTO_GOBACKN, this);
	}

    private class MyTimer extends AbstractTimer {
    	public MyTimer(AbstractScheduler scheduler, double interval) {
    		super(scheduler, interval, false);
    	}
    	protected void run() throws Exception {
            this.stop();
			// System.out.println("\u001B[31m Temps ecoule !");
		}
    }
	
    /**
     * 
     * @param src
     * @param datagram
     * @throws Exception
     */
	@Override
	public void receive(IPInterfaceAdapter src, Datagram datagram) throws Exception {
    	TCPSegment segment = (TCPSegment) datagram.getPayload(); //On récupère le ségment
		
        if(segment.isAck()){ //On vérifie si c'est un ACK ou un paquet
            if(segment.getSequenceNumber() == this.segmentSent.get(0).getSequenceNumber()){ //Si c'est le premier segment envoyé
                if(this.timers.get(0).isRunning() == true){ //Si le timer est toujours lancé
                    this.timers.get(0).stop(); //On arrête le timer
                    this.timers.remove(0); //On retire le timer de la séquence
                    this.segmentSent.remove(0); //On retire le ségment de la liste des ségments envoyés

                    System.out.println("\u001B[32m (" + (int) (host.getNetwork().getScheduler().getCurrentTime()*1000) + "ms)" +" ACK recu" +
                            ", donnees=" + segment);
                    
                    if(this.waitingSegment.size() > 0){ //Si il reste des segments à envoyer
                        TCPSegment segmentToSend = this.waitingSegment.get(0); //On récupère le prochain ségment à envoyer
                        host.getIPLayer().send(IPAddress.ANY, datagram.src, IP_PROTO_GOBACKN, segmentToSend); //On envoie le ségment

                        System.out.println("\u001B[33m (" + (int) (host.getNetwork().getScheduler().getCurrentTime()*1000) + "ms)" +" Paquet envoye" +
                                ", hote=" + host.name + ", destinataire=" + datagram.dst + ", donnees=" + segmentToSend);
            
                        this.timers.add(new MyTimer(host.getNetwork().getScheduler(), this.time)); //On ajoute un timer pour le segment envoyé
                        this.timers.get(this.timers.size()-1).start(); //On démarre le timer
                        this.segmentSent.add(segmentToSend); //On ajoute le ségment à la liste des ségments envoyés
                        this.waitingSegment.remove(0); //On supprime le segment de la liste d'attente
                    }
                }else{ //Si le timer est fini
                    // for(int i=0; i<this.timers.size(); i++){ //On arrête tout les timers
                    //     this.timers.get(i).stop();
                    // }
                    this.timers.clear(); //On supprime tout les timers
                    for(int i=0; i< this.segmentSent.size(); i++){ //On renvoie tout les ségments non ACK
                        host.getIPLayer().send(IPAddress.ANY, datagram.src, IP_PROTO_GOBACKN, this.segmentSent.get(i));

                        System.out.println("\u001B[33m (" + (int) (host.getNetwork().getScheduler().getCurrentTime()*1000) + "ms)" +" Paquet reenvoye" +
                                ", hote=" + host.name + ", destinataire=" + datagram.dst + ", donnees=" + this.segmentSent.get(i));

                        this.timers.add(new MyTimer(host.getNetwork().getScheduler(), this.time)); //On ajoute le timer
                        this.timers.get(this.timers.size()-1).start(); //On démarre le timer
                    }
                }
            }
        }else{ //Si c'est un paquet
            System.out.println("\u001B[34m (" + (int) (host.getNetwork().getScheduler().getCurrentTime()*1000) + "ms)" +" Donnees recues" +
                    ", donnees=" + segment);
            
            this.segmentsReceived.add(segment); //On ajoute aux segments déjà reçus
            sendAcknowledgment(datagram, segment.getSequenceNumber()); //On envoie un ACK
        }
	}

    /**
     * 
     * @param data
     * @param destination
     * @throws Exception
     */
    public void sendData(int data, IPAddress destination) throws Exception{
        int[] segmentData = new int[]{data};
        TCPSegment segment = new TCPSegment(segmentData, this.actualSequenceNumber);

        if(this.segmentSent.size() < this.windowSize){ //On remplie la fenêtre
            this.timers.add(new MyTimer(host.getNetwork().getScheduler(), this.time+(this.actualSequenceNumber-1)*0.005)); //On ajoute un timer
            this.timers.get(this.timers.size()-1).start(); //On démarre le timer
            host.getIPLayer().send(IPAddress.ANY, destination, IP_PROTO_GOBACKN, segment); //On envoie le segment
            
            System.out.println("\u001B[33m (" + (int) (host.getNetwork().getScheduler().getCurrentTime()*1000) + "ms)" +" Paquet envoye" +
                    ", hote=" + host.name + ", destinataire=" + destination + ", donnees=" + segment);

            this.segmentSent.add(segment); //On ajoute le segment dans la liste des segments envoyés
            this.actualSequenceNumber++; //On actualise le numéro de séquence
        } else{ //Si la fenêtre est pleine, on ajoute à la file d'attente
            this.waitingSegment.add(segment);
            this.actualSequenceNumber++;
        }
    }

    /**
     * Envoie d'un ACK au Sender
     * @param datagram
     * @param sequenceNumber
     * @throws Exception
     */
    private void sendAcknowledgment(Datagram datagram, int sequenceNumber) throws Exception{
        TCPSegment segment = new TCPSegment(sequenceNumber); //Nouveau ségment de type ACK avec le numéro de séquence
        host.getIPLayer().send(IPAddress.ANY, datagram.src, IP_PROTO_GOBACKN, segment); //Envoie de l'ACK

        System.out.println("\u001B[35m (" + (int) (host.getNetwork().getScheduler().getCurrentTime()*1000) + "ms)" +" ACK envoye" +
                ", hote=" + host.name + ", destinataire=" + datagram.src + ", donnees=" + segment);
    }
}