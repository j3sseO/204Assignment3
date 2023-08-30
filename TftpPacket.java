import java.net.DatagramPacket;
import java.net.InetAddress;

class TftpPacket
{
    /* private internal variables used by the parse method */
    public enum Type {RRQ, DATA, ACK, ERROR};
    private InetAddress addr = null;
    private int port = 0;
    private Type type = null;
    private String filename = null;
    private String error = null;
    private int block = 0;
    private byte[] data = null;

    /* getter methods for these private variables */
    public InetAddress getAddr() { return addr; }
    public int getPort() { return port; }
    public Type getType() { return type; }
    public String getFilename() { return filename; }
    public String getError() { return error; }
    public int getBlock() { return block; }
    public byte[] getData() { return data; }

    /* utility method to convert the TftpPacket to a string form */
    public String toString()
    {
	if(type == null)
	    return "null";
	if(type == Type.RRQ)
	    return "RRQ " + (filename != null ? filename : "<null>");
	else if(type == Type.DATA)
	    return "DATA " + block + " " + data.length;
	else if(type == Type.ACK)
	    return "ACK " + block;
	else if(type == Type.ERROR)
	    return "ERROR " + (error != null ? error : "<null>");
	return "unknown TftpPacket";
    }

    /*
     * utility method to determine what the next block number should
     * be, given the current block number.
     */
    static int nextBlock(int current)
    {
        // valid block numbers are in the range 1.. 255
        if (current == 255) {
            return 1;
        } else {
            return current + 1;
        }
    }

    /*
     * utility method to determine what the previous block number was,
     * given the current block number.
     */
    static int lastBlock(int current)
    {   
        // valid block numbers are in the range 1.. 255
        if (current == 1) {
            return 255;
        } else {
            return current - 1;
        }
    }

    /*
     * utility method to create a RRQ DatagramPacket, given the
     * destination address, port number, and filename.
     */
    static public DatagramPacket createRRQ(InetAddress dst, int port,
					   String filename)
    {
	/*
	 * if the filename is empty, the destination address is null,
	 * or the port number is invalid, then we cannot build a valid
	 * DatagramPacket, so return null.
	 */
	if(filename.isEmpty() || dst == null || port < 1 || port > 65535)
	    return null;

	/*
	 * convert the filename to a byte array, and then allocate a
	 * second byte array (payload) that includes the one byte RRQ
	 * type, followed by the filename byte array
	 */
	byte[] bytes = filename.getBytes();
	byte[] payload = new byte[1 + bytes.length];
	payload[0] = 1; /* 1 == RRQ */

	/*
	 * copy the filename, which we converted to a string, into the
	 * payload
	 */
	System.arraycopy(bytes, 0, payload, 1, bytes.length);

	/* return a DatagramPacket with the payload / dst / port */
	return new DatagramPacket(payload, payload.length, dst, port);
    }

    /*
     * utility method to create an ACK DatagramPacket, given the
     * destination address, port number, and block number to include
     * in the ACK.
     */
    static public DatagramPacket createACK(InetAddress dst, int port, int block)
    {
	/*
	 * if the block number is invalid, the destination address is
	 * null, or the port number is invalid, then we cannot build a
	 * valid DatagramPacket, so return null.
	 */
        if (block < 1 || block > 255 || dst == null || port <  1 || port > 65535) {
            return null;
        }

        // creates new byte array for our ACK packet and sets the type field and block number
        byte[] payload = new byte[2];
        payload[0] = 3;
        payload[1] = (byte) block; // casts block number into byte value and stores in our ACK payload.

        DatagramPacket ackPacket = new DatagramPacket(payload, payload.length, dst, port);
        return ackPacket;
    }

    /*
     * utility method to create a DATA DatagramPacket, given the
     * destination address, port number, block number, and payload.
     */
    static public DatagramPacket createDATA(InetAddress dst, int port,
					    int block, byte[] data, int length)
    {
	/*
	 * if the block number is invalid, the length is invalid, the
	 * destination address is null, or the port number is invalid,
	 * then we cannot build a valid DatagramPacket, so return
	 * null.
	 */
        if (block < 1 || block > 255 || length < 0 || length > 512 || dst == null || port < 1 || port > 65535) {
            return null;
        }

        // creates a new byte array for our DATA packet and sets the type field and block number.
        byte[] payload = new byte[length + 2];
        payload[0] = 2;
        payload[1] = (byte) block; // casts block number into byte value and stores in our DATA payload.

        System.arraycopy(data, 0, payload, 2, length); // copies bytes from data parameter into our payload


        DatagramPacket dataPacket = new DatagramPacket(payload, payload.length, dst, port);
        return dataPacket;
    }

    /*
     * utility method to create an ERROR DatagramPacket, given
     * the destination address, port number, and error string.
     */
    static public DatagramPacket createERROR(InetAddress dst, int port,
					     String error)
    {
	/*
	 * if the error string is empty, the destination address is
	 * null, or the port number is invalid, then we cannot build a
	 * valid DatagramPacket, so return null.
	 */
        if (error.isEmpty() || dst == null || port < 1 || port > 65536) {
            return null;
        }
        
        // creates a new byte array for out ERROR packet and sets the type field and block number
        byte[] errorBytes = error.getBytes(); // converts error string into bytes so we can use the length and copy it into our payload 
        byte[] payload = new byte[errorBytes.length + 1];
        payload[0] = 4;

        System.arraycopy(errorBytes, 0, payload, 1, errorBytes.length); // copies bytes from error parameter into our payload   

        DatagramPacket errorPacket = new DatagramPacket(payload, payload.length, dst, port);
        return errorPacket;
    }

    /*
     * utility method to parse a DatagramPacket, so that the user of
     * the class can access parts of each TFTP packet that they are
     * interested in without having to carefully parse the packet.
     */
    static public TftpPacket parse(DatagramPacket in)
    {
	if(in == null)
	    return null;

	TftpPacket p = new TftpPacket();
	byte[] data = in.getData();
	int length = in.getLength();

	/* there must be at least one byte for a TFTP type field */
	if(length < 1)
	    return null;

	/* keep a copy of the address / port */
	p.addr = in.getAddress();
	p.port = in.getPort();

	if(data[0] == 1) {
	    /*
	     * parse the RRQ.  the filename must be at least one byte,
	     * so the length must be at least two
	     */
	    if(length < 2)
		return null;
	    p.type = Type.RRQ;

	    /*
	     * use a string constructor that builds a string from a
	     * byte array.  the first parameter is 1 because that's
	     * the offset in the byte array to find the first
	     * character of the string.
	     */
	    p.filename = new String(data, 1, length - 1);

	    /* we've decoded the packet, so return it now */
	    return p;
	}
	else if(data[0] == 2) {
	    /*
	     * parse the DATA packet, which must be at least 2 bytes
	     * in size (for the type and block number fields).  use
	     * Java's Byte.toUnsignedInt to treat the byte as an
	     * unsigned integer when creating the integer value.
	     * extract the type and block
	     */
	    if (length < 2) return null;
        p.type = Type.DATA;
        p.block = Byte.toUnsignedInt(data[1]);

	    /*
	     * to extract the data, allocate a new byte array sized
	     * exactly the size needed (length-2) and use
	     * System.arraycopy to copy the data out of the packet.
	     */
	    byte[] extractedData = new byte[length - 2];
        p.data = extractedData;
        System.arraycopy(data, 2, p.data, 0, length - 2);

        return p;
	}
	else if(data[0] == 3) {
	    /*
	     * parse the ACK packet, which must be exactly 2 bytes in
	     * size (the type and block number fields
	     */
	    if (length < 2) return null;
        p.type = Type.ACK;
        p.block = Byte.toUnsignedInt(data[1]);

        return p;
	}
	else if(data[0] == 4) {
	    /*
	     * parse the ERROR packet, which must be at least 2 bytes
	     * in size (for the type and error strings)
	     */
	    if (length < 2) return null;
        p.type = Type.ERROR;
        p.error = new String(data, 1, length - 1);

        return p;
	}

	/*
	 * the packet could not be decoded because it used an
	 * unrecognized type, so return null
	 */
	return null;
    }
}