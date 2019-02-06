import java.net.*;
import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Vector;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

/**
 * The object is bound to a specified receiver host and port when created, and is able to
 * send the contents of a file to this receiver.
 */
public class Server {
    private File theFile;
    private FileInputStream fileReader;
    private DatagramSocket s;
    private int fileLength, currentPos, bytesRead, toPort;
    private byte[] msg, buffer;
    private String toHost, initReply;
    private InetAddress toAddress;
    private int seqNum,sendNum;
    private Vector<ServerData> tempData;
    private int state;  //1代表慢启动，2代表拥堵避免
    private int rwnd,cwnd,ssthresh;  //接受窗口，拥堵窗口,慢启动阀值
    private int lastAckNum,ackTime;
    private ServerData lastServerData;


    /**
     * Class constructor.
     * Creates a new UDPSender object capable of sending a file to the specified address and port.
     *
     * @param address the address of the receiving host
     * @param port    the listening port on the receiving host
     */
    public Server(InetAddress address, int port) throws IOException {
        toPort = port;
        toAddress = address;
        msg = new byte[8000];
        buffer = new byte[8192];
        seqNum = sendNum = 1;
        state=1;
        rwnd=1000;
        cwnd=1;
        ssthresh=1000;
        lastAckNum=-1;
        ackTime=0;
        tempData=new Vector<>();
        s = new DatagramSocket();
        s.connect(toAddress, toPort);
    }


    /**
     * Sends a file to the bound host.
     * Reads the contents of the specified file, and sends it via UDP to the host
     * and port specified at when the object was created.
     *
     * @param theFile the file to send
     */
    public void sendFile(File theFile) throws IOException {
        // Init stuff
        fileReader = new FileInputStream(theFile);
        fileLength = fileReader.available();

        System.out.println(" -- Filename: " + theFile.getName());
        System.out.println(" -- Bytes to send: " + fileLength);

        // 1. Send the filename to the receiver
        send((theFile.getName() + "::" + fileLength).getBytes());

        // 2. Wait for a reply from the receiver
        DatagramPacket reply = new DatagramPacket(buffer, buffer.length);
        s.receive(reply);
        //initReply = (new String(reply.getData(), 0, reply.getLength()));


        // 3. Send the content of the file
        if (new String(reply.getData(), 0, reply.getLength()).equals("OK")) {
            System.out.println("  -- Got OK from receiver - sending the file ");
            // 启动定时器线程，并在1000毫秒后开始，每隔1000毫秒执行一次定时任务
            Timer timer = new Timer();
            timer.schedule(new TimerTask() {

                @Override
                public void run() {
                    if (state == 1 && cwnd < ssthresh) {
                        cwnd *= 2;
                    } else {
                        cwnd++;
                    }

                    for (int i = 0; i < tempData.size(); ++i) {
                        if (!tempData.get(i).isAck && tempData.get(i).timeOut()) {
                            System.out.println("Timeout for package " + String.valueOf(tempData.get(i).getSeqNum()));
                            state = 2;    //没有收到确认，进入拥堵避免
                            ssthresh /= 2;
                            resend(tempData.get(i));
                        }
                    }
                }

            }, 1000,1000);

            while (currentPos < fileLength) {
                int windowSize=cwnd>rwnd?rwnd:cwnd;
                if (sendNum - seqNum < windowSize) {
                    bytesRead = fileReader.read(msg);
                    MyData myData = new MyData(sendNum, msg);
                    tempData.add(new ServerData(myData));
                    send(objToBytes(myData));
                    System.out.println("send package " + String.valueOf(sendNum));
                    ++sendNum;
                    currentPos = currentPos + bytesRead;
                    try {
                        Thread.sleep(1);
                    }catch (Exception e){
                        e.printStackTrace();
                    }
                    new Thread(() -> {
                        receiveMsg();
                    }).start();

                }
            }
            System.out.println("  -- File transfer complete...");
            timer.cancel();
        } else {
            System.out.println("Recieved something other than OK... exiting");
        }
    }


    private void send(byte[] message){
        try {
            DatagramPacket packet =
                    new DatagramPacket(message, message.length);
            s.send(packet);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void receiveMsg(){
        try {
            DatagramPacket ack = new DatagramPacket(buffer, buffer.length);
            s.receive(ack);
            int ackNum = Integer.parseInt(new String(ack.getData(), 0, ack.getLength()));
            if(ackNum==lastAckNum){
                state=2;    //没有收到确认，进入拥堵避免
                ssthresh/=2;
                ackTime++;
                if(ackTime>=3){
                    //快速重传
                    System.out.println("receive 3 times duplicate ack package "+String.valueOf(ackNum));
                    for(int i=0;i<tempData.size();++i){
                        if(tempData.get(i).getSeqNum()==ackNum+1){
                            resend(tempData.get(i));
                        }
                    }
                }
            }else{
               lastAckNum=ackNum;
               ackTime=0;
            }

            for(int i=0;i<tempData.size();++i){
                if(tempData.get(i).getSeqNum()==ackNum){
                    tempData.get(i).isAck=true;
                    System.out.println("ack package " + ackNum);
                    break;
                }
            }

            for(int i=0;i<tempData.size();++i) {
                if(tempData.get(i).isAck && tempData.get(i).getSeqNum()==seqNum){
                    ++seqNum;
                    tempData.remove(i);
                    i=0;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void resend(ServerData serverData) {
        send(objToBytes(serverData.getData()));
        System.out.println("resend package " + String.valueOf(sendNum) +
                " for the " + String.valueOf(serverData.getTimes()) + " times");
        serverData.setSendTime();
        serverData.setTimes();
        if (serverData.getTimes() > 100) {
            System.out.println("Transfer timeout, abort!");
            System.exit(1);
        }

        new Thread(() -> {
            receiveMsg();
        }).start();
    }

    public static void main(String[] args) {
        try {
            Server server = new Server(InetAddress.getByName("127.0.0.1"), 9090);
            server.sendFile(new File("a.txt"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 对象转Byte数组
     * @param obj
     * @return
     */
    public static byte[] objToBytes(Object obj) {
        byte[] bytes = null;
        ByteArrayOutputStream byteArrayOutputStream = null;
        ObjectOutputStream objectOutputStream = null;
        try {
            byteArrayOutputStream = new ByteArrayOutputStream();
            objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
            objectOutputStream.writeObject(obj);
            objectOutputStream.flush();
            bytes = byteArrayOutputStream.toByteArray();
        } catch (IOException e) {
            System.out.println("objectToByteArray failed, " + e);
        } finally {
            if (objectOutputStream != null) {
                try {
                    objectOutputStream.close();
                } catch (IOException e) {
                    System.out.println("close objectOutputStream failed, " + e);
                }
            }
            if (byteArrayOutputStream != null) {
                try {
                    byteArrayOutputStream.close();
                } catch (IOException e) {
                    System.out.println("close byteArrayOutputStream failed, " + e);
                }
            }

        }
        return bytes;
    }
}