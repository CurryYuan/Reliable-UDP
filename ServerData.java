public class ServerData {
    private MyData data;
    private long sendTime;
    private int times;
    private int seqNum;
    public boolean isAck;

    public MyData getData() {
        return data;
    }

    public void setData(MyData data) {
        this.data = data;
    }

    public long getSendTime() {
        return sendTime;
    }

    public void setSendTime() {
        this.sendTime = System.currentTimeMillis();
    }

    public int getTimes() {
        return times;
    }

    public void setTimes() {
        this.times++;
    }

    public int getSeqNum() {
        return seqNum;
    }

    public void setSeqNum(int seqNum) {
        this.seqNum = seqNum;
    }

    public boolean timeOut(){
        return (System.currentTimeMillis()-this.sendTime>3000);
    }

    public ServerData(MyData data) {
        this.data = data;
        this.sendTime = System.currentTimeMillis();
        this.times = 1;
        this.seqNum=data.getSeqNum();
        this.isAck=false;
    }
}
