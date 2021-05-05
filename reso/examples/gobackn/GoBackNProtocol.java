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

    
    private final int windowSize = 6; //Taille max de la fenêtre
    private final double time = 0.085; //Temps du timer en s
    private int window = 1; //Taille de la fenêtre

    private int actualSequenceNumber = 1; //Dernier numéro de séquence envoyé

    private ArrayList<TCPSegment> waitingQueue = new ArrayList<TCPSegment>(); //Liste des segments en attente
    private ArrayList<TCPSegment> segmentSent = new ArrayList<TCPSegment>(); //Liste des segments envoyé dans l'ordre
    private ArrayList<TCPSegment> ACKSent = new ArrayList<TCPSegment>(); //Liste des ACK envoyé dans l'ordre
    private ArrayList<TCPSegment> segmentsReceived = new ArrayList<TCPSegment>(); //Liste des ségments reçus

    private ArrayList<AbstractTimer> timers = new ArrayList<AbstractTimer>(); //Liste des timers

    private boolean test = true; //Test de perte de paquet
	
    /**
     * Constructeur du protocol GoBackN
     * @param host
     */
	public GoBackNProtocol(IPHost host) {
		this.host = host;
    	host.getIPLayer().addListener(this.IP_PROTO_GOBACKN, this);
	}

    /**
     * Class du Timer qu'on utilise avec les paquets
     */
    private class MyTimer extends AbstractTimer {
    	public MyTimer(AbstractScheduler scheduler, double interval) {
    		super(scheduler, interval, false);
    	}
    	protected void run() throws Exception {
            stop();
			System.out.println("\u001B[31m Timeout ! \u001B[0m");
		}
    }
	
    /**
     * Protocol GoBackN + pipelining
     * @param src
     * @param datagram
     * @throws Exception
     */
	@Override
	public void receive(IPInterfaceAdapter src, Datagram datagram) throws Exception {
    	TCPSegment segment = (TCPSegment) datagram.getPayload(); //On récupère le ségment

        if(!segment.isAck()){ //Si c'est un paquet
            System.out.println("\u001B[34m (" + (int) (host.getNetwork().getScheduler().getCurrentTime()*1000) + "ms)" +" Donnees recues" +
                    ", donnees=" + segment + "\u001B[0m");
            
            this.segmentsReceived.add(segment); //On ajoute aux segments déjà reçus
            sendAcknowledgment(datagram, segment.getSequenceNumber()); //On envoie un ACK
        }

        if(segment.isAck()){ //Si c'est un ACK
            if(segment.getSequenceNumber() == this.segmentSent.get(0).getSequenceNumber()){ //Si c'est le segment que l'on attend
                if(this.timers.get(0).isRunning()){ //Si le timer est toujours lancé
                    this.timers.get(0).stop(); //On arrête le timer
                    this.timers.remove(0); //On supprime le timer
                    this.segmentSent.remove(0); //On retire le ségment de la liste des ségments envoyés

                    if(this.window < this.windowSize){
                        this.window++; //Slow start
                    }
                    // else{ //Additive increase
                        
                    // }

                    System.out.println("\u001B[32m (" + (int) (host.getNetwork().getScheduler().getCurrentTime()*1000) + "ms)" +" ACK recu" +
                            ", donnees=" + segment + "\u001B[0m");
                            
                    System.out.println("[ Taille de la fenetre : " + this.window + " ]");
                    
                    if(this.waitingQueue.size() > 0){ //Si il reste des segments à envoyer
                        int lap;
                        if(this.window > this.waitingQueue.size()){ //Si la fenêtre est trop grande que le nombre restant de paquet a envoyer
                            lap = this.waitingQueue.size();
                        }else{
                            if(this.window > 2){ //Pour ne pas envoyer plus de paquet que la taille de la fenêtre
                                lap = this.window-1;
                            }else{
                                lap = this.window;
                            }
                        }
                        
                        for(int i=0; i<lap; i++){
                            TCPSegment segmentToSend = this.waitingQueue.get(0); //On récupère le prochain ségment à envoyer

                            host.getIPLayer().send(IPAddress.ANY, datagram.src, IP_PROTO_GOBACKN, segmentToSend); //On envoie le ségment
                            this.timers.add(new MyTimer(host.getNetwork().getScheduler(), this.time)); //On ajoute un timer pour le segment envoyé
                            this.timers.get(this.timers.size()-1).start(); //On démarre le timer
                            this.segmentSent.add(segmentToSend); //On ajoute le ségment à la liste des ségments envoyés
                            this.waitingQueue.remove(0); //On supprime le segment de la liste d'attente

                            System.out.println("\u001B[33m (" + (int) (host.getNetwork().getScheduler().getCurrentTime()*1000) + "ms)" +" Paquet envoye" +
                                    ", hote=" + host.name + ", destinataire=" + datagram.dst + ", donnees=" + segmentToSend + "\u001B[0m");
                        }
                        
                    }
                }
            }else{ //Paquet perdu (car paquet x+1 arrivé avant paquet x)
                System.out.println("\u001B[31m Le paquet " + this.segmentSent.get(0).getSequenceNumber() + " a ete perdu ! \u001B[0m");
            }
        }

        if(this.timers.size() > 0){ //Si il y a au moins 1 timer d'initié
            if(this.timers.get(0).isRunning() == false){ //Timeout
                for(int i=0; i<this.timers.size(); i++){ //On arrête tout les timers
                    this.timers.get(i).stop();
                }
                this.timers.clear(); //On supprime tout les timers

                this.window = 1; //Réaction à un timeout
                System.out.println("[ Taille de la fenetre : " + this.window + " ]");

                ArrayList<TCPSegment> temp = new ArrayList<TCPSegment>(); //Liste intermédiaire
                
                for (TCPSegment i : this.segmentSent) {
                    temp.add(i);
                }
                this.segmentSent.clear();

                for (TCPSegment i : this.waitingQueue) {
                    temp.add(i);
                }
                this.waitingQueue.clear();

                for (TCPSegment i : temp) {
                    this.waitingQueue.add(i);
                }

                host.getIPLayer().send(IPAddress.ANY, datagram.src, IP_PROTO_GOBACKN, this.waitingQueue.get(0)); //On renvoie le premier car fenetre = 1
                this.timers.add(new MyTimer(host.getNetwork().getScheduler(), this.time)); //On ajoute le timer
                this.timers.get(this.timers.size()-1).start(); //On démarre le timer
                this.segmentSent.add(this.waitingQueue.get(0)); //On l'ajoute dans la liste des segments envoyés
                this.waitingQueue.remove(0); //On retire le segment de la file

                System.out.println("\u001B[33m (" + (int) (host.getNetwork().getScheduler().getCurrentTime()*1000) + "ms)" +" Paquet envoye" +
                        ", hote=" + host.name + ", destinataire=" + datagram.dst + ", donnees=" + this.segmentSent.get(0) + "\u001B[0m");
            }
        }
	}

    /**
     * Envoie d'un paquet
     * @param data
     * @param destination
     * @throws Exception
     */
    public void sendData(int data, IPAddress destination) throws Exception{
        int[] segmentData = new int[]{data};
        TCPSegment segment = new TCPSegment(segmentData, this.actualSequenceNumber);

        if(this.segmentSent.size() < this.window){ //On remplie la fenêtre
            System.out.println("[ Taille de la fenetre : " + this.window + " ]");
            host.getIPLayer().send(IPAddress.ANY, destination, IP_PROTO_GOBACKN, segment); //On envoie le segment
            this.timers.add(new MyTimer(host.getNetwork().getScheduler(), this.time+(this.actualSequenceNumber-1)*0.005)); //On ajoute un timer
            this.timers.get(this.timers.size()-1).start(); //On démarre le timer
            this.segmentSent.add(segment); //On ajoute le segment dans la liste des segments envoyés
            this.actualSequenceNumber++; //On actualise le numéro de séquence

            System.out.println("\u001B[33m (" + (int) (host.getNetwork().getScheduler().getCurrentTime()*1000) + "ms)" +" Paquet envoye" +
                    ", hote=" + host.name + ", destinataire=" + destination + ", donnees=" + segment + "\u001B[0m");
        }else{ //Si la fenêtre est pleine, on ajoute à la file d'attente
            this.waitingQueue.add(segment);
            this.actualSequenceNumber++;
        }
    }

    /**
     * Envoie d'un ACK
     * @param datagram
     * @param sequenceNumber
     * @throws Exception
     */
    private void sendAcknowledgment(Datagram datagram, int sequenceNumber) throws Exception{
        if(sequenceNumber == 3){ //On crée une perte de paquet
            if(!this.test){
                TCPSegment segment = new TCPSegment(sequenceNumber); //Nouveau ségment de type ACK avec le numéro de séquence
                host.getIPLayer().send(IPAddress.ANY, datagram.src, IP_PROTO_GOBACKN, segment); //Envoie de l'ACK

                this.ACKSent.add(segment); //On ajoute l'ACK dans la liste des ACK envoyé

                System.out.println("\u001B[35m (" + (int) (host.getNetwork().getScheduler().getCurrentTime()*1000) + "ms)" +" ACK envoye" +
                        ", hote=" + host.name + ", destinataire=" + datagram.src + ", donnees=" + segment + "\u001B[0m");
            }
            this.test = false;
        }else{
            TCPSegment segment = new TCPSegment(sequenceNumber); //Nouveau ségment de type ACK avec le numéro de séquence
            host.getIPLayer().send(IPAddress.ANY, datagram.src, IP_PROTO_GOBACKN, segment); //Envoie de l'ACK
            
            this.ACKSent.add(segment); //On ajoute l'ACK dans la liste des ACK envoyé

            System.out.println("\u001B[35m (" + (int) (host.getNetwork().getScheduler().getCurrentTime()*1000) + "ms)" +" ACK envoye" +
                    ", hote=" + host.name + ", destinataire=" + datagram.src + ", donnees=" + segment + "\u001B[0m");
        }
    }
}