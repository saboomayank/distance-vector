package client;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import model.Message;
import model.Node;
import model.distanceVector;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.*;

public class Client extends Thread
{
    Set<SelectionKey> keys;
    Iterator<SelectionKey> selectedKeysIterator;
    ByteBuffer buffer = ByteBuffer.allocate(5000);
    SocketChannel socketChannel;
    int bytesRead;
    public void run()
    {
        try {
            while(true){
                int channelReady = distanceVector.read.selectNow();
                keys = distanceVector.read.selectedKeys();
                selectedKeysIterator = keys.iterator();
                if(channelReady!=0){
                    while(selectedKeysIterator.hasNext()){
                        SelectionKey key = selectedKeysIterator.next();
                        socketChannel = (SocketChannel)key.channel();
                        try{
                            bytesRead = socketChannel.read(buffer);
                        }catch(IOException ie){
                            selectedKeysIterator.remove();
                            String IP = distanceVector.parseChannelIp(socketChannel);
                            Node node = distanceVector.getNodeByIP(IP);
                            distanceVector.disable(node);
                            System.out.println(IP+" remotely closed the connection!");
                            break;
                        }
                        String message = "";
                        while(bytesRead!=0){
                            buffer.flip();
                            while(buffer.hasRemaining()){
                                message+=((char)buffer.get());
                            }
                            ObjectMapper mapper = new ObjectMapper();
                            Message msg = null;
                            boolean messageReceived = false;
                            int fromID = 0;
                            try{
                                msg = mapper.readValue(message,Message.class);
                                messageReceived = true;
                                distanceVector.numberOfPacketsReceived++;
                                fromID = msg.getId();
                            }catch(JsonMappingException jme){
                                System.out.println("Server "+distanceVector.parseChannelIp(socketChannel)+" crashed.");
                            }
                            Node fromNode = distanceVector.getNodeById(fromID);
                            if(msg!=null){

                                if(msg.getType().equals("update") && messageReceived){
                                    List<String> receivedRT = msg.getRoutingTable();
                                    Map<Node,Integer> createdReceivedRT = makeRoutingTable(receivedRT);
                                    int presentCost = distanceVector.routingTable.get(fromNode);
                                    int updatedCost = createdReceivedRT.get(distanceVector.myNode);
                                    if(presentCost!=updatedCost){
                                        distanceVector.routingTable.put(fromNode,updatedCost);
                                    }
                                }
                                if(msg.getType().equals("step") && messageReceived) {
                                    List<String> receivedRT = msg.getRoutingTable();
                                    Map<Node,Integer> createdReceivedRT = makeRoutingTable(receivedRT);
                                    for(Map.Entry<Node, Integer> entry1 : distanceVector.routingTable.entrySet()){
                                        if(entry1.getKey().equals(distanceVector.myNode)){
                                            continue;
                                        }
                                        else{
                                            int presentCost = entry1.getValue();
                                            int costToReceipient = createdReceivedRT.get(distanceVector.myNode);
                                            int costToFinalDestination = createdReceivedRT.get(entry1.getKey());
                                            if(costToReceipient+costToFinalDestination < presentCost){
                                                distanceVector.routingTable.put(entry1.getKey(),costToReceipient+costToFinalDestination);
                                                distanceVector.nextHop.put(entry1.getKey(),fromNode);
                                            }
                                        }
                                    }

                                    if(msg.getType().equals("disable") || !messageReceived){
                                        distanceVector.routingTable.put(fromNode, Integer.MAX_VALUE-2);
                                        System.out.println("Routing Table updated with Server "+fromID+"'s cost set to infinity");
                                        if(distanceVector.isNeighbor(fromNode)){
                                            for(SocketChannel channel:distanceVector.openChannels){
                                                if(fromNode.getIp().equals(distanceVector.parseChannelIp(channel))){
                                                    try {
                                                        channel.close();
                                                    } catch (IOException e) {
                                                        System.out.println("Cannot close the connection;");
                                                    }
                                                    distanceVector.openChannels.remove(channel);
                                                    break;
                                                }
                                            }
                                            distanceVector.routingTable.put(fromNode, Integer.MAX_VALUE-2);
                                            distanceVector.neighbors.remove(fromNode);
                                        }
                                    }
                                }
                                if(message.isEmpty()){
                                    break;
                                }
                                else{
                                    System.out.println("Message received from Server "+msg.getId()+" ("+distanceVector.parseChannelIp(socketChannel)+")");
                                    System.out.println("Current Routing Table:-");
                                    distanceVector.display();
                                }
                                buffer.clear();
                                if(message.trim().isEmpty())
                                    bytesRead =0;
                                else{
                                    try{
                                        bytesRead = socketChannel.read(buffer);
                                    }catch(ClosedChannelException cce){
                                        System.out.println("Channel closed for communication with Server "+fromID+".");
                                    }
                                }

                                bytesRead=0;
                                selectedKeysIterator.remove();
                            }
                        }
                    }
                }
            }
        }catch(Exception e) {
            e.printStackTrace();
        }

    }
    private Map<Node, Integer> makeRoutingTable(List<String> receivedRT) {
        Map<Node,Integer> rt = new HashMap<Node,Integer>();
        for(String str:receivedRT){
            String[] parts = str.split("#");
            int id = Integer.parseInt(parts[0]);
            int cost = Integer.parseInt(parts[1]);
            rt.put(distanceVector.getNodeById(id), cost);
        }
        return rt;
    }

}
