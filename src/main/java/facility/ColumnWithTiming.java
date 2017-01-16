package facility;

import algorithm.Column;
import data.DataInstance;
import data.TestRequest;

import java.util.*;

/**
 * Yuhui Shi - University of Michigan
 * academic use only
 */
public class ColumnWithTiming extends Column {

    private final Map<Integer, Integer> startTimeMap;
    private final Set<Integer> resourceSet;

    public ColumnWithTiming(List<Integer> seq, int release) {
        super(seq, release);
        // use default timing options
        this.startTimeMap = defaultStartTime();
        this.resourceSet = resourceBasedOnStart();
        this.cost = calacColCost();
    }


    public ColumnWithTiming(String instID, List<Integer> seq, int release) {
        super(instID, seq, release);
        // use default timing options
        this.startTimeMap = defaultStartTime();
        this.resourceSet = resourceBasedOnStart();
        this.cost = calacColCost();
    }

    public ColumnWithTiming(List<Integer> seq, int release, Map<Integer, Integer> startTimeMap) {
        super(seq, release);
        this.startTimeMap = startTimeMap;
        this.resourceSet = resourceBasedOnStart();
        this.cost = calacColCost();
    }

    public ColumnWithTiming(String instID, List<Integer> seq, int release, Map<Integer, Integer> startTimeMap) {
        super(instID, seq, release);
        this.startTimeMap = startTimeMap;
        this.resourceSet = resourceBasedOnStart();
        this.cost = calacColCost();
    }

    private Map<Integer, Integer> defaultStartTime() {
        Map<Integer, Integer> startTimeMap = new HashMap<>();
        int start = this.release;
        for (int tid : this.seq) {
            TestRequest test = DataInstance.getInstance(instID).getTestById(tid);

            if (start + test.getPrep() < test.getRelease()) {
                startTimeMap.put(tid, test.getRelease());
                start = test.getRelease() + test.getTat() + test.getAnalysis();
            } else {
                startTimeMap.put(tid, start);
                start += test.getDur();
            }
        }
        return startTimeMap;
    }

    private Set<Integer> resourceBasedOnStart() {
        Set<Integer> resourceSet = new HashSet<>();
        this.startTimeMap.entrySet().forEach(e -> {
            int tid = e.getKey();
            int start = e.getValue();
            TestRequest test = DataInstance.getInstance(instID).getTestById(tid);
            int tat_start = start + test.getPrep();
            int tat_end = start + test.getPrep() + test.getTat();
            for (int d = tat_start; d < tat_end; d++) {
                resourceSet.add(d);
            }
        });
        return resourceSet;
    }

    public List<Integer> daysHasCrash() {
        return new ArrayList<>(this.resourceSet);
    }

    private double calacColCost() {
        // compute the column cost based on start time of tests
        return this.seq.stream().mapToInt(tid -> Math.max(startTimeMap.get(tid)
                + DataInstance.getInstance(instID).getTestById(tid).getDur()
                - DataInstance.getInstance(instID).getTestById(tid).getDeadline(), 0))
                .sum();

    }

    public int getStartTimeByTid(int tid) {
        return startTimeMap.get(tid);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        ColumnWithTiming that = (ColumnWithTiming) o;

        return startTimeMap.equals(that.startTimeMap);

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + startTimeMap.hashCode();
        return result;
    }

    public boolean isValid() {
        // everything after vehicle release
        boolean afterRelease = seq.stream().allMatch(tid->startTimeMap.get(tid) >= release);
        boolean afterTestRelease = seq.stream().allMatch(tid->startTimeMap.get(tid)
                >= DataInstance.getInstance(instID).getTestById(tid).getRelease());
        boolean noOverlap = true;
        int prevEnd = release;
        for (Integer tid : seq) {
            int start = startTimeMap.get(tid);
            int dur = DataInstance.getInstance(instID).getTestById(tid).getDur();
            if (start < prevEnd) {
                noOverlap = false;
                break;
            }
            prevEnd = start + dur;
        }

//        if (!afterRelease)
//            System.out.println("after release");
//        if (!afterTestRelease)
//            System.out.println("after test release");
//        if (!noOverlap)
//            System.out.println("no overlap");

        return afterRelease && afterTestRelease && noOverlap;
    }
}
