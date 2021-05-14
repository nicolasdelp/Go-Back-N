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

    private final int maximumWindowSize = 10; //Taille maximum de la fenêtre
    private int currentWindowSize = 1; //Taille actuelle de la fenêtre
    private final double time = 0.1; //Temps pour les timers (en s)

    private int currentSequenceNumber = 1; //Numéro de séquence actuel
    
    private ArrayList<TCPSegment> segmentsSent = new ArrayList<TCPSegment>(); //Liste des segments envoyés (dans l'ordre)
    private ArrayList<TCPSegment> waitingQueue = new ArrayList<TCPSegment>(); //Liste des segments en attente
    private ArrayList<Integer> ACKReceived = new ArrayList<Integer>(); //Liste du nombre d'ACK recu (dans l'ordre)
    private ArrayList<AbstractTimer> timers = new ArrayList<AbstractTimer>(); //Liste des timers

    private boolean additiveIncrease = true;
    private boolean slowStart = false;


    private boolean test = true; //Test de perte de paquet

    /**
     * Constructeur du protocol GoBackN
     * @param host
     */
	public GoBackNProtocol(IPHost host) {
		this.host = host;
    	host.getIPLayer().addListener(this.IP_PROTO_GOBACKN, this);
        //Print l'ensemble de la configuration
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
			System.out.println("\u001B[31m Temps ecoule ! \u001B[0m");
		}
    }

    /**
     * Envoie d'un segment
     * @param segment
     * @param destination
     * @throws Exception
     */
    private void sendSegment(TCPSegment segment, IPAddress destination) throws Exception{
        host.getIPLayer().send(IPAddress.ANY, destination, IP_PROTO_GOBACKN, segment); //On envoie le segment
        this.timers.add(new MyTimer(host.getNetwork().getScheduler(), this.time)); //On ajoute un timer
        this.timers.get(this.timers.size()-1).start(); //On démarre le timer
        this.segmentsSent.add(segment); //On ajoute le segment dans la liste des segments envoyés
        this.currentSequenceNumber++; //On actualise le numéro de séquence

        System.out.println("\u001B[33m (" + (int) (host.getNetwork().getScheduler().getCurrentTime()*1000) + "ms)" +" Segment envoye" +
                ", hote=" + host.name + ", destinataire=" + destination + ", donnees=" + segment + "\u001B[0m");
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

                System.out.println("\u001B[35m (" + (int) (host.getNetwork().getScheduler().getCurrentTime()*1000) + "ms)" +" ACK envoye" +
                        ", hote=" + host.name + ", destinataire=" + datagram.src + ", donnees=" + segment + "\u001B[0m");
            }
            System.out.println("Un paquet a ete perdu !");
            this.test = false;
        }else{
            TCPSegment segment = new TCPSegment(sequenceNumber); //Nouveau ségment de type ACK avec le numéro de séquence
            host.getIPLayer().send(IPAddress.ANY, datagram.src, IP_PROTO_GOBACKN, segment); //Envoie de l'ACK
            
            System.out.println("\u001B[35m (" + (int) (host.getNetwork().getScheduler().getCurrentTime()*1000) + "ms)" +" ACK envoye" +
                    ", hote=" + host.name + ", destinataire=" + datagram.src + ", donnees=" + segment + "\u001B[0m");
        }


        // TCPSegment segment = new TCPSegment(sequenceNumber); //On crée le segment (ACK) a envoyer
        // host.getIPLayer().send(IPAddress.ANY, datagram.src, IP_PROTO_GOBACKN, segment); //On envoie le segment

        // System.out.println("\u001B[35m (" + (int) (host.getNetwork().getScheduler().getCurrentTime()*1000) + "ms)" +" ACK envoye" +
        //         ", hote=" + host.name + ", destinataire=" + datagram.src + ", donnees=" + segment + "\u001B[0m");
    }

    /**
     * Envoie d'un paquet
     * @param data
     * @param destination
     * @throws Exception
     */
    public void sendData(int data, IPAddress destination) throws Exception{
        int[] segmentData = new int[]{data}; //On met les données dans une liste
        TCPSegment segment = new TCPSegment(segmentData, this.currentSequenceNumber); //On crée le segment a envoyer

        if(this.segmentsSent.size() < this.currentWindowSize){ //Additive increase
            System.out.println("[ Taille de la fenetre : " + this.currentWindowSize + " ]");
            sendSegment(segment, destination);
        }else{ //On ajoute à la file d'attente
            this.waitingQueue.add(segment);
            this.currentSequenceNumber++;
        }
    }

    private void additiveIncrease(){
        this.slowStart = false;
        this.additiveIncrease = true;
        if(this.currentWindowSize < this.maximumWindowSize){
            this.currentWindowSize++;
        }
    }

    private void multiplicativeDecrease(){
        this.currentWindowSize = (int) this.currentWindowSize/2; //A déterminer
        this.slowStart = false;
        this.additiveIncrease = true;
    }

    private void slowStart(){
        this.slowStart = true;
        this.additiveIncrease = false;
        this.currentWindowSize = this.currentWindowSize*2;
    }

    /**
     * Protocol GoBack-N
     * @param src
     * @param datagram
     * @throws Exception
     */
	@Override
	public void receive(IPInterfaceAdapter src, Datagram datagram) throws Exception {
        TCPSegment segment = (TCPSegment) datagram.getPayload(); //On récupère le segment

        if(!segment.isAck()){ //Si c'est un paquet
            System.out.println("\u001B[34m (" + (int) (host.getNetwork().getScheduler().getCurrentTime()*1000) + "ms)" +" Segment recu" +
                        ", donnees=" + segment + "\u001B[0m");

            sendAcknowledgment(datagram, segment.getSequenceNumber()); //On envoie un ACK
        }

        if(segment.isAck()){ //Si c'est un ACK
            if(this.ACKReceived.size() < segment.getSequenceNumber()){
                this.ACKReceived.add(1); //Si il n'est pas encore dedans
            }else {
                this.ACKReceived.set(segment.getSequenceNumber()-1, this.ACKReceived.get(segment.getSequenceNumber()-1)+1); //Sinon on ajoute 1 au nombre de fois qu'on a reçu cet ACK
            }

            for(int elem : this.ACKReceived){
                if(elem == 3){ //Multiplicative Decrease
                    multiplicativeDecrease();
                    this.ACKReceived.clear();
                }
            }

            if(segment.getSequenceNumber() == this.segmentsSent.get(0).getSequenceNumber()){ //Si c'est le segment qu'on attend
                if(this.timers.get(0).isRunning()){ //Si le timer "tourne" toujours
                    this.timers.get(0).stop(); //On arrête le timer
                    this.timers.remove(0); //On supprime le timer
                    this.segmentsSent.remove(0); //On retire le segment de la liste des segments envoyés
                    
                    if(this.slowStart){
                        if(this.currentWindowSize < (this.maximumWindowSize/2)){
                            slowStart();
                        }else{ //Si on passe la moitier du max de la fenêtre on passe à du additive increase
                            this.additiveIncrease = true;
                        }
                    }

                    if(this.additiveIncrease){
                        additiveIncrease();
                    }
                    

                    System.out.println("\u001B[32m (" + (int) (host.getNetwork().getScheduler().getCurrentTime()*1000) + "ms)" +" ACK recu" +
                            ", donnees=" + segment + "\u001B[0m");

                    if(this.waitingQueue.size() > 0){ //S'il reste des segments à envoyer
                        System.out.println("[ Taille de la fenetre : " + this.currentWindowSize + " ]");
                        int laps = this.currentWindowSize-this.segmentsSent.size(); //Nombre de segments a renvoyer
                        for(int i=0; i<laps; i++){
                            TCPSegment segmentToSend = this.waitingQueue.get(0); //On récupère le prochain ségment à envoyer
                            sendSegment(segmentToSend, datagram.src);
                            this.waitingQueue.remove(0); //On supprime le segment de la liste d'attente
                        }
                    }
                }
            }
        }

        if(this.timers.size() > 0){ //Si il y a au moins 1 timer d'initié
            if(this.timers.get(0).isRunning() == false){ //Timeout
                for(int i=0; i<this.timers.size(); i++){ //On arrête tout les timers
                    this.timers.get(i).stop();
                }
                this.timers.clear(); //On supprime tout les timers

                this.currentWindowSize = 1; //Réaction à un timeout
                this.slowStart = true;
                this.additiveIncrease = false;

                ArrayList<TCPSegment> temp = new ArrayList<TCPSegment>(); //Liste intermédiaire
                
                for (TCPSegment i : this.segmentsSent) {
                    temp.add(i);
                }
                this.segmentsSent.clear();

                for (TCPSegment i : this.waitingQueue) {
                    temp.add(i);
                }
                this.waitingQueue.clear();

                for (TCPSegment i : temp) {
                    this.waitingQueue.add(i);
                }

                System.out.println("[ Taille de la fenetre : " + this.currentWindowSize + " ]");
                sendSegment(this.waitingQueue.get(0), datagram.src);
                this.waitingQueue.remove(0); //On retire le segment de la file
            }
        }
    }
}