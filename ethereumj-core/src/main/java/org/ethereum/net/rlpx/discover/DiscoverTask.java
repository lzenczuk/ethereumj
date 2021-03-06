package org.ethereum.net.rlpx.discover;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.socket.DatagramPacket;
import org.ethereum.crypto.ECKey;
import org.ethereum.net.rlpx.FindNodeMessage;
import org.ethereum.net.rlpx.Message;
import org.ethereum.net.rlpx.Node;
import org.ethereum.net.rlpx.discover.table.KademliaOptions;
import org.ethereum.net.rlpx.discover.table.NodeEntry;
import org.ethereum.net.rlpx.discover.table.NodeTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class DiscoverTask implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger("discover");

    NodeManager nodeManager;

    byte[] nodeId;

    public DiscoverTask(NodeManager nodeManager) {
        this.nodeManager = nodeManager;
        nodeId = nodeManager.homeNode.getId();
    }

    @Override
    public void run() {
        discover(nodeId, 0, new ArrayList<Node>());
    }

    public synchronized void discover(byte[] nodeId, int round, List<Node> prevTried) {

        logger.info("--------> Run discovery task: "+ Hex.toHexString(nodeId));

        try {
//        if (!channel.isOpen() || round == KademliaOptions.MAX_STEPS) {
//            logger.info("{}", String.format("Nodes discovered %d ", table.getAllNodes().size()));
//            return;
//        }

            if (round == KademliaOptions.MAX_STEPS) {
                logger.debug("Node table contains [{}] peers", nodeManager.getTable().getNodesCount());
                logger.debug("{}", String.format("(KademliaOptions.MAX_STEPS) Terminating discover after %d rounds.", round));
                logger.trace("{}\n{}", String.format("Nodes discovered %d ", nodeManager.getTable().getNodesCount()), dumpNodes());
                return;
            }

            List<Node> closest = nodeManager.getTable().getClosestNodes(nodeId);
            List<Node> tried = new ArrayList<>();

            int cn = closest == null ? 0: closest.size();

            logger.info("--------> Closest nodes: "+ cn);

            for (Node n : closest) {
                if (!tried.contains(n) && !prevTried.contains(n)) {
                    try {
                        nodeManager.getNodeHandler(n).sendFindNode(nodeId);
                        tried.add(n);
                        Thread.sleep(50);
                    }catch (InterruptedException e) {
                    } catch (Exception ex) {
                        logger.error("Unexpected Exception " + ex, ex);
                    }
                }
                if (tried.size() == KademliaOptions.ALPHA) {
                    break;
                }
            }

//            channel.flush();

            if (tried.isEmpty()) {
                logger.debug("{}", String.format("(tried.isEmpty()) Terminating discover after %d rounds.", round));
                logger.trace("{}\n{}", String.format("Nodes discovered %d ", nodeManager.getTable().getNodesCount()), dumpNodes());
                return;
            }

            tried.addAll(prevTried);

            discover(nodeId, round + 1, tried);
        } catch (Exception ex) {
            logger.info("{}", ex);
        }
    }

    private String dumpNodes() {
        String ret = "";
        for (NodeEntry entry : nodeManager.getTable().getAllNodes()) {
            ret += "    " + entry.getNode() + "\n";
        }
        return ret;
    }
}
