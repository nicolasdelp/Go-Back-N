package reso.examples.gobackn;

import java.util.ArrayList;

import reso.ip.Datagram;
import reso.ip.IPAddress;
import reso.ip.IPHost;
import reso.ip.IPInterfaceAdapter;
import reso.ip.IPInterfaceListener;
import reso.ip.IPLayer;

public class GoBackNProtocol implements IPInterfaceListener {

	public static final int IP_PROTO_GOBACKN = Datagram.allocateProtocolNumber("GOBACKN");
	
	private final IPHost host; 

    private final int windowSize = 3; //Taille de la fenêtre 
    private final int RTT = 1000; //Temps du timer (= 1ms)

    private int actualSequenceNumber = 1; //Dernier numéro de séquence envoyé
    private int ackSequenceNumber = 1; //Dernier numéro d'ACK de séquence envoyé
    private int expectedSequenceNumber = 1; //Numéro de séquence attendu par le receiver

    private ArrayList<TCPSegment> segmentSent = new ArrayList<TCPSegment>(); //Liste des segments envoyé dans l'ordre
    private ArrayList<TCPSegment> waitingSegment = new ArrayList<TCPSegment>(); //Liste des segments en attente
	
	public GoBackNProtocol(IPHost host) {
		this.host = host;
    	host.getIPLayer().addListener(this.IP_PROTO_GOBACKN, this);
	}
	
	@Override
	public void receive(IPInterfaceAdapter src, Datagram datagram) throws Exception {
    	TCPSegment segment = (TCPSegment) datagram.getPayload();
		
        if(segment.isAck()){ //Coté sender
            System.out.println("\u001B[32m Data (" + (int) (host.getNetwork().getScheduler().getCurrentTime()*1000) + "ms)" +
				" host=" + host.name + ", dgram.src=" + datagram.src + ", dgram.dst=" +
				datagram.dst + ", iif=" + src + ", data=" + segment);

            int sequenceNumber = segment.getSequenceNumber();
            if(sequenceNumber == this.segmentSent.get(0).getSequenceNumber()){
                this.segmentSent.remove(0);

                if(this.waitingSegment.size() > 0){ //Si il reste des segments à envoyer
                    TCPSegment segmentToSend = this.waitingSegment.get(0);
                    host.getIPLayer().send(IPAddress.ANY, datagram.src, IP_PROTO_GOBACKN, segmentToSend);
                    this.segmentSent.add(segmentToSend);
                    this.waitingSegment.remove(0);
                }
            }
        } else{ //Coté receiver
            System.out.println("\u001B[34m Data (" + (int) (host.getNetwork().getScheduler().getCurrentTime()*1000) + "ms)" +
				" host=" + host.name + ", dgram.src=" + datagram.src + ", dgram.dst=" +
				datagram.dst + ", iif=" + src + ", data=" + segment);

            if(segment.getSequenceNumber() == this.expectedSequenceNumber){
                sendAcknowledgment(datagram);
                this.ackSequenceNumber++;
                this.expectedSequenceNumber++;
            }
        }
	}

    public void sendData(int data, IPAddress destination) throws Exception{
        int[] segmentData = new int[]{data};
        TCPSegment segment = new TCPSegment(segmentData, this.actualSequenceNumber);

        if(this.segmentSent.size() < this.windowSize){ //On remplie la fenêtre
            host.getIPLayer().send(IPAddress.ANY, destination, IP_PROTO_GOBACKN, segment);
            this.segmentSent.add(segment);
            this.actualSequenceNumber++;
        } else{ //Si la fenêtre est pleine, on ajoute à la file d'attente
            this.waitingSegment.add(segment);
            this.actualSequenceNumber++;
        }
    }

    private void sendAcknowledgment(Datagram datagram) throws Exception{
        host.getIPLayer().send(IPAddress.ANY, datagram.src, IP_PROTO_GOBACKN, new TCPSegment(this.ackSequenceNumber));
    }
}