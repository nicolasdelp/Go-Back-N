package reso.examples.gobackn;

import java.util.Random;
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

    //##########################   PARAMETRES   ###############################
    //Le nombre de paquet qui sont envoyé se trouve dans la classe Demo.java
    private final int maximumWindowSize = 10; //Taille de la fenêtre
    private boolean test = true; //Perte de paquet
    private int testNumber = 5; //Numéro de séquence du paquet à perdre
    //#########################################################################

    private int currentWindowSize = 1; //Taille actuelle de la fenêtre
    private final double time = 0.080; //Temps pour les timers (en s)
    private int currentSequenceNumber = 1; //Numéro de séquence actuel
    
    private ArrayList<TCPSegment> segmentsSent = new ArrayList<TCPSegment>(); //Liste des segments envoyés (dans l'ordre)
    private ArrayList<TCPSegment> waitingQueue = new ArrayList<TCPSegment>(); //Liste des segments en attente
    private ArrayList<Integer> ACKReceived = new ArrayList<Integer>(); //Liste du nombre d'ACK recu (dans l'ordre)
    private ArrayList<AbstractTimer> timers = new ArrayList<AbstractTimer>(); //Liste des timers

    private boolean additiveIncrease = true;
    private boolean slowStart = false;

    /**
     * Constructeur du protocol GoBackN
     * @param host L'IP du noeud
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
			System.out.println("\u001B[31m Temps ecoule ! \u001B[0m");
		}
    }

    /**
     * Envoie d'un segment
     * @param segment Un segment TCP
     * @param destination L'IP de destination
     * @throws Exception Une exception qui pourrait être levé
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
     * @param datagram Le datagramme
     * @param sequenceNumber Le numéro de séquence de L'ACK
     * @throws Exception Une exception qui pourrait être levé
     */
    private void sendAcknowledgment(Datagram datagram, int sequenceNumber) throws Exception{
        if(sequenceNumber == this.testNumber){ //On crée une perte d'un paquet
            if(!this.test){
                TCPSegment segment = new TCPSegment(sequenceNumber); //Nouveau ségment de type ACK avec le numéro de séquence
                host.getIPLayer().send(IPAddress.ANY, datagram.src, IP_PROTO_GOBACKN, segment); //Envoie de l'ACK

                System.out.println("\u001B[35m (" + (int) (host.getNetwork().getScheduler().getCurrentTime()*1000) + "ms)" +" ACK envoye" +
                        ", hote=" + host.name + ", destinataire=" + datagram.src + ", donnees=" + segment + "\u001B[0m");
            }else{
                System.out.println("Le paquet " + this.testNumber + " a ete perdu !");
                this.test = false;
            }
        }else{
            TCPSegment segment = new TCPSegment(sequenceNumber); //Nouveau ségment de type ACK avec le numéro de séquence
            host.getIPLayer().send(IPAddress.ANY, datagram.src, IP_PROTO_GOBACKN, segment); //Envoie de l'ACK
            
            System.out.println("\u001B[35m (" + (int) (host.getNetwork().getScheduler().getCurrentTime()*1000) + "ms)" +" ACK envoye" +
                    ", hote=" + host.name + ", destinataire=" + datagram.src + ", donnees=" + segment + "\u001B[0m");
        }
    }

    /**
     * Envoie d'un paquet
     * @param data Une liste de données (ici des entiers)
     * @param destination L'IP de destination
     * @throws Exception Une exception qui pourrait être levé
     */
    public void sendData(int data, IPAddress destination) throws Exception{
        int[] segmentData = new int[]{data}; //On met les données dans une liste
        TCPSegment segment = new TCPSegment(segmentData, this.currentSequenceNumber); //On crée le segment a envoyer

        if(this.segmentsSent.size() < this.currentWindowSize){
            System.out.println("[ Taille de la fenetre : " + this.currentWindowSize + " ]");
            sendSegment(segment, destination); //On envoie le segment
        }else{
            this.waitingQueue.add(segment); //On ajoute à la file d'attente
            this.currentSequenceNumber++;
        }
    }

    /**
     * Méthode qui paramètre les booléens utile à la congestion (Additive Increase)
     */
    private void additiveIncrease(){
        this.slowStart = false;
        this.additiveIncrease = true;
        if(this.currentWindowSize < this.maximumWindowSize){
            this.currentWindowSize++;
        }
    }

    /**
     * Méthode qui paramètre les booléens utile à la congestion (Multiplicative Decrease)
     */
    private void multiplicativeDecrease(){
        this.currentWindowSize = (int) this.currentWindowSize/2; //On divise par 2 la fenêtre
        this.slowStart = false;
        this.additiveIncrease = true;
    }

    /**
     * Méthode qui paramètre les booléens utile à la congestion (Slow Start)
     */
    private void slowStart(){
        this.slowStart = true;
        this.additiveIncrease = false;
        this.currentWindowSize = this.currentWindowSize*2; //On multiplie par 2 la taille de la fenêtre
    }

    /**
     * Protocol GoBack-N
     * @param src L'IP source
     * @param datagram Le datagramme
     * @throws Exception Une exception qui pourrait être levé
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
            if(this.ACKReceived.size() < segment.getSequenceNumber()){ //Gestion des 3 ACK dupliqués
                this.ACKReceived.add(1); //Si il n'est pas encore dedans
            }else {
                this.ACKReceived.set(segment.getSequenceNumber()-1, this.ACKReceived.get(segment.getSequenceNumber()-1)+1); //Sinon on ajoute 1 au nombre de fois qu'on a reçu cet ACK
            }

            for(int elem : this.ACKReceived){ //Si on a reçu 3 fois le même ACK
                if(elem == 3){ //Multiplicative Decrease
                    multiplicativeDecrease();
                    this.ACKReceived.clear();
                }
            }

            if(segment.getSequenceNumber() == this.segmentsSent.get(0).getSequenceNumber()){ //Si on reçoit le segment qu'on attend
                if(this.timers.get(0).isRunning()){ //Si le timer "tourne" toujours
                    this.timers.get(0).stop(); //On arrête le timer
                    this.timers.remove(0); //On supprime le timer
                    this.segmentsSent.remove(0); //On retire le segment de la liste des segments envoyés
                    
                    if(this.slowStart){ //Si on est en Slow Start
                        if(this.currentWindowSize < (this.maximumWindowSize/2)){
                            slowStart();
                        }else{ //Si on passe la moitié de la fenêtre on passe à du additive increase
                            this.additiveIncrease = true;
                        }
                    }

                    if(this.additiveIncrease){ //Si on est en Additive Increase
                        additiveIncrease();
                    }
                    

                    System.out.println("\u001B[32m (" + (int) (host.getNetwork().getScheduler().getCurrentTime()*1000) + "ms)" +" ACK recu" +
                            ", donnees=" + segment + "\u001B[0m");

                    if(this.waitingQueue.size() > 0){ //S'il reste des segments à envoyer
                        System.out.println("[ Taille de la fenetre : " + this.currentWindowSize + " ]");

                        int laps = this.currentWindowSize-this.segmentsSent.size(); //Nombre de segments a envoyer
                        for(int i=0; i<laps; i++){
                            TCPSegment segmentToSend = this.waitingQueue.get(0); //On récupère le prochain ségment à envoyer
                            sendSegment(segmentToSend, datagram.src); //On envoie le segment
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

                ArrayList<TCPSegment> temp = new ArrayList<TCPSegment>(); //Liste intermédiaire pour tout remettre dans la file d'attente
                
                for (TCPSegment i : this.segmentsSent) { //Les segment envoyés non-acké
                    temp.add(i);
                }
                this.segmentsSent.clear();

                for (TCPSegment i : this.waitingQueue) { //Les segment de l'ancienne liste d'attente
                    temp.add(i);
                }
                this.waitingQueue.clear();

                for (TCPSegment i : temp) { //On remet tout dans la nouvelle liste d'attente
                    this.waitingQueue.add(i);
                }

                System.out.println("[ Taille de la fenetre : " + this.currentWindowSize + " ]");
                sendSegment(this.waitingQueue.get(0), datagram.src); //On envoie le segment
                this.waitingQueue.remove(0); //On retire le segment de la file
            }
        }
    }
}