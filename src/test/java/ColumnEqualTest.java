import algorithm.Column;
import algorithm.CompatibilityMatrixDecomposition;
import data.DataInstance;
import data.Reader;
import facility.ColumnWithTiming;
import org.junit.Before;
import org.junit.Test;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by yuhuishi on 9/27/2016.
 * University of Michigan
 * Academic use only
 */
public class ColumnEqualTest {

    private final String filepath = "./data/157 - orig.tp3s";

    @Before
    public void init() {
        Reader jsonReader = new Reader(filepath);
        DataInstance.init(jsonReader);
    }

    @Test
    public void columnEqTest() {

        Reader jsonReader = new Reader(filepath);
        DataInstance.init(jsonReader);

        List<Integer> seq1 = new ArrayList<>();
        List<Integer> seq2 = new ArrayList<>();
        int[] tids = new int[] {1883,1895,1870};
        for (int i = 0; i < tids.length; i++) {
            seq1.add(tids[i]);
            seq2.add(tids[i]);
        }

        Column col1 = new Column(seq1, 10);
        Column col2 = new Column(seq2, 10);
        Column col3 = new Column(seq1, 2);
        List<Integer> seq3 = new ArrayList<>();
        seq3.add(1883); seq3.add(1870); seq3.add(1895);
        Column col4 = new Column(seq3, 10);


        assert col1.equals(col2);
        assert col1.hashCode()==col2.hashCode();
        assert !col1.equals(col3);
        assert col1.hashCode()!=col3.hashCode();
        assert !col1.equals(col4);
        assert col1.hashCode()!=col4.hashCode();
    }

    @Test
    public void columnWithTimingEqTest() {
        Reader jsonReader = new Reader(filepath);
        DataInstance.init(jsonReader);

        List<Integer> seq1 = new ArrayList<>();
        List<Integer> seq2 = new ArrayList<>();
        int[] tids = new int[] {1883,1895,1870};
        Map<Integer,Integer> start1 = new HashMap<>();
        Map<Integer,Integer> start2 = new HashMap<>();


        for (int i = 0; i < tids.length; i++) {
            seq1.add(tids[i]); seq2.add(tids[i]);
            start1.put(tids[i],i); start2.put(tids[i], i);
        }

        ColumnWithTiming col1 = new ColumnWithTiming(seq1, 10, start1);
        ColumnWithTiming col2 = new ColumnWithTiming(seq2, 10, start2);

        assert col1.equals(col2);
        assert col1.hashCode()==col2.hashCode();
    }

    @Test
    public void compMatrixDecompositionTest() {

        List<List<Integer>> result = CompatibilityMatrixDecomposition.compDecompose();
        long totalTest = result.stream().flatMap(Collection::stream).count();
        assert totalTest == DataInstance.getInstance().getTidList().size();
        System.out.println(result);
    }

    @Test
    public void danglingTestTest() {
        int[] dangling = {1863, 1866, 1868, 1886, 1895, 1896, 1905, 1908, 1909};
        List<Integer> tidList = DataInstance.getInstance().getTidList();
        for (int checkTid : dangling) {
            // check no tests can follow them
            List<Integer> compTestsAfter =
                    tidList.stream().filter(tid -> DataInstance.getInstance().getRelation(checkTid, tid))
                            .collect(Collectors.toList());
            assert compTestsAfter.size() == 0;
            // check no tests can before them
            List<Integer> compTestsBefore =
                    tidList.stream().filter(tid -> DataInstance.getInstance().getRelation(tid, checkTid))
                            .collect(Collectors.toList());
            assert compTestsBefore.size() == 0;
        }

    }

}
