import java.net.*;
import java.io.*;

class TftpServerWorker extends Thread
{
    private DatagramPacket req;

    public void run()
    {
        /* parse the request packet, ensuring that it is a RRQ. */
		TftpPacket tp = TftpPacket.parse(req);

		if (tp.getType() != TftpPacket.Type.RRQ || tp == null) {
			return;
		}

		/*
		* make a note of the address and port the client's request
		* came from
		*/
		String fileName = tp.getFilename();
		InetAddress address = tp.getAddr();
		int port = tp.getPort();
		System.out.println(fileName);
		
		/* create a datagram socket to send on, setSoTimeout to 1s (1000ms) */
		DatagramSocket ds;
		try {
			ds = new DatagramSocket();
			ds.setSoTimeout(1000);
		} catch (SocketException e) {
			System.err.println(e);
			return;
		}

		/* try to open the file.  if not found, send an error */
		FileInputStream fileReader = null;
		try {
			fileReader = new FileInputStream(new File(fileName));
		} catch (FileNotFoundException e) {
			DatagramPacket dp = TftpPacket.createERROR(address, port, 
											fileName + " was not found.");
			try {
				ds.send(dp);
				return;
			} catch (IOException er) {
				System.err.println(er);
			}
		}

		/*
		* Allocate a txbuf byte buffer 512 bytes in size to read
		* chunks of a file into, and declare an integer that keeps
		* track of the current block number initialized to one.
		*
		* allocate a rxbuf byte buffer 2 bytes in size to receive
		* TFTP ack packets into, and then allocate a DatagramPacket
		* backed by that rxbuf to pass to the DatagramSocket::receive
		* method
		*/
		byte[] txbuf = new byte[512];
		int nextBlock = 1;
		byte[] rxbuf = new byte[2];
		DatagramPacket ackPacket = new DatagramPacket(rxbuf, rxbuf.length);

		while(true) {
			/*
			* read a chunk from the file, and make a note of the size
			* read.  if we get EOF, signalled by
			* FileInputStream::read returning -1, then set the chunk
			* size to zero to cause an empty block to be sent.
			*/
			int size = 0;

			try {
				size = fileReader.read(txbuf);
			} catch (IOException e) {
				System.err.println(e);
			}

			if (size == -1) {
				size = 0;
			}

			/*
			* use TftpPacket.createData to create a DATA packet
			* addressed to the client's address and port, specifying
			* the block number, the contents of the block, and the
			* size of the block
			*/
			DatagramPacket dataPacket = TftpPacket.createDATA(address, port, 
														nextBlock, txbuf, size);

			/*
			* declare a boolean value to control transmission through
			* each loop, and an integer to count the number of
			* transmission attempts made with the current block.
			*/
			boolean transmission = true;
			int attemptsMade = 0;

			while(transmission) {
				/*
				* if we are to transmit the packet this pass through
				* the loop, send the packet and increment the number
				* of attempts we have made with this block.  set the
				* boolean value to false to prevent the packet being
				* retransmitted except on a SocketTimeoutException,
				* noted below.
				*/

				try {
					ds.send(dataPacket);
				} catch (IOException e) {
					System.err.println(e);
				}

				attemptsMade++;
				transmission = false;
				
				/*
				* call receive, looking for an ACK for the current
				* block number.  if we get an ack, break out of the
				* retransmission loop.  otherwise, if we get a
				* SocketTimeoutException, set the boolean value to
				* true.  if we have tried five times, then we break
				* out of the loop to give up.
				*/
				try {
					ds.receive(ackPacket);
					TftpPacket receivedACK = TftpPacket.parse(ackPacket);
					if (receivedACK.getBlock() != nextBlock || receivedACK.getType() != TftpPacket.Type.ACK) throw new SocketException();
				} catch (SocketException e) {
					transmission = true;
					attemptsMade++;
					if (attemptsMade == 5) break;
				} catch (IOException e) {
					System.err.println(e);		
				}
			}

			/*
			* outside of the loop, determine if we just sent our last
			* transmission (the block size was less than 512 bytes,
			* or we tried five times without getting an ack
			*/
			if (size < 512 || attemptsMade == 5) {
				break;
			}

			/*
			* use TftpPacket.nextBlock to determine the next block
			* number to use.
			*/
			nextBlock = TftpPacket.nextBlock(nextBlock);
		}

		/* cleanup: close the FileInputStream and the DatagramSocket */
		try {
			fileReader.close();
			ds.close();
		} catch (IOException e) {
			System.err.println(e);
		}

		return;
		}

	public TftpServerWorker(DatagramPacket req) {
		this.req = req;
	}
}

class TftpServer
{
    public static void main(String args[])
    {
	try {
	    /*
	     * allocate a DatagramSocket, and find out what port it is
	     * listening on
	     */
	    DatagramSocket ds = new DatagramSocket();
	    System.out.println("TftpServer on port " + ds.getLocalPort());

	    for(;;) {
		/*
		 * allocate a byte buffer to back a DatagramPacket
		 * with.  I suggest 1472 byte array for this.
		 * allocate the corresponding DatagramPacket, and call
		 * DatagramSocket::receive
		 */
		byte[] buf = new byte[1472];
		DatagramPacket p = new DatagramPacket(buf, 1472);
		ds.receive(p);

		/*
		 * allocate a new worker thread to process this
		 * packet.  implement the logic looking for a RRQ in
		 * the worker thread's run method.
		 */
		TftpServerWorker worker = new TftpServerWorker(p);
		worker.start();
	    }
	}
	catch(Exception e) {
	    System.err.println("TftpServer::main Exception: " + e);
	}

	return;
    }
}