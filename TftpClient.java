import java.net.*;
import java.io.*;

class TftpClient
{
    public static void main(String args[])
    {
	/* expect three arguments */
	if(args.length != 3) {
	    System.err.println("usage: TftpClient <name> <port> <file>\n");
	    return;
	}

	/* process the command line arguments */
	String name = args[0];
	String filename = args[2];

	/*
	 * use Integer.parseInt to get the number from the second
	 * (port) argument
	 */
	int port;
    port = Integer.parseInt(args[1]);

	/*
	 * use InetAddress.getByName to get an IP address for the name
	 * argument
	 */
	InetAddress ia;
	try {
        ia = InetAddress.getByName(name);
    } catch (UnknownHostException e) {
        System.err.println(e);
        return;
    }

	/* allocate a DatagramSocket, and setSoTimeout to 6s (6000ms) */
	DatagramSocket ds;
	try {
		ds = new DatagramSocket();
        ds.setSoTimeout(6000);
    } catch (SocketException e) {
        System.err.println(e);
        return;
    }

	/*
	 * open an output file; preface the filename with "rx-" so
	 * that you do not try to overwrite a file that the server is
	 * about to try to send to you.
	 */
	FileOutputStream fos;
	try {
        File output = new File("rx-" + filename);
        fos = new FileOutputStream(output);
    } catch (FileNotFoundException e) {
        System.err.println(e);
        return;
    }

	/*
	 * create a read request using TftpPacket.createRRQ and then
	 * send the packet over the DatagramSocket.
	 */
	DatagramPacket rrqPacket = TftpPacket.createRRQ(ia, port, filename);
    try {
        ds.send(rrqPacket);
    } catch (IOException e) {
        System.err.println(e);
        return;
    }

	/*
	 * declare an integer to keep track of the block that you
	 * expect to receive next, initialized to one.  allocate a
	 * byte buffer of 514 bytes (i.e., 512 block size plus two one
	 * byte header fields) to receive DATA packets.  allocate a
	 * DatagramPacket backed by that byte buffer to pass to
	 * DatagramSocket::receive to receive packets into.
	 */
	int nextBlock = 1;
    byte[] receiveBuffer = new byte[514];
    DatagramPacket dpReceive = new DatagramPacket(receiveBuffer, receiveBuffer.length);

	/*
	 * an infinite loop that we will eventually break out of, when
	 * either an exception occurs, or we receive a block less than
	 * 512 bytes in size.
	 */
	while(true) {
	    try {
		/*
		 * receive a packet on the DatagramSocket, and then
		 * parse it with TftpPacket.parse.  get the IP address
		 * and port where the packet came from.  The port will
		 * be different to the port you sent the RRQ to, and
		 * we will use these values to transmit the ACK to
		 */
		ds.receive(dpReceive);
        TftpPacket tp = TftpPacket.parse(dpReceive);

        InetAddress addressReceived = tp.getAddr();
        int portReceived = tp.getPort();

		/*
		 * if we could not parse the packet (parse returns
		 * null), then use "continue" to loop again without
		 * executing the remaining code in the loop.
		 */
		
        if(tp == null) continue;
		 

		/*
		 * if the response is an ERROR packet, then print the
		 * error message and return.
		 */
		if (tp.getType() == TftpPacket.Type.ERROR) {
            System.out.println(tp.getError());
            return;
        }

		/*
		 * if the packet is not a DATA packet, then use
		 * "continue" to loop again without executing the
		 * remaining code in the loop.
		 */
		if (tp.getType() != TftpPacket.Type.DATA) {
            continue;
        }

		/*
		 * if the block number is exactly the block that we
		 * were expecting, then get the data (TftpPacket::getData)
		 * and then write it to disk.  then, send an ack for the
		 * block.  then, check to see if we received less than
		 * 512 bytes in that block; if we did, then we infer that
		 * the sender has finished, and break out of the while loop.
		 */
		if (nextBlock == tp.getBlock()) {
            byte[] data = tp.getData();
            fos.write(data);

            DatagramPacket ackPacket = TftpPacket.createACK(addressReceived, portReceived, nextBlock);
            ds.send(ackPacket);

            if (data.length < 512) break;
            
            nextBlock = TftpPacket.nextBlock(nextBlock);

        }

		/*
		 * else, if the block number is the same as the block
		 * number we just received, send an ack without writing
		 * the block to disk, etc.  in this case, the server
		 * didn't receive the ACK we sent, and retransmitted.
		 */
		else {
            DatagramPacket ackPacket = TftpPacket.createACK(addressReceived, portReceived, tp.getBlock());
            ds.send(ackPacket);
        }
	    } catch(Exception e) {
		System.err.println("Exception: " + e);
		break;
	    }
	}

	/* cleanup -- close the output file and the DatagramSocket */
	try {
        fos.close();
        ds.close();
    } catch (IOException e) {
        System.err.println(e);
    }
    }
}