package branchandprice;

import algorithm.Algorithm;
import algorithm.Column;
import algorithm.ColumnGeneration;
import gurobi.GRBEnv;
import gurobi.GRBException;
import utils.Global;

import java.util.*;

/**
 * Created by yuhuishi on 12/18/2016.
 * University of Michigan
 * Academic use only
 */
public class BranchAndPrice implements Algorithm {

    public static final int SOLVE_INTEGER_PROBLEM_INTERVAL = 1;
    public static final int INTEGER_PROBLEM_TIME_LIMIT = 300;
    public static final int MAX_ITERATIONS = 9999;
    public static final int MAX_NODE = 999;


    // singleton implementation
    private ArrayDeque<Node> pendingNode;
    private PriorityQueue<Node> pendingNodeBestBound;
    private final long initTime;

    private int nodesExplored;

    private double upperbound;

    private List<Column> bestIncumbent;

    private GRBEnv grbEnv;

    private static BranchAndPrice branchTree;

    public static BranchAndPrice getBranchTree() {
        if (null == branchTree)
            return branchTree = new BranchAndPrice();

        return branchTree;
    }

    private BranchAndPrice() {
        this.pendingNode = new ArrayDeque<>();
        this.pendingNodeBestBound = new PriorityQueue<>();

        this.bestIncumbent = new ArrayList<>();
        this.upperbound = Double.MAX_VALUE;
        this.nodesExplored = 0;

        initTime = System.currentTimeMillis();
    }

    public void solve() {
        // initiate the initial set of columns
        List<Column> initCols = ColumnGeneration.enumInitCol(2);

        // solve the root relaxation
        int nodesProcessed = 0;
        double rootObj = 0;
        Node root = new Node(initCols, new ArrayList<>(), -Double.MAX_VALUE);
        try {
            this.offerNode(root);
            while (!isQueueEmpty()) {
                System.out.println("Remaining nodes: " + queueSize());
                Node nodeToProcess = getPendingNode();
                if (!nodeToProcess.isProcessed())
                    nodesProcessed++;
                nodeToProcess.process();
                if (nodesProcessed==1)
                    rootObj = nodeToProcess.getObjVal();
            }

            if (nodesProcessed > MAX_NODE) {
                System.out.println("Termination due to MAX_NODE_REACHED.");
            }


        } catch (GRBException e) {
            e.printStackTrace();
        }

        // parse the solution
        assert bestIncumbent.size()>0;
        System.out.println("Used vehicles: " + bestIncumbent.size());
        double tardiness = bestIncumbent.stream().mapToDouble(Column::getCost).sum();
        System.out.println("Tardiness: " + tardiness);
        System.out.println("Obj val: " + upperbound);
        System.out.println("Nodes explored: " + nodesProcessed);
        assert rootObj>0;
        assert upperbound < Double.MAX_VALUE;

        System.out.println("Obj val: " + (upperbound-rootObj)/rootObj);

    }

    @Override
    public long getTimeTillNow() {
        return System.currentTimeMillis()-initTime;
    }

    private Node getBestBoundNode() {
        return pendingNodeBestBound.poll();
    }

    private Node getNextPendingNode() {
        return pendingNode.pollFirst();
    }

    private Node getPendingNode() {
        nodesExplored++;
        if (nodesExplored % 2==0)
            return getBestBoundNode();
        else
            return getNextPendingNode();
    }

    private int queueSize() {
        return Math.max(
                pendingNode.size(), pendingNodeBestBound.size()
        );
    }

    private boolean isQueueEmpty() {
        return pendingNode.isEmpty() && pendingNodeBestBound.isEmpty();
    }


    void offerNode(Node node) {
        // add  the node to the two queues
        pendingNode.offerFirst(node);
        pendingNodeBestBound.offer(node);
    }

    boolean updateIncumbent(List<Column> incumbent) {
        // check  the objective function value of the incumbent
        double objval = Global.VEHICLE_COST * incumbent.size()
                + incumbent.stream().mapToDouble(Column::getCost).sum();
        if (objval < upperbound) {
            bestIncumbent.clear();
            bestIncumbent.addAll(incumbent);

            upperbound = objval;
            return true;
        }

        return false;
    }

    double getUpperbound() {
        return upperbound;
    }

    GRBEnv getGrbEnv() throws GRBException {
        if (null == grbEnv)
            grbEnv = new GRBEnv();
        return grbEnv;
    }

    public void destroy() {
        try {
            grbEnv.dispose();
            this.branchTree = null;
        } catch (GRBException e) {
            e.printStackTrace();
        }
    }
}
