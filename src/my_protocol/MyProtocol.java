package my_protocol;

import framework.IRDTProtocol;
import framework.Utils;

import java.util.Arrays;

/**
 * @version 10-07-2019
 *
 * Copyright University of Twente,  2013-2024
 *
 **************************************************************************
 *                          = Copyright notice =                          *
 *                                                                        *
 *            This file may ONLY  be distributed UNMODIFIED!              *
 * In particular, a correct solution to the challenge must  NOT be posted *
 * in public places, to preserve the learning effect for future students. *
 **************************************************************************
 */
public class MyProtocol extends IRDTProtocol {
    /*Names:
    Dylan Sterling -S3177858
    Ahmet Ertugrul Hacioglu -S3362264 */

    // change the following as you wish:
    static final int HEADERSIZE=5;   // number of header bytes in each packet
    static final int DATASIZE=512;   // max. number of user data bytes in each packet
    private int filePointer = 0;

    @Override
    public void sender() {
        System.out.println("Sending...");

        // read from the input file
        Integer[] fileContents = Utils.getFileContents(getFileID());
        // keep track of where we are in the data

        int header = 0;

        boolean running = true;
        while(running) {
            int datalen = Math.min(DATASIZE, fileContents.length - filePointer);
            Integer[] pkt = new Integer[HEADERSIZE + datalen];
            // write something random into the header byte
            pkt[0] = 0xff & header;
            pkt[1] = (0xff00 & header) >>> 8;
            int fileLength = fileContents.length;
            System.out.println("length of file = " + fileLength);

            pkt[2] = 0xff & fileLength;
            System.out.println(pkt[2]);
            pkt[3] = (0xff00 & fileLength) >>> 8;
            System.out.println(pkt[3]);
            pkt[4] = (0xff0000 & fileLength) >>> 16;
            System.out.println(pkt[4]);
            //System.out.println("Length of ");

            // copy databytes from the input file into data part of the packet, i.e., after the header
            System.arraycopy(fileContents, filePointer, pkt, HEADERSIZE, datalen);

            // send the packet to the network layer
            getNetworkLayer().sendPacket(pkt);
            System.out.println("Sent one packet with header=" + header);

            // schedule a timer for 1000 ms into the future, just to show how that works:

           //framework.Utils.Timeout.SetTimeout(1000, this, header);
           /* try{
                Utils.Timeout.Start();
            } catch(Exception ignored){
            }*/
            //System.out.println("set a timeout for 1000 miliseconds");


            // and loop and sleep; you may use this loop to check for incoming acks...
            boolean stop = false;
            while (!stop) {
                try {
                    Thread.sleep(130);
                    //System.out.println("checking for new packet");
                    Integer[] packet = getNetworkLayer().receivePacket();
                    //System.out.println("packet: "+packet);

                    if (packet != null && (packet[0] + (packet[1] << 8)) == header) {
                        // tell the user
                        System.out.println("Received acknowledgement, for packet " + packet[0]);
                        filePointer += DATASIZE;

                        header++;
                        //Utils.Timeout.Stop();
                        stop = true;
                    } else {
                        //System.out.println("sending packet: " + header + " again");
                        getNetworkLayer().sendPacket(pkt);
                    }
                } catch(InterruptedException e){
                    System.out.println("Interrupted");
                    stop = true;
                }
            }
            //System.out.println("exited second while loop");
            if(filePointer >= fileContents.length){
                System.out.println("filepointer is larger than file, filepointer is "+ filePointer);
                running = false;
            }
            System.out.println("filepointer is smaller than file size: " + filePointer);
        }

    }

    @Override
    public void TimeoutElapsed(Object tag) {
        System.out.println("timeout occured with tag: " + tag);
        int z=(Integer)tag;
        // handle expiration of the timeout:

        Integer[] fileContents = Utils.getFileContents(getFileID());

        // create a new packet of appropriate size
        int datalen = Math.min(DATASIZE, fileContents.length - filePointer);
        Integer[] pkt = new Integer[HEADERSIZE + datalen];
        // write something random into the header byte
        pkt[0] = 0xff & z;
        pkt[1] = (0xff00 & z) >>> 8;
        int fileLength = fileContents.length;
        System.out.println("length of file = " + fileLength);

        pkt[2] = 0x0ff & fileLength;
        pkt[3] = (0xff00 & fileLength) >>> 8;
        pkt[4] = (0xff0000 & fileLength) >>> 16;


        // copy databytes from the input file into data part of the packet, i.e., after the header
        System.arraycopy(fileContents, filePointer, pkt, HEADERSIZE, datalen);

        // send the packet to the network layer
        getNetworkLayer().sendPacket(pkt);
        System.out.println("Sent one packet from timeout with header="+pkt[0]);

        framework.Utils.Timeout.SetTimeout(2000, this, z);

    }

    @Override
    public Integer[] receiver() {
        System.out.println("Receiving...");
        int lastPacket = -1;

        // create the array that will contain the file contents
        // note: we don't know yet how large the file will be, so the easiest (but not most efficient)
        // is to reallocate the array every time we find out there's more data
        Integer[] fileContents = new Integer[0];
        // loop until we are done receiving the file
        boolean stop = false;
        while (!stop) {

            // try to receive a packet from the network layer
            Integer[] packet = getNetworkLayer().receivePacket();

            // if we indeed received a packet
            if (packet != null && (packet[0] + (packet[1] << 8)) > lastPacket) {

                // tell the user

                lastPacket = packet[0] + (packet[1] << 8);
                System.out.println("Received packet, length= "+packet.length+", header: "+lastPacket);
                Integer[] ack = new Integer[2];
                ack[0] = packet[0];
                ack[1] = packet[1];
                int fileLength = packet[2] + (packet[3] << 8) + (packet[4] << 16);
                System.out.println("filelength is " + fileLength);
                getNetworkLayer().sendPacket(ack);
                System.out.println("sending acknowledgement");

                // append the packet's data part (excluding the header) to the fileContents array, first making it larger
                int oldlength=fileContents.length;
                int datalen= packet.length - HEADERSIZE;
                fileContents = Arrays.copyOf(fileContents, oldlength+datalen);
                System.arraycopy(packet, HEADERSIZE, fileContents, oldlength, datalen);
                System.out.println("length of current file: " + fileContents.length);
                if(fileContents.length >= fileLength){
                    System.out.println("stopping because file: " + fileLength + "is larger than current length " + fileContents.length);
                    stop = true;
                }

            }else if (packet != null){
                Integer[] ack = new Integer[2];
                ack[0] = 0xff & lastPacket;
                ack[1] = (0xff00 & lastPacket) >>> 8;
                getNetworkLayer().sendPacket(ack);
            } else {
                // wait ~10ms (or however long the OS makes us wait) before trying again
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    System.out.println("interrupted");
                    stop = true;
                }
            }
        }

        // return the output file
        return fileContents;
    }
}
