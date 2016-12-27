package branchandprice;

import algorithm.Column;
import data.DataInstance;
import gurobi.GRBException;
import utils.Global;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Created by yuhuishi on 12/18/2016.
 * University of Michigan
 * Academic use only
 */
public class Node implements Comparable<Node> {
    private static int NODE_ID_COUNTER = 0;


    private final int nodeID;
    private List<Column> currentCol;
    private List<BranchConstraint> constraintList;
    private double lowerBound; // lowerbound of the parent node
    private boolean processed;

    private double objVal;

    public Node(List<Column> currentCol, List<BranchConstraint> constraintList, double lowerBound) {
        nodeID = NODE_ID_COUNTER++;

        this.currentCol = currentCol;
        this.constraintList = constraintList; // defensive copy
        this.lowerBound = lowerBound;
        this.processed = false;
    }

    public void process() throws GRBException {
        if (this.processed)
            return;

        Logger.getAnonymousLogger().info("Proocessing node ... " + nodeID);
        Logger.getAnonymousLogger()
                .info(String.format("Node ID: %d, Col list size: %d, Branch constraints size: %d, Lowerbound: %.3f, best incumbent: %.3f\n",
                        nodeID, currentCol.size(), constraintList.size(), lowerBound, BranchAndPrice.getBranchTree().getUpperbound()));
        // check if lower bounds is higher than current best incumbent
        // if yes, fathom  the node
        if (this.lowerBound >= BranchAndPrice.getBranchTree().getUpperbound()) {
            Logger.getAnonymousLogger().info("Node " + nodeID + " lowerbound > upperbound, fathomed.");
            return;
        }


        // solve the lp relaxation
        int initColSize = currentCol.size();
        Logger.getAnonymousLogger().info("Solving the lp relaxation");
        LPSolver lpSolver = new LPSolver(currentCol, constraintList, BranchAndPrice.getBranchTree().getGrbEnv());
        Map<Column, Double> lpSolution = lpSolver.solve();

        objVal = calcObjVal(lpSolution);
        Logger.getAnonymousLogger()
                .info("Finish solving lp relaxation, objval=" + objVal + "# cols generated: " + (currentCol.size()-initColSize));


        // check integrality, to see if more branches are going to be added
        Node[] nodesToAdd = makeBranches(lpSolution);
        if (null == nodesToAdd) {
            // integer solution, update the incumbent
            Logger.getAnonymousLogger().info("Find integer solution, updating incumbent...");
            List<Column> usedCols = lpSolution.keySet().stream().filter(col -> lpSolution.get(col) > 0.5)
                    .collect(Collectors.toList());
            BranchAndPrice.getBranchTree().updateIncumbent(usedCols);
        } else {
            // add to branches
            for (Node node : nodesToAdd) {
                BranchAndPrice.getBranchTree().offerNode(node);
            }


            // if node id is even, solve the integer version of the problem to update the best incumbent value
            List<Column> integerSolution;
            if (this.nodeID % BranchAndPrice.SOLVE_INTEGER_PROBLEM_INTERVAL == 0) {
                // solve the integer problem
                Logger.getAnonymousLogger().info("Solving integer problem to get incumbent...");
                integerSolution = lpSolver.solveIntegerProblem();
                // update the incumbent
                if (integerSolution.size()>0)
                    BranchAndPrice.getBranchTree().updateIncumbent(integerSolution);
            }
        }


        // mark as processed
        this.processed = true;

        lpSolver.end();

    }

    private double calcObjVal(Map<Column, Double> solution) {
        return solution.entrySet().stream()
                .mapToDouble(entry->entry.getValue()*(Global.VEHICLE_COST+entry.getKey().getCost())).sum();
    }

    private BranchConstraint[] checkIntegrality(Map<Column, Double> solution) {
        // check if exist fractional lambda variables


        // build the cache to speed up query
        Map<Integer, List<Column>> testParition = colsContainTid(solution.keySet());
        Map<Integer, Map<Integer, List<Column>>> orderedPairPartion = colsContainOrderedPair(testParition);

        // check tests tpgether
        int[] result = integralityCheckTestPair(solution, orderedPairPartion);
        if (result[0] != -1) {
            Logger.getAnonymousLogger().info("Find fractional solution for test pair " + result[0] + "," + result[1]);
            return new BranchConstraint[] {
                    new ConstraintTestTogether(BranchConstraint.CONSTRAINT_DIRECTION.ENFORCE,
                            result[0], result[1]),
                    new ConstraintTestTogether(BranchConstraint.CONSTRAINT_DIRECTION.FORBID,
                            result[0], result[1])
            };
        }

        // check single test
        result = integralityCheckTestOnVehicle(solution, testParition);
        if (result[0] != -1) {
            // find fractional solution
            Logger.getAnonymousLogger().info("Find fractional solution for test-vehicle pair " + result[0] + "," + result[1]);

            return new BranchConstraint[] {
                    new ConstraintTestOnVehicle(BranchConstraint.CONSTRAINT_DIRECTION.ENFORCE,
                            result[0], result[1]),
                    new ConstraintTestOnVehicle(BranchConstraint.CONSTRAINT_DIRECTION.FORBID,
                            result[0], result[1])
            };
        }

        // comprehensive check
        result = integralityCheckTestPairOnVehicle(solution, orderedPairPartion);
        if (result[0] != -1) {
            Logger.getAnonymousLogger().info("Find fractional solution for test-test-vehicle pair " + result[0] + "," + result[1] + "," + result[2]);
            return new BranchConstraint[] {
                    new ConstraintTestTogetherOnVehicle(BranchConstraint.CONSTRAINT_DIRECTION.ENFORCE,
                            result[0], result[1], result[2]),
                    new ConstraintTestTogetherOnVehicle(BranchConstraint.CONSTRAINT_DIRECTION.FORBID,
                            result[0], result[1], result[2])
            };
        }

        return null; // all integer
    }

    private Node[] makeBranches(Map<Column, Double> solution) {
        // check the integrality of solution,
        // if find fractional solution, make two branches
        BranchConstraint[] cons = checkIntegrality(solution);
        if (cons != null) {
            // find fractional solution, make branches

            // defensive copy
            List<BranchConstraint> leftConsList = new ArrayList<>(constraintList);
            List<BranchConstraint> rightConsList = new ArrayList<>(constraintList);
            leftConsList.add(cons[0]);
            rightConsList.add(cons[1]);
            Node left = new Node(new ArrayList<>(currentCol),
                    leftConsList, objVal);
            Node right = new Node(new ArrayList<>(currentCol),
                    rightConsList, objVal);

            return new Node[] {left, right};

        } else {
            // all integer, fathom the current branch
            return null;
        }


    }

    private Map<Integer, List<Column>> colsContainTid(Collection<Column> colPool) {
        // partition all columns into cols contain tid
        Map<Integer, List<Column>> result = new HashMap<>();
        colPool.stream().forEach(
                col->col.getSeq().forEach(tid->{
                    if (result.containsKey(tid))
                        result.get(tid).add(col);
                    else {
                        result.put(tid, new ArrayList<>());
                        result.get(tid).add(col);
                    }
                })
        );
        return result;
    }

    private Map<Integer, Map<Integer, List<Column>>> colsContainOrderedPair(Map<Integer, List<Column>> partition) {
        Map<Integer, Map<Integer, List<Column>>> result = new HashMap<>();
        // initialization
        DataInstance.getInstance().getTidList().forEach(tid->result.put(tid, new HashMap<>()));
        for (int tid1 : DataInstance.getInstance().getTidList()) {
            for (int tid2 : DataInstance.getInstance().getTidList()) {
                // columns contain both tests
                List<Column> intersection = partition.get(tid1).stream()
                        .filter(col -> partition.get(tid2).contains(col))
                        .filter(col-> col.getSeq().indexOf(tid1) < col.getSeq().indexOf(tid2))
                        .collect(Collectors.toList());
                result.get(tid1).put(tid2, intersection);
            }
        }
        return result;
    }

    private int[] integralityCheckTestOnVehicle(Map<Column, Double> solution, Map<Integer, List<Column>> testPartition) {
        // lambda = columns containing tid, and using vehicle release
        int[] result = {-1,-1};
        double minDistToHalf = Double.MAX_VALUE;
        for (int tid : DataInstance.getInstance().getTidList()) {
            for (int vrelease : DataInstance.getInstance().getVehicleReleaseList()) {
                double lambda = testPartition.get(tid).stream().filter(col->col.getRelease()==vrelease)
                        .mapToDouble(solution::get).sum();
                if (Math.abs(lambda-0.5) < minDistToHalf) {
                    minDistToHalf = Math.abs(lambda-0.5);
                    result[0] = tid;
                    result[1] = vrelease;
                }
            }
        }
        if (minDistToHalf > 0.49) return new int[]{-1,-1}; // all integer
        return  result;
    }

    private int[] integralityCheckTestPair(Map<Column, Double> solution, Map<Integer, Map<Integer, List<Column>>> partition) {
        int[] result = {-1, -1};
        double minDistToHalf = Double.MAX_VALUE;
        List<Integer> tidList = DataInstance.getInstance().getTidList();
        for (int i = 0; i < tidList.size(); i++) {
            int tid1 = tidList.get(i);
            for (int i1 = i+1; i1 < tidList.size(); i1++) {
                int tid2 = tidList.get(i1);
                double lambda =
                        partition.get(tid1).get(tid2).stream().mapToDouble(solution::get).sum() +
                                partition.get(tid2).get(tid1).stream().mapToDouble(solution::get).sum();
                if (Math.abs(lambda - 0.5) < minDistToHalf) {
                    minDistToHalf = Math.abs(lambda - 0.5);
                    result[0] = tid1;
                    result[1] = tid2;
                }
            }
        }

        if (minDistToHalf > 0.49) return new int[]{-1,-1}; // all integer
        return result;

    }

    private int[] integralityCheckTestPairOnVehicle(Map<Column, Double> solution, Map<Integer, Map<Integer, List<Column>>> partition) {
        int[] result = {-1, -1, -1};
        double minDistToHalf = Double.MAX_VALUE;
        List<Integer> tidList = DataInstance.getInstance().getTidList();
        for (int i=0; i<tidList.size(); i++) {
            int tid1 = tidList.get(i);
            for (int j=0; j<tidList.size(); j++) {
                int tid2 = tidList.get(j);
                if (i==j) continue;
                for (int vrelease : DataInstance.getInstance().getVehicleReleaseList()) {
                    double lambda = partition.get(tid1).get(tid2).stream().filter(col->col.getRelease()==vrelease)
                            .mapToDouble(solution::get).sum();
                    if (Math.abs(lambda - 0.5) < minDistToHalf) {
                        minDistToHalf = Math.abs(lambda - 0.5);
                        result[0] = tid1;
                        result[1] = tid2;
                        result[2] = vrelease;
                    }
                }
            }
        }
        if (minDistToHalf > 0.49) return new int[]{-1,-1,-1}; // all integer
        return result;
    }


    @Override
    public int compareTo(Node o) {
        if (lowerBound < o.lowerBound)
            return -1;
        else if (lowerBound > o.lowerBound)
            return 1;
        else
            return 0;
    }

    public boolean isProcessed() {
        return processed;
    }

    public double getObjVal() {
        return objVal;
    }
}
