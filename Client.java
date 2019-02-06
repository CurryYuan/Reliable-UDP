import java.net.*;
import java.io.*;
import java.util.*;

public class Client{
    DatagramSocket s;
    String filename, initString;
    byte[] buffer;
    DatagramPacket initPacket, receivedPacket;
    FileOutputStream fileWriter;
    int bytesReceived, bytesToReceive;
    private Vector<MyData> tempData;


    public Client(int port) throws IOException
    {
        // Init stuff
        s = new DatagramSocket(port);
        buffer = new byte[8192];
        tempData=new Vector<>();

        System.out.println(" -- Ready to receive file on port: "+port);


        // 1. Wait for a sender to transmit the filename
        initPacket = receivePacket();

        initString = "Recieved-"+new String(initPacket.getData(), 0, initPacket.getLength());
        StringTokenizer t = new StringTokenizer(initString, "::");
        filename = t.nextToken();
        bytesToReceive = Integer.valueOf(t.nextToken());

        System.out.println("  -- The file will be saved as: "+filename);
        System.out.println("  -- Expecting to receive: "+bytesToReceive+" bytes");

        // 2. Send an reply containing OK to the sender
        send(initPacket.getAddress(), initPacket.getPort(), (new String("OK")).getBytes());
        //System.out.println("send something to port: "+initPacket.getPort());


        // 3. Receive the contents of the file
        fileWriter = new FileOutputStream(filename);
        int seqNum=1;

        while(bytesReceived < bytesToReceive) {
            receivedPacket = receivePacket();
            Object obj = bytesToObj(receivedPacket.getData());
            MyData myData = (MyData) obj;
            int revNum=myData.getSeqNum();

            if(revNum<seqNum){
                System.out.println("receive duplicate package " + String.valueOf(revNum));
                send(initPacket.getAddress(), initPacket.getPort(), String.valueOf(revNum).getBytes());
            } else if (revNum - seqNum < 100){
                tempData.add(myData);
                System.out.println("receive package " + String.valueOf(revNum));
                send(initPacket.getAddress(), initPacket.getPort(), String.valueOf(revNum).getBytes());
                if(revNum!=seqNum){
                    send(initPacket.getAddress(), initPacket.getPort(), String.valueOf(seqNum-1).getBytes());
                }

                for(int i=0;i<tempData.size();++i){
                    if(tempData.get(i).getSeqNum()==seqNum){
                        fileWriter.write(tempData.get(i).getMsg(), 0, tempData.get(i).getMsg().length);
                        bytesReceived = bytesReceived + tempData.get(i).getMsg().length;
                        ++seqNum;
                        tempData.remove(i);
                        i=0;
                    }
                }
            }
        }
        System.out.println("  -- File transfer complete.");
    }


    public DatagramPacket receivePacket() throws IOException{

        DatagramPacket packet =
                new DatagramPacket(buffer, buffer.length);
        s.receive(packet);

        return packet;
    }

    public byte[] receiveData() throws IOException{

        DatagramPacket packet =
                new DatagramPacket(buffer, buffer.length);
        s.receive(packet);

        return packet.getData();
    }

    public void send(InetAddress recv, int port,byte[] message)
            throws IOException {

        //InetAddress recv = InetAddress.getByName(host);
        DatagramPacket packet =
                new DatagramPacket(message, message.length, recv, port);
        s.send(packet);
    }

    public static void main(String[] args){
        try {
            Client client=new Client(9090);
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    /**
     * Byte数组转对象
     * @param bytes
     * @return
     */
    public static Object bytesToObj(byte[] bytes) {
        Object obj = null;
        ByteArrayInputStream byteArrayInputStream = null;
        ObjectInputStream objectInputStream = null;
        try {
            byteArrayInputStream = new ByteArrayInputStream(bytes);
            objectInputStream = new ObjectInputStream(byteArrayInputStream);
            obj = objectInputStream.readObject();
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("byteArrayToObject failed, " + e);
        } finally {
            if (byteArrayInputStream != null) {
                try {
                    byteArrayInputStream.close();
                } catch (IOException e) {
                    System.out.println("close byteArrayInputStream failed, " + e);
                }
            }
            if (objectInputStream != null) {
                try {
                    objectInputStream.close();
                } catch (IOException e) {
                    System.out.println("close objectInputStream failed, " + e);
                }
            }
        }
        return obj;
    }

}