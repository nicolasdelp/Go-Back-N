package reso.examples.gobackn;

import reso.common.Message;

public class TCPSegment implements Message {
	
	public final int sequenceNumber; 
    public final int[] data;
    public final boolean isAck;
	
	/**
	 * Constructeur d'un Segment TCP avec donnée
	 * @param data Une liste de données (ici des entiers)
	 * @param sequenceNumber Un numéro de séquence
	 */
	public TCPSegment(int[] data, int sequenceNumber) {
		this.data = data;
        this.sequenceNumber = sequenceNumber;
        this.isAck = false;
	}
	
	/**
	 * Constructeur d'un Segment TCP sans donnée
	 * @param sequenceNumber Un numéro de séquence
	 */
    public TCPSegment(int sequenceNumber) {
        this.isAck = true;
		this.data = new int[]{};
        this.sequenceNumber = sequenceNumber;
	}
	
	/**
	 * Méthode pour l'affichage en console
	 */
	public String toString() {
		return "Segment [seq. num.=" + sequenceNumber + ", isAck=" + isAck +"]";
	}

	/**
	 * Ascesseur du booléen "isAck"
	 * @return La valeur de "isAck"
	 */
    public boolean isAck(){
        return this.isAck;
    }

	/**
	 * Ascesseur du numéro de séquenc
	 * @return Le numéro de séquence
	 */
	public int getSequenceNumber() {
		return this.sequenceNumber;
	}

	/**
	 * Donne la taille occupé par le segmment en mémoire
	 * @return La taille du segment
	 */
	@Override
	public int getByteLength() {
		// The TCP segment carries an array of 'int'
		return 4*this.data.length+1;
	}
}
