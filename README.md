# Reliable UDP
Using UDP to realize reliable data transfer

实现一个网络应用程序LFTP，用于支持网络上两台电脑的大文件传输。

使用UDP作为传输层协议，100%可靠，实现流量控制和拥堵控制。

# 传输功能实现
MyData 封装传输包的数据类，包括seqNum包序列号，msg要传输的数据分组。

ServerData 发送端用来缓存发送数据的类，包括MyData用来保存数据包，sendTime用来保存发送时间，times用来保存重发的次数，isAck表示是否收到回复。
发送端维护一个Vector<ServerData> tempData 作为缓冲区。
  
在开始发送文件后，启动一个定时器线程，每一秒更新一次拥堵控制的状态和cwnd的值。同时会检查缓冲区里没有ack的包是否超时，如果超时，启动超时重传。

窗口长度windowSize由接受窗口长度rwnd和拥堵窗口长度cwnd的最小值决定，rwnd由客户端返回的空闲窗口长度确定，cwnd则根据拥堵控制协议的算法确定。

当缓冲区的长度sendNum-seqNum<滑动窗口的长度windowSize时，fileReader读取一段长度为8000byte的数据msg，把它封装到传输对象MyData中，然后将其序列化，调用send函数发送。然后新开一个线程执行receiveMsg函数，等待客户端返回的ack。当每收到一个带有ack的数据包，判断该ack的seqNum是否和上次的相同，如果相同，判断是否连续三次收到，如果是，启动快速重传，重发seqNum为ackNum+1的包。如果不相同，移除缓冲区中序列号为lastAckNum的对象，seqNum+1，滑动窗口向前移动。同时更新lastAckNum的值和累积次数ackTime。

当缓冲区长度等于滑动窗口长度时，不再发送数据包。

接收端当缓冲区长度小于窗口长度rwnd时，如果接受到的包就是需要的下一个包，ack seqNum，不然的话，ack seqNum-1。
