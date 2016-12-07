package algorithm;

import data.DataInstance;

import java.util.*;

/**
 * Yuhui Shi - University of Michigan
 * academic use only
 */
public class CompatibilityMatrixDecomposition {

    public static List<List<Integer>> compDecompose() {
        List<List<Integer>> result = new ArrayList<>();

        // define a symmetry matrix
        Map<Integer, Map<Integer, Boolean>> symCompMatrix = new HashMap<>();
        // initialize
        List<Integer> tidList = DataInstance.getInstance().getTidList();
        tidList.forEach(tid -> symCompMatrix.put(tid, new HashMap<>()));
        tidList.forEach(tid1 -> tidList.forEach(tid2 -> {
            if (DataInstance.getInstance().getRelation(tid1, tid2)
                    || DataInstance.getInstance().getRelation(tid2, tid1))
                symCompMatrix.get(tid1).put(tid2, true);
            else
                symCompMatrix.get(tid1).put(tid2, false);
        }));

        Map<Integer, Boolean> marked = new HashMap<>();
        tidList.forEach(tid -> marked.put(tid, false));

        while (marked.values().contains(false)) {
            Deque<Integer> seed = new ArrayDeque<>();
            for (int tid : marked.keySet()) {
                if (!marked.get(tid)) {
                    seed.add(tid);
                    marked.put(tid, true);
                    break;
                }
            }

            List<Integer> compSet = new ArrayList<>();
            while (!seed.isEmpty()) {
                // add all compatible to the set
                int seedTid = seed.pop();
                compSet.add(seedTid);
                tidList.stream().filter(tid -> symCompMatrix.get(seedTid).get(tid) && !marked.get(tid))
                        .forEach(tid -> {
                            seed.add(tid);
                            marked.put(tid, true);
                        });
            }
            result.add(compSet);
        }

        return result;

    }
}
