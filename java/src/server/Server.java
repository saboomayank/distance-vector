package server;
import model.distanceVector;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;


public class Server extends Thread{
    private int port = 0;
    public Server(int port)
    {
        this.port = port;
    }
    public void run() {
        try
        {
            distanceVector.read = Selector.open();
            distanceVector.write = Selector.open();
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.bind(new InetSocketAddress(port));
            while(true)
            {
                SocketChannel socketChannel=serverSocketChannel.accept();
                if(socketChannel != null)
                {
                    socketChannel.configureBlocking(false);
                    socketChannel.register(distanceVector.read, SelectionKey.OP_READ);
                    socketChannel.register(distanceVector.write, SelectionKey.OP_WRITE);
                    distanceVector.openChannels.add(socketChannel);
                    System.out.println("The connection to peer "+ distanceVector.parseChannelIp(socketChannel)+" is succesfully established");
                }
            }
        }
        catch(IOException i)
        {
            System.out.println(i);
        }
    }
}