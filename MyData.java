import java.io.Serializable;
import java.nio.ByteBuffer;

public class MyData implements Serializable{
    private int seqNum;
    private byte[] msg;

    private static final long serialVersionUID = -5809782578272943999L;

    public MyData(int seqNum, byte[] msg) {
        this.seqNum = seqNum;
        this.msg = msg;
    }

    public byte[] getMsg() {
        return msg;
    }

    public void setMsg(byte[] msg) {
        this.msg = msg;
    }

    public int getSeqNum() {
        return seqNum;
    }

    public void setSeqNum(int seqNum) {
        this.seqNum = seqNum;
    }

/*    @Override
    public String toString() {
        return new String("ddd");
    }*/
}
